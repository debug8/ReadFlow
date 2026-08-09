package net.readflow.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import net.readflow.model.WordMark
import net.readflow.model.WordToken

/**
 * Стилі позначених слів. Кольори приходять із теми, тому передаються ззовні,
 * а не беруться тут: у темній темі червоне тло помилки інше.
 */
data class WordStyles(
    val boundary: SpanStyle,
    val error: SpanStyle
)

/**
 * Зібрати текст режиму читання.
 *
 * Символи беруться з вихідного тексту **без змін**: межі слів із
 * [net.readflow.core.TextStatsCalculator.getWords] — це індекси саме у вихідному
 * рядку, і будь-яка нормалізація тут зсунула б їх, а разом з ними й номери слів.
 *
 * Стиль додається лише позначеним словам. Звичайне слово окремого діапазону не
 * отримує навмисно: на тексті в 3000 слів це 6000 зайвих span-ів у розкладці,
 * а виглядало б воно так само, як успадкований стиль абзацу.
 */
fun buildReadingText(
    text: String,
    words: List<WordToken>,
    styles: WordStyles,
    markOf: (WordToken) -> WordMark = { WordMark.NONE }
): AnnotatedString = buildAnnotatedString {
    append(text)

    for (word in words) {
        val style = when (markOf(word)) {
            WordMark.NONE -> continue
            WordMark.BOUNDARY -> styles.boundary
            WordMark.ERROR -> styles.error
        }

        // Межі слова напівінтервальні [start, end) — рівно те, що чекає addStyle.
        addStyle(style, word.start, word.end)
    }
}

/**
 * Індекс слова в [words], у яке влучив тап по символу [offset], або `-1`.
 *
 * `TextLayoutResult.getOffsetForPosition` повертає найближчу **межу** символу, а
 * не сам символ: тап у праву половину останньої букви дає offset одразу за
 * словом. Тому влучанням вважається і `offset == word.end` — інакше в кінець
 * кожного слова промахувалися б.
 *
 * Пошук двійковий: на 3000 слів лінійний перебір на кожен тап зайвий.
 */
fun wordIndexAt(words: List<WordToken>, offset: Int): Int {
    if (words.isEmpty() || offset < 0) {
        return -1
    }

    var low = 0
    var high = words.size - 1
    var candidate = -1

    // Останнє слово, яке починається не пізніше за offset.
    while (low <= high) {
        val middle = (low + high) / 2

        if (words[middle].start <= offset) {
            candidate = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }

    if (candidate < 0) {
        return -1
    }

    return if (offset <= words[candidate].end) candidate else -1
}

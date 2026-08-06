package net.readflow.core

import net.readflow.model.TextStats
import net.readflow.model.WordToken
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Правила підрахунку з розділу 4 `SPEC.md` — в одному місці.
 *
 * Чистий Kotlin без залежностей від Android SDK: це дзеркало
 * `desktop/ReadFlow/Core/TextStatsCalculator.cs`, і на однакових текстах
 * обидві реалізації мусять давати однакові числа. Змінюється правило —
 * правляться специфікація, обидві реалізації і тести, одним комітом.
 */
object TextStatsCalculator {

    // Апостроф. Word сам замінює ' на ’, українські розкладки дають ʼ —
    // без повного списку те саме слово рахувалося б по-різному.
    private const val APOSTROPHES = "'’ʼ‘´`"

    // Дефіс. Тире (– —) і мінус (−) сюди свідомо НЕ входять: вони розділяють слова.
    private const val HYPHENS = "-‐‑"

    private const val SENTENCE_TERMINATORS = ".!?…"

    /**
     * Розібрати текст на слова з їхніми межами.
     * Індекси відповідають **вихідному** тексту, тому їх можна одразу
     * використовувати для рендеру й підсвічування.
     */
    fun getWords(text: String?): List<WordToken> {
        if (text.isNullOrEmpty()) {
            return emptyList()
        }

        // Приблизно одне слово на 6 символів — щоб список не перевиділявся на великих текстах.
        val words = ArrayList<WordToken>(text.length / 6 + 4)
        var position = 0

        while (position < text.length) {
            if (!isLetter(text[position])) {
                position++
                continue
            }

            val start = position
            var letters = 1
            position++

            while (position < text.length) {
                if (isLetter(text[position])) {
                    letters++
                    position++
                    continue
                }

                // Сполучник входить у слово, лише якщо праворуч від нього теж буква.
                // Ліворуч буква вже гарантована: ми всередині слова.
                if (isJoiner(text[position]) &&
                    position + 1 < text.length &&
                    isLetter(text[position + 1])
                ) {
                    letters++
                    position += 2
                    continue
                }

                break
            }

            words.add(
                WordToken(
                    number = words.size + 1,
                    text = text.substring(start, position),
                    start = start,
                    letterCount = letters
                )
            )
        }

        return words
    }

    /**
     * Порахувати всю статистику тексту.
     *
     * @param options параметри користувача; `null` — значення за замовчуванням.
     */
    fun calculate(text: String?, options: CountingOptions? = null): TextStats =
        calculate(text, getWords(text), options)

    /**
     * Те саме, але з уже розібраними словами — щоб не робити розбір двічі,
     * коли слова однаково потрібні для рендеру.
     */
    fun calculate(text: String?, words: List<WordToken>?, options: CountingOptions?): TextStats {
        if (text.isNullOrEmpty()) {
            return TextStats.Empty
        }

        val tokens = words ?: getWords(text)
        val mode = (options ?: CountingOptions.Default).paragraphs

        // Знаки рахуємо на нормалізованому тексті: інакше \r\n дав би на два знаки
        // більше, ніж той самий текст на десктопі.
        val normalized = normalizeLineEndings(text)

        var charCountNoSpaces = 0
        var letterCount = 0

        for (c in normalized) {
            if (!c.isWhitespace()) {
                charCountNoSpaces++
            }

            if (isLetter(c)) {
                letterCount++
            }
        }

        return TextStats(
            wordCount = tokens.size,
            charCount = normalized.length,
            charCountNoSpaces = charCountNoSpaces,
            letterCount = letterCount,
            averageWordLength = averageWordLength(letterCount, tokens.size),
            sentenceCount = countSentences(normalized),
            paragraphCount = countParagraphs(normalized, mode)
        )
    }

    /** Буква — Unicode-літера або цифра (`SPEC.md`, 4.1). */
    fun isLetter(c: Char): Boolean {
        // Сполучник ніколи не буква — і це не формальність.
        // Український апостроф ʼ (U+02BC) належить до категорії Lm, тож
        // Char.isLetter() вважає його літерою. Без цієї перевірки «мавпʼячий»
        // мав би 9 букв, а «мавп'ячий» — 8: те саме слово, різні числа
        // залежно від того, звідки скопійовано текст.
        if (isJoiner(c)) {
            return false
        }

        return c.isLetter() || c.isDigit()
    }

    /** Сполучник — апостроф або дефіс; тире й мінус сюди не входять. */
    fun isJoiner(c: Char): Boolean =
        APOSTROPHES.indexOf(c) >= 0 || HYPHENS.indexOf(c) >= 0

    /**
     * Середня довжина слова, округлена до 0.1 «від нуля» (`SPEC.md`, 4.4).
     *
     * Ділення робиться на **точному дробі**, а не на `Double`, і це принципово.
     * `BigDecimal(letters.toDouble() / words)` дало б інші числа, ніж десктоп:
     * 81 буква на 20 слів — це рівно 4.05, але найближчий Double трохи менший
     * за 4.05, тож HALF_UP дав би 4.0, тоді як `Math.Round(..., AwayFromZero)`
     * у .NET дає 4.1. На сітці з 1 524 200 пар «букви/слова» такі розбіжності
     * трапляються 1520 разів, з них 560 — у звичайному діапазоні 3–10 букв.
     * Точне ділення збігається з десктопом у 100% випадків.
     */
    private fun averageWordLength(letterCount: Int, wordCount: Int): Double {
        if (wordCount == 0) {
            return 0.0
        }

        return BigDecimal(letterCount)
            .divide(BigDecimal(wordCount), 1, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun countSentences(text: String): Int {
        var count = 0
        var hasContent = false

        for (c in text) {
            if (SENTENCE_TERMINATORS.indexOf(c) >= 0) {
                if (hasContent) {
                    count++
                    hasContent = false
                }

                // Кілька роздільників поспіль («Ого!!!») дають одне речення:
                // hasContent уже false, тож наступні просто пропускаються.
                continue
            }

            if (isLetter(c)) {
                hasContent = true
            }
        }

        // Останній фрагмент без крапки теж є реченням.
        if (hasContent) {
            count++
        }

        return count
    }

    private fun countParagraphs(text: String, mode: ParagraphMode): Int {
        var count = 0
        var lineHasContent = false
        var previousLineWasEmpty = true

        for (i in 0..text.length) {
            val endOfLine = i == text.length || text[i] == '\n'

            if (!endOfLine) {
                if (!text[i].isWhitespace()) {
                    lineHasContent = true
                }

                continue
            }

            if (lineHasContent) {
                // У режимі блоків новий абзац починається лише після порожнього рядка.
                if (mode == ParagraphMode.NON_EMPTY_LINES || previousLineWasEmpty) {
                    count++
                }
            }

            previousLineWasEmpty = !lineHasContent
            lineHasContent = false
        }

        return count
    }

    /**
     * Звести `\r\n` і одиночний `\r` до `\n`.
     * Якщо `\r` у тексті немає — повертається той самий рядок без копіювання.
     */
    private fun normalizeLineEndings(text: String): String {
        if (text.indexOf('\r') < 0) {
            return text
        }

        val builder = StringBuilder(text.length)
        var i = 0

        while (i < text.length) {
            val c = text[i]

            if (c == '\r') {
                builder.append('\n')

                if (i + 1 < text.length && text[i + 1] == '\n') {
                    i++
                }

                i++
                continue
            }

            builder.append(c)
            i++
        }

        return builder.toString()
    }
}

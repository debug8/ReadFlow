package net.readflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import net.readflow.core.TextStatsCalculator
import net.readflow.model.WordMark
import net.readflow.model.WordToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Побудова тексту режиму читання й влучання тапа по слову.
 *
 * Обидві функції чисті, тому перевіряються звичайними JVM-тестами: жести й
 * розкладку тексту вони не чіпають, а помиляються саме тут — в індексах.
 */
class ReadingTextTest {

    private val styles = WordStyles(
        boundary = SpanStyle(color = Color(0xFF1B5E4B)),
        error = SpanStyle(background = Color(0xFFFFDAD6))
    )

    private fun words(text: String) = TextStatsCalculator.getWords(text)

    /** Текст не змінюється: у режимі читання ті самі символи, що ввів учитель. */
    @Test
    fun `text is rendered as is`() {
        val text = "Мама мила раму — і комп'ютер теж."

        val built = buildReadingText(text, words(text), styles)

        assertEquals(text, built.text)
    }

    /** Без позначок жодного стилю не додається. */
    @Test
    fun `plain words get no spans`() {
        val text = "Мама мила раму."

        val built = buildReadingText(text, words(text), styles)

        assertTrue(built.spanStyles.isEmpty())
    }

    /** Позначене слово отримує стиль рівно на своїх межах. */
    @Test
    fun `marked word gets a span on its own range`() {
        val text = "Мама мила раму."
        val tokens = words(text)

        val built = buildReadingText(text, tokens, styles) { word ->
            if (word.number == 2) WordMark.ERROR else WordMark.NONE
        }

        assertEquals(1, built.spanStyles.size)
        assertEquals(5, built.spanStyles[0].start)
        assertEquals(9, built.spanStyles[0].end)
        assertEquals("мила", text.substring(built.spanStyles[0].start, built.spanStyles[0].end))
        assertEquals(styles.error, built.spanStyles[0].item)
    }

    /** Межа й помилка — різні стилі, і вони не змішуються. */
    @Test
    fun `boundary and error use different styles`() {
        val text = "один два три"
        val tokens = words(text)

        val built = buildReadingText(text, tokens, styles) { word ->
            when (word.number) {
                1 -> WordMark.BOUNDARY
                3 -> WordMark.ERROR
                else -> WordMark.NONE
            }
        }

        assertEquals(2, built.spanStyles.size)
        assertEquals(styles.boundary, built.spanStyles[0].item)
        assertEquals(styles.error, built.spanStyles[1].item)
    }

    /**
     * Слово з апострофом і слово з дефісом — це по одному діапазону, а не по два:
     * інакше номер слова в підказці розійшовся б із лічильником слів.
     */
    @Test
    fun `apostrophe and hyphen stay inside one span`() {
        val text = "комп'ютер синьо-жовтий"
        val tokens = words(text)

        val built = buildReadingText(text, tokens, styles) { WordMark.ERROR }

        assertEquals(2, built.spanStyles.size)
        assertEquals("комп'ютер", text.substring(built.spanStyles[0].start, built.spanStyles[0].end))
        assertEquals(
            "синьо-жовтий",
            text.substring(built.spanStyles[1].start, built.spanStyles[1].end)
        )
    }

    /** Порожній текст нічого не ламає. */
    @Test
    fun `empty text builds an empty string`() {
        val built = buildReadingText("", emptyList(), styles)

        assertEquals("", built.text)
        assertTrue(built.spanStyles.isEmpty())
    }

    // --- wordIndexAt ---

    /** Тап усередину слова влучає в нього. */
    @Test
    fun `offset inside a word hits it`() {
        val text = "Мама мила раму."
        val tokens = words(text)

        assertEquals(0, wordIndexAt(tokens, 0))
        assertEquals(0, wordIndexAt(tokens, 2))
        assertEquals(1, wordIndexAt(tokens, 6))
        assertEquals(2, wordIndexAt(tokens, 12))
    }

    /**
     * Межа після останньої букви — теж влучання: `getOffsetForPosition` повертає
     * саме її, коли палець потрапив у праву половину букви.
     */
    @Test
    fun `offset right after a word still hits it`() {
        val text = "Мама мила раму."
        val tokens = words(text)

        assertEquals(0, wordIndexAt(tokens, 4))
        assertEquals(1, wordIndexAt(tokens, 9))
    }

    /** Тап у розділовий знак чи зайвий пробіл нічого не позначає. */
    @Test
    fun `offset between words misses`() {
        val text = "один    два"
        val tokens = words(text)

        assertEquals(-1, wordIndexAt(tokens, 6))
        assertEquals(-1, wordIndexAt(tokens, 7))
        assertEquals(1, wordIndexAt(tokens, 8))
    }

    /** Порожній список і від'ємний offset не падають. */
    @Test
    fun `empty list and negative offset return nothing`() {
        assertEquals(-1, wordIndexAt(emptyList(), 0))
        assertEquals(-1, wordIndexAt(words("текст"), -1))
    }

    /** Offset за кінцем тексту нічого не позначає. */
    @Test
    fun `offset past the end returns nothing`() {
        val tokens = words("один два")

        assertEquals(-1, wordIndexAt(tokens, 999))
    }

    /** Номер слова в підказці рахується від 1 і збігається з порядком у тексті. */
    @Test
    fun `word numbers start at one`() {
        val text = "перше друге третє"
        val tokens = words(text)

        assertEquals(1, tokens[wordIndexAt(tokens, 1)].number)
        assertEquals(3, tokens[wordIndexAt(tokens, 13)].number)
    }

    /**
     * Двійковий пошук перевіряється не на трьох словах, а на всьому тексті:
     * кожен символ кожного слова мусить влучити саме в своє слово.
     */
    @Test
    fun `every letter of every word hits its own word`() {
        val text = generateSequence(1) { it + 1 }
            .take(500)
            .joinToString(" ") { "слово$it" }
        val tokens = words(text)

        assertEquals(500, tokens.size)

        for ((index, word) in tokens.withIndex()) {
            for (offset in word.start until word.end) {
                assertEquals("Символ $offset", index, wordIndexAt(tokens, offset))
            }
        }
    }

    /**
     * Побудова на тексті ~3000 слів — верхня межа сценарію зі `SPEC.md`.
     * Поріг навмисно з великим запасом: тест ловить не мілісекунди, а
     * випадкове скочування в квадратичний алгоритм.
     */
    @Test
    fun `building three thousand words is fast`() {
        val text = generateSequence(1) { it + 1 }
            .take(3000)
            .joinToString(" ") { "слово$it" }
        val tokens = words(text)

        assertEquals(3000, tokens.size)

        val started = System.nanoTime()
        val built = buildReadingText(text, tokens, styles)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(text.length, built.text.length)
        assertTrue("Побудова зайняла $elapsedMs мс", elapsedMs < 500)
    }
}

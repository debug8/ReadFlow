package net.readflow.core

import net.readflow.model.WordToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перенесено з `desktop/ReadFlow.Tests/TextStatsCalculatorTests.cs` з тими самими
 * числами. Кожен тест відповідає правилу з розділу 4 `SPEC.md`.
 *
 * Якщо тест падає — спершу дивимось у специфікацію, а не «підганяємо» код:
 * ці ж числа має показати десктопна версія.
 *
 * Назви латиницею навмисно: Kotlin робить із них імена class-файлів, а кирилиця
 * в назві файлу ламає збірку там, де кодування шляхів не UTF-8.
 */
class TextStatsCalculatorTest {

    // ── Порожній текст ────────────────────────────────────────────────

    /** Порожній текст — усюди нулі, без падіння на діленні. */
    @Test
    fun `empty text - all counts are zero`() {
        for (text in listOf(null, "")) {
            val stats = TextStatsCalculator.calculate(text)

            assertEquals(0, stats.wordCount)
            assertEquals(0, stats.charCount)
            assertEquals(0, stats.charCountNoSpaces)
            assertEquals(0, stats.letterCount)
            assertEquals(0.0, stats.averageWordLength, 0.0)
            assertEquals(0, stats.sentenceCount)
            assertEquals(0, stats.paragraphCount)
        }
    }

    /** Самі пробіли: слів немає, але знаки рахуються. */
    @Test
    fun `whitespace only - no words but chars counted`() {
        val stats = TextStatsCalculator.calculate("   \t  \n  ")

        assertEquals(0, stats.wordCount)
        assertEquals(9, stats.charCount)
        assertEquals(0, stats.charCountNoSpaces)
        assertEquals(0, stats.sentenceCount)
        assertEquals(0, stats.paragraphCount)
        assertEquals("Ділення на нуль не має ставатися.", 0.0, stats.averageWordLength, 0.0)
    }

    /** Порожній текст не дає жодного токена слова. */
    @Test
    fun `empty text - returns no word tokens`() {
        assertEquals(0, TextStatsCalculator.getWords(null).size)
        assertEquals(0, TextStatsCalculator.getWords("").size)
    }

    // ── Апостроф (4.2) ────────────────────────────────────────────────

    /** Апостроф не розриває слово і сам буквою не є. */
    @Test
    fun `apostrophe does not split word`() {
        val words = TextStatsCalculator.getWords("комп'ютер")

        assertEquals(1, words.size)
        assertEquals("комп'ютер", words[0].text)
        assertEquals("Апостроф не є буквою.", 8, words[0].letterCount)
    }

    /** Усі варіанти апострофа поводяться однаково — зокрема ʼ U+02BC. */
    @Test
    fun `all apostrophe variants are treated identically`() {
        // Word підміняє ' на ’, українські розкладки дають ʼ.
        for (apostrophe in listOf('\'', '’', 'ʼ', '‘', '´', '`')) {
            val text = "мавп" + apostrophe + "ячий"
            val words = TextStatsCalculator.getWords(text)
            val code = "U+%04X".format(apostrophe.code)

            assertEquals("Апостроф $code розірвав слово.", 1, words.size)
            assertEquals("Апостроф $code порахувався як буква.", 8, words[0].letterCount)
        }
    }

    /** Апостроф у кінці слова до нього не входить. */
    @Test
    fun `trailing apostrophe is not part of word`() {
        val words = TextStatsCalculator.getWords("слово' ще")

        assertEquals(2, words.size)
        assertEquals("слово", words[0].text)
    }

    // ── Дефіс і тире (4.2) ────────────────────────────────────────────

    /** Дефіс не розриває слово. */
    @Test
    fun `hyphen does not split word`() {
        val words = TextStatsCalculator.getWords("синьо-жовтий")

        assertEquals(1, words.size)
        assertEquals("синьо-жовтий", words[0].text)
        assertEquals(11, words[0].letterCount)
    }

    /** Кілька дефісів у слові лишають його одним словом. */
    @Test
    fun `multiple hyphens stay one word`() {
        assertEquals(1, TextStatsCalculator.getWords("будь-як-небудь").size)
    }

    /** Тире й мінус — роздільники, на відміну від дефіса. */
    @Test
    fun `em dash and en dash split words`() {
        assertEquals(2, TextStatsCalculator.getWords("слово — друге").size)
        assertEquals(2, TextStatsCalculator.getWords("слово–друге").size)
        assertEquals(2, TextStatsCalculator.getWords("слово−друге").size)
    }

    /** Два дефіси поспіль слово розривають. */
    @Test
    fun `double hyphen splits word`() {
        // Другий дефіс не має букви ліворуч, тому слово розривається.
        val words = TextStatsCalculator.getWords("слово--друге")

        assertEquals(2, words.size)
        assertEquals("слово", words[0].text)
        assertEquals("друге", words[1].text)
    }

    /** Окремий дефіс словом не є. */
    @Test
    fun `standalone hyphen is not a word`() {
        assertEquals(2, TextStatsCalculator.getWords("а - б").size)
    }

    // ── Пробіли й розділові знаки ─────────────────────────────────────

    /** Багато роздільників підряд не породжують порожніх слів. */
    @Test
    fun `many separators do not produce empty words`() {
        val words = TextStatsCalculator.getWords("  раз,,,   два!!!  \t\n  три  ")

        assertEquals(3, words.size)
        assertEquals(listOf("раз", "два", "три"), words.map { it.text })
    }

    /** Текст без букв не має ні слів, ні речень. */
    @Test
    fun `punctuation only - has no words and no sentences`() {
        val stats = TextStatsCalculator.calculate("... !? — «»")

        assertEquals(0, stats.wordCount)
        assertEquals(0, stats.letterCount)
        assertEquals("Фрагмент без букв реченням не є.", 0, stats.sentenceCount)
    }

    // ── Цифри (4.1) ───────────────────────────────────────────────────

    /** Цифри — це слово й це букви. */
    @Test
    fun `digits count as word and as letters`() {
        val stats = TextStatsCalculator.calculate("У 2024 році")

        assertEquals("«2024» — окреме слово.", 3, stats.wordCount)
        assertEquals("Цифри рахуються як букви.", 1 + 4 + 4, stats.letterCount)
    }

    /** Цифри й літери разом лишаються одним словом. */
    @Test
    fun `digits and letters mixed stay one word`() {
        val words = TextStatsCalculator.getWords("A4 та 3D-модель")

        assertEquals(3, words.size)
        assertEquals("A4", words[0].text)
        assertEquals("3D-модель", words[2].text)
    }

    // ── Знаки (4.3) ───────────────────────────────────────────────────

    /** Знаки з пробілами й без. */
    @Test
    fun `chars counted with and without spaces`() {
        val stats = TextStatsCalculator.calculate("а б в")

        assertEquals(5, stats.charCount)
        assertEquals(3, stats.charCountNoSpaces)
    }

    /** CRLF — один знак; Windows і Android дають однакові числа. */
    @Test
    fun `crlf counts as single char`() {
        val windows = TextStatsCalculator.calculate("а\r\nб")
        val unix = TextStatsCalculator.calculate("а\nб")
        val mac = TextStatsCalculator.calculate("а\rб")

        assertEquals(3, windows.charCount)
        assertEquals(unix.charCount, windows.charCount)
        assertEquals(unix.charCount, mac.charCount)
        assertEquals(unix.paragraphCount, windows.paragraphCount)
    }

    // ── Середня довжина слова (4.4) ───────────────────────────────────

    /** Округлення до 0.1. */
    @Test
    fun `average word length rounded to one decimal`() {
        // 2 + 4 + 5 = 11 букв на 3 слова = 3.666… → 3.7
        val stats = TextStatsCalculator.calculate("ця мала книга")

        assertEquals(11, stats.letterCount)
        assertEquals(3.7, stats.averageWordLength, 0.0001)
    }

    /** Рівно .5 округлюється «від нуля», а не банківським правилом. */
    @Test
    fun `average word length rounds half away from zero`() {
        // 17 букв на 4 слова = 4.25. Банківське округлення .NET дало б 4.2,
        // Java/Kotlin дає 4.3 — платформи мусять збігатися.
        val stats = TextStatsCalculator.calculate("аб абв абвг абвгдежз")

        assertEquals(17, stats.letterCount)
        assertEquals(4, stats.wordCount)
        assertEquals(4.3, stats.averageWordLength, 0.0001)
    }

    /**
     * Округлення робиться на точному дробі, а не на Double.
     *
     * Ті самі випадки, що й у `AverageWordLength_RoundsExactFractionNotDouble`
     * на десктопі. 81/20 — це рівно 4.05, але в Double воно зберігається як
     * 4.04999999999999982…, і чесне HALF_UP над таким числом дає 4.0.
     * Розходяться всі серединні значення, чий знаменник не є степенем двійки,
     * тому перевіряємо кілька, а не один.
     */
    @Test
    fun `average word length is rounded on the exact fraction not on a double`() {
        val cases = listOf(
            Triple(20, 81, 4.1),   // 4.05
            Triple(20, 41, 2.1),   // 2.05
            Triple(20, 39, 2.0),   // 1.95
            Triple(20, 51, 2.6),   // 2.55
            Triple(40, 90, 2.3)    // 2.25
        )

        for ((words, letters, expected) in cases) {
            val text = buildText(words, letters)
            val stats = TextStatsCalculator.calculate(text)

            assertEquals("Текст побудовано неправильно.", words, stats.wordCount)
            assertEquals("Текст побудовано неправильно.", letters, stats.letterCount)
            assertEquals(
                "$letters/$words має давати $expected",
                expected,
                stats.averageWordLength,
                0.0001
            )
        }
    }

    /** Текст із рівно [words] слів і рівно [letters] букв разом. */
    private fun buildText(words: Int, letters: Int): String {
        require(letters >= words) { "Кожне слово має щонайменше одну букву." }

        val base = letters / words
        val extra = letters % words

        return (0 until words).joinToString(" ") { i ->
            "а".repeat(base + if (i < extra) 1 else 0)
        }
    }

    // ── Речення (4.5) ─────────────────────────────────────────────────

    /** Речення рахуються за роздільниками. */
    @Test
    fun `sentences counted by terminators`() {
        assertEquals(3, TextStatsCalculator.calculate("Раз. Два! Три?").sentenceCount)
    }

    /** Кілька роздільників поспіль — одне речення. */
    @Test
    fun `sentences - repeated terminators count once`() {
        assertEquals(1, TextStatsCalculator.calculate("Ого!!!").sentenceCount)
        assertEquals(1, TextStatsCalculator.calculate("Справді?!").sentenceCount)
        assertEquals(2, TextStatsCalculator.calculate("Так… Ні…").sentenceCount)
    }

    /** Останній фрагмент без крапки теж є реченням. */
    @Test
    fun `sentences - tail without terminator counts`() {
        assertEquals(2, TextStatsCalculator.calculate("Перше. Друге без крапки").sentenceCount)
    }

    // ── Абзаци (4.6) ──────────────────────────────────────────────────

    /** Режим «кожен непорожній рядок». */
    @Test
    fun `paragraphs - non empty lines mode`() {
        val options = CountingOptions(ParagraphMode.NON_EMPTY_LINES)

        assertEquals(3, TextStatsCalculator.calculate("А\nБ\n\nВ", options).paragraphCount)
        assertEquals(2, TextStatsCalculator.calculate("А\nБ", options).paragraphCount)
        assertEquals(1, TextStatsCalculator.calculate("А\n   \n", options).paragraphCount)
    }

    /** Режим «блок між порожніми рядками». */
    @Test
    fun `paragraphs - blank line separated mode`() {
        val options = CountingOptions(ParagraphMode.BLANK_LINE_SEPARATED)

        assertEquals(2, TextStatsCalculator.calculate("А\nБ\n\nВ", options).paragraphCount)
        assertEquals(1, TextStatsCalculator.calculate("А\nБ", options).paragraphCount)
        assertEquals(2, TextStatsCalculator.calculate("А\n \t \nВ", options).paragraphCount)
    }

    /** Режим за замовчуванням — «кожен непорожній рядок», як на десктопі. */
    @Test
    fun `paragraphs - default mode is non empty lines`() {
        assertEquals(ParagraphMode.NON_EMPTY_LINES, CountingOptions.Default.paragraphs)
        assertEquals(3, TextStatsCalculator.calculate("А\nБ\n\nВ").paragraphCount)
    }

    // ── Межі слів (для Задач 4 і 6) ───────────────────────────────────

    /** Межі й нумерація слів вказують рівно на слово у вихідному тексті. */
    @Test
    fun `word tokens have correct boundaries and numbers`() {
        val text = "  Ліс прокинувся, пташки співали."
        val words = TextStatsCalculator.getWords(text)

        assertEquals(4, words.size)

        words.forEachIndexed { i, word ->
            assertEquals(
                "Нумерація має починатися з 1 і не мати пропусків.",
                i + 1,
                word.number
            )
            assertEquals(
                "Межі слова мають вказувати рівно на нього у вихідному тексті.",
                word.text,
                text.substring(word.start, word.start + word.length)
            )
            assertEquals(word.start + word.length, word.end)
        }

        assertEquals(2, words[0].start)
        assertEquals("співали", words[3].text)
    }

    /** Нормалізація переносів не зсуває індекси слів у вихідному тексті. */
    @Test
    fun `word token boundaries refer to original text even with crlf`() {
        // За цими індексами Задача 4 рендеритиме саме вихідний текст.
        val text = "перше\r\nдруге"
        val words = TextStatsCalculator.getWords(text)

        assertEquals(2, words.size)
        assertEquals("друге", text.substring(words[1].start, words[1].start + words[1].length))
    }

    /** Букв поза словами не буває: сума по словах дорівнює загальній. */
    @Test
    fun `letter count equals sum of word letters`() {
        val text = "Мама мила раму, а тато — 2 вікна: синьо-жовті й комп'ютерні!"
        val words: List<WordToken> = TextStatsCalculator.getWords(text)
        val stats = TextStatsCalculator.calculate(text, words, null)

        assertEquals(
            "Букви поза словами існувати не можуть.",
            words.sumOf { it.letterCount },
            stats.letterCount
        )
        assertEquals(words.size, stats.wordCount)
    }

    // ── Продуктивність (критерій приймання 10) ────────────────────────

    /** 3000 слів — верхня межа сценарію — рахуються швидше за 500 мс. */
    @Test
    fun `large text is processed quickly`() {
        val builder = StringBuilder()
        for (i in 0 until 3000) {
            builder.append("синьо-жовтий ")
            if (i % 12 == 11) {
                builder.append(".\n")
            }
        }

        val text = builder.toString()
        val startedAt = System.nanoTime()
        val stats = TextStatsCalculator.calculate(text)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(3000, stats.wordCount)
        assertTrue(
            "Підрахунок на 3000 слів зайняв $elapsedMs мс.",
            elapsedMs < 500
        )
    }
}

package net.readflow.core

import net.readflow.model.Attempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * Експорт історії в CSV: BOM, роздільник `;` і екранування RFC 4180
 * (`SPEC.md`, розділ «CSV»). Рядок навмисно не збирається конкатенацією —
 * тут перевіряється саме хелпер [HistoryCsv.field].
 */
class HistoryCsvTest {

    private val zone = ZoneOffset.UTC

    private fun attempt(
        studentName: String = "Іван",
        createdAt: Long = Instant.parse("2026-08-19T12:30:00Z").toEpochMilli(),
        grade: Int = 2,
        wpm: Int = 84,
        cpm: Int = 420,
        errors: Int = 3,
        errorPercent: Double = 12.5
    ) = Attempt(
        id = 1,
        studentName = studentName,
        createdAt = createdAt,
        grade = grade,
        wordsPerMinute = wpm,
        charsPerMinute = cpm,
        errors = errors,
        errorPercent = errorPercent
    )

    /** Порожнє поле лишається порожнім, без лапок. */
    @Test
    fun `plain value is left untouched`() {
        assertEquals("Іван", HistoryCsv.field("Іван"))
        assertEquals("", HistoryCsv.field(""))
    }

    /** Роздільник у значенні змушує обрамити лапками. */
    @Test
    fun `value with separator is quoted`() {
        assertEquals("\"Іван; Петро\"", HistoryCsv.field("Іван; Петро"))
    }

    /** Лапки всередині подвоюються, а поле обрамлюється. */
    @Test
    fun `inner quotes are doubled`() {
        assertEquals("\"Він сказав \"\"привіт\"\"\"", HistoryCsv.field("Він сказав \"привіт\""))
    }

    /** Перенос рядка теж вимагає обрамлення. */
    @Test
    fun `newline forces quoting`() {
        assertEquals("\"рядок1\nрядок2\"", HistoryCsv.field("рядок1\nрядок2"))
    }

    /** Файл починається з BOM — інакше Excel не впізнає UTF-8. */
    @Test
    fun `export starts with BOM`() {
        val csv = HistoryCsv.export(emptyList(), zone)

        assertTrue(csv.startsWith(HistoryCsv.BOM))
    }

    /** Порожня історія — це BOM і рядок заголовків, без записів. */
    @Test
    fun `empty history exports only the header`() {
        val csv = HistoryCsv.export(emptyList(), zone)
        val body = csv.removePrefix(HistoryCsv.BOM)

        assertEquals(
            "Учень;Дата;Клас;Слів/хв;Знаків/хв;Помилки;Помилок %" + HistoryCsv.NEWLINE,
            body
        )
    }

    /** Один запис пишеться колонками у правильному порядку. */
    @Test
    fun `attempt row has the right columns`() {
        val csv = HistoryCsv.export(listOf(attempt()), zone)
        val lines = csv.removePrefix(HistoryCsv.BOM).split(HistoryCsv.NEWLINE)

        // [0] — заголовок, [1] — запис, [2] — порожній хвіст після останнього \r\n.
        assertEquals("Іван;2026-08-19 12:30;2;84;420;3;12,5", lines[1])
    }

    /** Клас 0 («не обрано») лишає комірку порожньою, а не пише «0». */
    @Test
    fun `grade zero becomes an empty cell`() {
        val csv = HistoryCsv.export(listOf(attempt(grade = 0)), zone)
        val row = csv.removePrefix(HistoryCsv.BOM).split(HistoryCsv.NEWLINE)[1]

        assertEquals("Іван;2026-08-19 12:30;;84;420;3;12,5", row)
    }

    /** Імʼя з роздільником не ламає таблицю. */
    @Test
    fun `student name with a separator stays in one cell`() {
        val csv = HistoryCsv.export(listOf(attempt(studentName = "Клас 2; Іван")), zone)
        val row = csv.removePrefix(HistoryCsv.BOM).split(HistoryCsv.NEWLINE)[1]

        assertTrue(row.startsWith("\"Клас 2; Іван\";"))
        // Рівно сім колонок: обрамлене імʼя не додало зайвого роздільника.
        assertFalse(row.removePrefix("\"Клас 2; Іван\"").startsWith(";;"))
    }

    /** Відсоток пишеться з комою — десятковий знак української локалі Excel. */
    @Test
    fun `error percent uses a comma`() {
        val csv = HistoryCsv.export(listOf(attempt(errorPercent = 7.0)), zone)
        val row = csv.removePrefix(HistoryCsv.BOM).split(HistoryCsv.NEWLINE)[1]

        assertTrue("очікували кому в десятковому: $row", row.endsWith(";7,0"))
    }
}

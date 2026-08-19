package net.readflow.core

import net.readflow.model.Attempt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Експорт історії замірів у CSV (`SPEC.md`, розділ «CSV»; Задача 8).
 *
 * Чистий Kotlin, без залежностей від Android: файл пише вже інтерфейс, а тут
 * лише збирається текст — щоб правила екранування перевірялися звичайними
 * JVM-тестами.
 *
 * Формат — той самий, що вимагає специфікація для обох платформ:
 * - **UTF-8 з BOM** ([BOM]) — інакше Excel в українській локалі показує кирилицю
 *   «кракозябрами»;
 * - роздільник **`;`** — бо кома в українській локалі Excel це десятковий знак,
 *   і рядок із комою поїхав би в одну комірку;
 * - екранування за **RFC 4180** — окремим хелпером [field], а не конкатенацією:
 *   значення з `;`, лапками чи переносом рядка обрамлюється лапками, а лапки
 *   всередині подвоюються.
 *
 * Дробові числа пишуться з **комою**, як і чекає українська локаль Excel; кома
 * не роздільник, тож екранування не потребує — саме тому роздільник і `;`.
 */
object HistoryCsv {

    /** Мітка порядку байтів. Excel без неї не впізнає UTF-8. */
    const val BOM = "﻿"

    /** Роздільник полів. */
    const val SEPARATOR = ';'

    /** Розрив рядків CSV — `\r\n` за RFC 4180. */
    const val NEWLINE = "\r\n"

    /** Заголовки колонок — людиночитані, бо файл відкривають в Excel. */
    val HEADERS = listOf(
        "Учень",
        "Дата",
        "Клас",
        "Слів/хв",
        "Знаків/хв",
        "Помилки",
        "Помилок %"
    )

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    /**
     * Зібрати CSV усієї історії.
     *
     * @param zone часовий пояс для форматування дати; за замовчуванням системний.
     */
    fun export(attempts: List<Attempt>, zone: ZoneId = ZoneId.systemDefault()): String {
        val builder = StringBuilder(BOM)

        builder.append(row(HEADERS))

        for (attempt in attempts) {
            builder.append(
                row(
                    listOf(
                        attempt.studentName,
                        formatDate(attempt.createdAt, zone),
                        if (attempt.grade > 0) attempt.grade.toString() else "",
                        attempt.wordsPerMinute.toString(),
                        attempt.charsPerMinute.toString(),
                        attempt.errors.toString(),
                        formatPercent(attempt.errorPercent)
                    )
                )
            )
        }

        return builder.toString()
    }

    /** Один рядок CSV із завершальним переносом. */
    private fun row(values: List<String>): String =
        values.joinToString(SEPARATOR.toString()) { field(it) } + NEWLINE

    /**
     * Екранування одного поля за RFC 4180.
     *
     * Обрамлюємо лапками, лише коли є що ламати — `;`, лапки або перенос рядка;
     * лапки всередині подвоюємо. Порожнє поле лишається порожнім.
     */
    fun field(value: String): String {
        val needsQuoting = value.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }

        if (!needsQuoting) {
            return value
        }

        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun formatDate(epochMillis: Long, zone: ZoneId): String =
        dateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** Відсоток із комою — десятковий знак української локалі Excel. */
    private fun formatPercent(value: Double): String =
        String.format(Locale.forLanguageTag("uk"), "%.1f", value)
}

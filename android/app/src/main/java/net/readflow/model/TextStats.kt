package net.readflow.model

/**
 * Незмінний знімок статистики тексту. Створюється лише
 * через [net.readflow.core.TextStatsCalculator.calculate].
 */
data class TextStats(

    /** Кількість слів. */
    val wordCount: Int,

    /** Знаки разом із пробілами. Перенос рядка — один знак. */
    val charCount: Int,

    /** Знаки без пробілів, табуляцій і переносів. */
    val charCountNoSpaces: Int,

    /** Букви: Unicode-літери та цифри (див. `SPEC.md`, 4.1). */
    val letterCount: Int,

    /** Середня довжина слова в буквах, округлена до 0.1. */
    val averageWordLength: Double,

    /** Кількість речень (наближено, за роздільниками `. ! ? …`). */
    val sentenceCount: Int,

    /** Кількість абзаців за обраним режимом. */
    val paragraphCount: Int
) {
    companion object {
        /** Статистика порожнього тексту: усюди нулі. */
        val Empty = TextStats(0, 0, 0, 0, 0.0, 0, 0)
    }
}

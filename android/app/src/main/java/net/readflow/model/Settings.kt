package net.readflow.model

/** Тема оформлення: вибір учителя, а не лише системна. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Налаштування вчителя. Зберігаються в DataStore Preferences і застосовуються
 * одразу (`SPEC_ANDROID.md`, 2.1).
 *
 * Клас і семестр обидва потрібні для оцінки за нормою: `shared/norms.json`
 * описує норми **окремо для кожного семестру** (`SPEC.md`, 4.9), тож самого
 * лише класу не вистачило б, щоб знайти межі.
 */
data class Settings(

    /** Кегль тексту в зоні читання, sp. */
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,

    /** Міжрядковий інтервал як множник кегля. */
    val lineSpacing: Float = DEFAULT_LINE_SPACING,

    /** Тривалість заміру, с. */
    val durationSeconds: Int = 60,

    val theme: ThemeChoice = ThemeChoice.SYSTEM,

    /** Клас учня (1–4) або 0, якщо не обрано. */
    val grade: Int = 0,

    /** Семестр (1 або 2). */
    val semester: Int = 1
) {
    companion object {
        const val DEFAULT_FONT_SIZE_SP = 18
        const val MIN_FONT_SIZE_SP = 14
        const val MAX_FONT_SIZE_SP = 32

        const val DEFAULT_LINE_SPACING = 1.4f
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.2f

        /** «Клас не обрано» — оцінка за нормою не показується. */
        const val NO_GRADE = 0
    }
}

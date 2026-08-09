package net.readflow.core

/**
 * Оцінка результату відносно норми (`SPEC.md`, 4.9).
 */
enum class NormEvaluation {

    /** Норму не визначено: клас не обраний або його немає в довіднику. */
    UNKNOWN,

    BELOW,
    WITHIN,
    ABOVE
}

/** Норма для одного класу й семестру. **Межі входять у норму.** */
data class ReadingNorm(
    val grade: Int,
    val semester: Int,
    val min: Int,
    val max: Int
)

/**
 * Клас із його нормами по семестрах. Підпис приходить із `shared/norms.json`,
 * а не збирається в коді: інакше українська «1 клас» опинилася б у двох місцях
 * і розійшлася б із десктопом.
 */
data class GradeNorms(
    val grade: Int,
    val label: String,
    val semesters: List<ReadingNorm>
)

/**
 * Підписи оцінок. Живуть у тому самому `norms.json`, що й числа: вони частина
 * довідника, а не інтерфейсу, і мусять бути однакові на обох платформах.
 */
data class NormLabels(
    val below: String = DEFAULT_BELOW,
    val within: String = DEFAULT_WITHIN,
    val above: String = DEFAULT_ABOVE
) {
    companion object {
        // Запасні підписи на випадок, коли в довіднику блоку evaluation немає.
        // Це не обхід правила «норми не хардкодяться»: правило про числа норм,
        // а тут три слова інтерфейсу, без яких на екрані була б порожнеча.
        const val DEFAULT_BELOW = "нижче норми"
        const val DEFAULT_WITHIN = "у межах норми"
        const val DEFAULT_ABOVE = "вище норми"

        val Fallback = NormLabels()

        /** Порожній чи пробільний підпис замінюється запасним. */
        fun of(below: String?, within: String?, above: String?) = NormLabels(
            below = below?.takeIf { it.isNotBlank() } ?: DEFAULT_BELOW,
            within = within?.takeIf { it.isNotBlank() } ?: DEFAULT_WITHIN,
            above = above?.takeIf { it.isNotBlank() } ?: DEFAULT_ABOVE
        )
    }
}

/**
 * Довідник норм техніки читання.
 *
 * Ані числа, ані підписи тут не зашиті: усе приходить із `shared/norms.json`.
 * Клас містить лише правило оцінки — чисту функцію, дзеркало
 * `desktop/ReadFlow/Core/ReadingNorms.cs`.
 */
data class NormsCatalog(
    val version: Int = 0,
    val grades: List<GradeNorms> = emptyList(),
    val labels: NormLabels = NormLabels.Fallback,

    /** Звідки взяті норми — показується вчителю разом із числами. */
    val source: String = "",

    /** Застереження до норм (напр. що в НУШ вони рекомендаційні). */
    val note: String = ""
) {
    /** Чи є в довіднику хоч один клас. */
    val isEmpty: Boolean get() = grades.isEmpty()

    /** Норма для класу й семестру або `null`. */
    fun find(grade: Int, semester: Int): ReadingNorm? = grades
        .firstOrNull { it.grade == grade }
        ?.semesters
        ?.firstOrNull { it.semester == semester }

    /**
     * Оцінити швидкість відносно норми класу й семестру.
     *
     * Порівняння цілих, без повторного округлення: WPM уже округлений «від нуля»
     * на точному дробі (4.7). Інакше 22.5, показане як 23, могло б повернутися
     * до 22 і перескочити межу норми — поруч із числом 23 на екрані.
     */
    fun evaluate(wordsPerMinute: Int, grade: Int, semester: Int): NormEvaluation =
        evaluate(wordsPerMinute, find(grade, semester))

    fun evaluate(wordsPerMinute: Int, norm: ReadingNorm?): NormEvaluation = when {
        norm == null -> NormEvaluation.UNKNOWN
        wordsPerMinute < norm.min -> NormEvaluation.BELOW
        wordsPerMinute > norm.max -> NormEvaluation.ABOVE
        else -> NormEvaluation.WITHIN
    }

    /** Підпис оцінки з довідника. Для [NormEvaluation.UNKNOWN] — порожній рядок. */
    fun describe(evaluation: NormEvaluation): String = when (evaluation) {
        NormEvaluation.BELOW -> labels.below
        NormEvaluation.WITHIN -> labels.within
        NormEvaluation.ABOVE -> labels.above
        NormEvaluation.UNKNOWN -> ""
    }

    companion object {

        /**
         * Порожній довідник: норми не прочитались. Не помилка й не виняток —
         * застосунок працює далі, просто без оцінки за нормою.
         */
        val Empty = NormsCatalog()

        /** Версія формату, яку розуміє цей код (`SPEC.md`, 4.9). */
        const val SUPPORTED_VERSION = 1
    }
}

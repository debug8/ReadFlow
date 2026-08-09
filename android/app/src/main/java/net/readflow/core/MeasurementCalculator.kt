package net.readflow.core

import java.math.BigDecimal

/**
 * Усе, що потрібно, щоб порахувати підсумок заміру. Складається у ViewModel
 * і передається сюди одним обʼєктом — щоб правило лишалося чистою функцією
 * й перевірялося звичайними JVM-тестами.
 */
data class MeasurementInput(

    val mode: MeasurementMode,

    /** Обрана тривалість заміру, с. У режимі A саме вона йде у формулу. */
    val durationSeconds: Int,

    /** Фактичний час секундоміра, мс. */
    val elapsedMillis: Long,

    /** Замір іще триває: підсумку поки немає. */
    val isRunning: Boolean,

    /** Усього слів у тексті. */
    val totalWords: Int,

    /** Усього знаків без пробілів. */
    val totalCharsNoSpaces: Int,

    /** Номер слова-межі (від 1) або `null`, якщо межі немає. */
    val boundaryWordNumber: Int? = null,

    /** Знаки без пробілів від початку тексту до слова-межі включно. */
    val boundaryCharsNoSpaces: Int = 0,

    /** Номери слів, позначених як помилка. */
    val errorWordNumbers: Set<Int> = emptySet()
)

/**
 * Підсумок заміру. Усі числа вже округлені за правилами `SPEC.md`, 4.7 —
 * інтерфейсу лишається тільки показати їх.
 */
data class MeasurementResult(

    /** Скільки слів учень прочитав. */
    val wordsRead: Int,

    /** Скільки знаків без пробілів учень прочитав. */
    val charsRead: Int,

    /** Час, який пішов у формулу, с. */
    val seconds: BigDecimal,

    val wordsPerMinute: Int,

    val charsPerMinute: Int,

    /** Помилки в межах прочитаного. */
    val errors: Int,

    /** Відсоток помилок від прочитаного, до 0.1. */
    val errorPercent: Double,

    /** «Чиста» швидкість: правильні слова за хвилину. */
    val cleanWordsPerMinute: Int
) {
    /** Час у форматі мм:сс — для підсумку, не для лічильника, що тікає. */
    val secondsRounded: Int get() = seconds.setScale(0, java.math.RoundingMode.HALF_UP).toInt()
}

/**
 * Правила розділу 4.7 в одному місці: скільки прочитано, за який час
 * і що з цього виходить.
 */
object MeasurementCalculator {

    /**
     * Підсумок заміру або `null`, якщо його ще немає.
     *
     * Підсумку немає, доки триває замір, доки нема тексту, а в режимі A —
     * доки вчитель не показав, докуди учень дочитав: без межі фіксована
     * тривалість перетворила б «прочитав перший абзац» на «прочитав усе».
     */
    fun evaluate(input: MeasurementInput): MeasurementResult? {
        if (input.isRunning || input.totalWords == 0) {
            return null
        }

        if (input.mode.usesFixedDuration) {
            if (input.boundaryWordNumber == null) {
                return null
            }
        } else if (input.elapsedMillis <= 0L) {
            return null
        }

        val seconds = if (input.mode.usesFixedDuration) {
            SpeedCalculator.secondsOf(input.durationSeconds)
        } else {
            SpeedCalculator.secondsOf(input.elapsedMillis)
        }

        // Без межі прочитаним вважається весь текст (`SPEC.md`, 4.7).
        val wordsRead = input.boundaryWordNumber ?: input.totalWords
        val charsRead =
            if (input.boundaryWordNumber == null) input.totalCharsNoSpaces
            else input.boundaryCharsNoSpaces

        // Помилки за межею читання не рахуються: учень туди не дочитав.
        // Позначки при цьому лишаються — перенесли межу, і вони знову в грі.
        val errors = input.errorWordNumbers.count { it <= wordsRead }

        return MeasurementResult(
            wordsRead = wordsRead,
            charsRead = charsRead,
            seconds = seconds,
            wordsPerMinute = SpeedCalculator.wordsPerMinute(wordsRead, seconds),
            charsPerMinute = SpeedCalculator.charsPerMinute(charsRead, seconds),
            errors = errors,
            errorPercent = SpeedCalculator.errorPercent(errors, wordsRead),
            // Та сама формула, просто інший чисельник.
            cleanWordsPerMinute = SpeedCalculator.wordsPerMinute(wordsRead - errors, seconds)
        )
    }
}

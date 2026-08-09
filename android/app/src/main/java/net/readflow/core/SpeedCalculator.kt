package net.readflow.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Формули швидкості читання (`SPEC.md`, 4.7).
 *
 * Чисті функції без стану й без залежностей від Android — дзеркало
 * `desktop/ReadFlow/Core/SpeedCalculator.cs`. На однакових числах обидві
 * реалізації мусять давати однаковий результат.
 */
object SpeedCalculator {

    private val SECONDS_PER_MINUTE = BigDecimal(60)
    private val MILLIS_PER_SECOND = BigDecimal(1000)
    private val HUNDRED = BigDecimal(100)

    /** Слів за хвилину. */
    fun wordsPerMinute(wordsRead: Int, seconds: BigDecimal): Int = perMinute(wordsRead, seconds)

    /**
     * Знаків за хвилину. Знаки — **без пробілів**: пробіли не вимовляються,
     * тож у швидкості читання вголос їм місця немає.
     */
    fun charsPerMinute(charsRead: Int, seconds: BigDecimal): Int = perMinute(charsRead, seconds)

    /**
     * Відсоток помилок від прочитаного, до 0.1.
     *
     * Округлення — «від нуля» на точному дробі, як і все в розділі 4:
     * 5 помилок із 40 слів це рівно 12.5%, справжня середина, і на `Double`
     * результат залежав би від платформи.
     */
    fun errorPercent(errors: Int, wordsRead: Int): Double {
        if (errors <= 0 || wordsRead <= 0) {
            return 0.0
        }

        return BigDecimal(errors)
            .multiply(HUNDRED)
            .divide(BigDecimal(wordsRead), 1, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Скільки приблизно хвилин займе текст на заданій швидкості.
     * Зворотна задача до [wordsPerMinute]; не менше однієї хвилини —
     * «≈ 0 хв» не сказало б учителю нічого.
     */
    fun minutesToRead(words: Int, wordsPerMinute: Int): Int {
        if (words <= 0 || wordsPerMinute <= 0) {
            return 0
        }

        val minutes = BigDecimal(words)
            .divide(BigDecimal(wordsPerMinute), 0, RoundingMode.HALF_UP)
            .toInt()

        return if (minutes < 1) 1 else minutes
    }

    /**
     * Мілісекунди в секунди **без втрати точності**.
     *
     * `millis / 1000.0` дав би `Double`, і тоді 22.5 у формулі швидкості
     * перестало б бути рівно 22.5 — рівно та пастка, від якої застерігає
     * `SPEC.md`, 4.4. Ділення на 1000 у `BigDecimal` завжди точне.
     */
    fun secondsOf(millis: Long): BigDecimal =
        BigDecimal(millis).divide(MILLIS_PER_SECOND)

    /** Секунди як точне ціле — для режиму A, де час береться з обраної тривалості. */
    fun secondsOf(seconds: Int): BigDecimal = BigDecimal(seconds)

    private fun perMinute(amount: Int, seconds: BigDecimal): Int {
        if (amount <= 0 || seconds.signum() <= 0) {
            return 0
        }

        // Ділення на точному дробі, а не на Double: 45 слів за 120 с — це рівно
        // 22.5, справжня середина, і на Double результат залежав би від платформи.
        return BigDecimal(amount)
            .multiply(SECONDS_PER_MINUTE)
            .divide(seconds, 0, RoundingMode.HALF_UP)
            .toInt()
    }
}

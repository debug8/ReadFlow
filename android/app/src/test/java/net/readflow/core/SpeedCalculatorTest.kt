package net.readflow.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Формули швидкості читання (`SPEC.md`, 4.7).
 *
 * Числа перенесені **один в один** із `desktop/ReadFlow.Tests/SpeedCalculatorTests.cs`:
 * якщо десктоп покаже 23 слова за хвилину, телефон мусить показати 23.
 */
class SpeedCalculatorTest {

    private fun sec(value: String) = BigDecimal(value)

    @Test
    fun `words per minute - basic cases`() {
        assertEquals(60, SpeedCalculator.wordsPerMinute(60, sec("60")))
        assertEquals(120, SpeedCalculator.wordsPerMinute(60, sec("30")))
        assertEquals(30, SpeedCalculator.wordsPerMinute(60, sec("120")))
        assertEquals(84, SpeedCalculator.wordsPerMinute(84, sec("60")))
    }

    @Test
    fun `chars per minute - basic cases`() {
        assertEquals(420, SpeedCalculator.charsPerMinute(420, sec("60")))
        assertEquals(840, SpeedCalculator.charsPerMinute(420, sec("30")))
    }

    /**
     * 45 слів за 120 с — це рівно 22.5, справжня середина. «Від нуля» дає 23;
     * банківське округлення дало б 22, а обчислення через `Double` залежало б
     * від платформи.
     */
    @Test
    fun `rounding is half away from zero on the exact fraction`() {
        assertEquals(23, SpeedCalculator.wordsPerMinute(45, sec("120")))
        assertEquals(24, SpeedCalculator.wordsPerMinute(47, sec("120")))
        assertEquals(11, SpeedCalculator.wordsPerMinute(21, sec("120")))
    }

    @Test
    fun `rounding of non-midpoint values`() {
        // 100 слів за 90 с = 66.66… -> 67
        assertEquals(67, SpeedCalculator.wordsPerMinute(100, sec("90")))

        // 100 слів за 91 с = 65.93… -> 66
        assertEquals(66, SpeedCalculator.wordsPerMinute(100, sec("91")))
    }

    /** Режим B дає фактичний час, а він майже ніколи не цілий. */
    @Test
    fun `fractional seconds are supported`() {
        // 84 слова за 47.5 с = 106.10… -> 106
        assertEquals(106, SpeedCalculator.wordsPerMinute(84, sec("47.5")))
    }

    @Test
    fun `zero or negative time returns zero instead of throwing`() {
        assertEquals(0, SpeedCalculator.wordsPerMinute(100, sec("0")))
        assertEquals(0, SpeedCalculator.wordsPerMinute(100, sec("-5")))
        assertEquals(0, SpeedCalculator.charsPerMinute(100, sec("0")))
    }

    @Test
    fun `zero or negative amount returns zero`() {
        assertEquals(0, SpeedCalculator.wordsPerMinute(0, sec("60")))
        assertEquals(0, SpeedCalculator.charsPerMinute(0, sec("60")))
        assertEquals(0, SpeedCalculator.wordsPerMinute(-3, sec("60")))
    }

    /** Учитель випадково натиснув Старт і одразу Стоп. */
    @Test
    fun `very short time does not overflow`() {
        assertEquals(6000, SpeedCalculator.wordsPerMinute(1, sec("0.01")))
    }

    /**
     * Мілісекунди в секунди мусять переводитися точно: інакше замір «рівно
     * дві хвилини» перестав би давати рівно 22.5 у формулі.
     */
    @Test
    fun `millis convert to seconds exactly`() {
        // compareTo, а не equals: у BigDecimal «120» і «120.000» рівні за
        // значенням, але різні за масштабом, і equals це розрізняє.
        assertEquals(0, SpeedCalculator.secondsOf(120_000L).compareTo(sec("120")))
        assertEquals(0, SpeedCalculator.secondsOf(47_500L).compareTo(sec("47.5")))
        assertEquals(23, SpeedCalculator.wordsPerMinute(45, SpeedCalculator.secondsOf(120_000L)))
        assertEquals(106, SpeedCalculator.wordsPerMinute(84, SpeedCalculator.secondsOf(47_500L)))
    }

    /** Відсоток помилок — теж на точному дробі (`SPEC.md`, 4.7). */
    @Test
    fun `error percent rounds half away from zero`() {
        assertEquals(20.0, SpeedCalculator.errorPercent(2, 10), 0.001)
        assertEquals(40.0, SpeedCalculator.errorPercent(4, 10), 0.001)

        // 1 із 3 = 33.33… -> 33.3
        assertEquals(33.3, SpeedCalculator.errorPercent(1, 3), 0.001)

        // 5 із 40 = рівно 12.5
        assertEquals(12.5, SpeedCalculator.errorPercent(5, 40), 0.001)
    }

    @Test
    fun `error percent without errors or words is zero`() {
        assertEquals(0.0, SpeedCalculator.errorPercent(0, 40), 0.001)
        assertEquals(0.0, SpeedCalculator.errorPercent(3, 0), 0.001)
    }

    @Test
    fun `minutes to read never drops below one`() {
        assertEquals(2, SpeedCalculator.minutesToRead(120, 60))
        assertEquals(1, SpeedCalculator.minutesToRead(10, 60))
        assertEquals(0, SpeedCalculator.minutesToRead(0, 60))
        assertEquals(0, SpeedCalculator.minutesToRead(100, 0))
    }
}

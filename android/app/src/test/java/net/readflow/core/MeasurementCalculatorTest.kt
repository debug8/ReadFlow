package net.readflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Підсумок заміру: що вважається прочитаним, за який час і що з цього виходить
 * (`SPEC.md`, 4.7–4.8).
 */
class MeasurementCalculatorTest {

    private fun input(
        mode: MeasurementMode = MeasurementMode.TIMER,
        durationSeconds: Int = 60,
        elapsedMillis: Long = 60_000,
        isRunning: Boolean = false,
        totalWords: Int = 100,
        totalCharsNoSpaces: Int = 500,
        boundary: Int? = null,
        boundaryChars: Int = 0,
        errors: Set<Int> = emptySet()
    ) = MeasurementInput(
        mode = mode,
        durationSeconds = durationSeconds,
        elapsedMillis = elapsedMillis,
        isRunning = isRunning,
        totalWords = totalWords,
        totalCharsNoSpaces = totalCharsNoSpaces,
        boundaryWordNumber = boundary,
        boundaryCharsNoSpaces = boundaryChars,
        errorWordNumbers = errors
    )

    /** Поки замір іде, підсумку немає: показувати проміжний WPM — брехати. */
    @Test
    fun `no result while the measurement is running`() {
        assertNull(MeasurementCalculator.evaluate(input(isRunning = true)))
    }

    /** Без тексту рахувати нема чого. */
    @Test
    fun `no result without words`() {
        assertNull(MeasurementCalculator.evaluate(input(totalWords = 0)))
    }

    /** Режим B без межі: прочитаним вважається весь текст. */
    @Test
    fun `timer mode without a boundary counts the whole text`() {
        val result = MeasurementCalculator.evaluate(input())!!

        assertEquals(100, result.wordsRead)
        assertEquals(500, result.charsRead)
        assertEquals(100, result.wordsPerMinute)
        assertEquals(500, result.charsPerMinute)
    }

    /** Режим B із межею: рахуються слова до неї включно. */
    @Test
    fun `timer mode with a boundary counts up to it`() {
        val result = MeasurementCalculator.evaluate(
            input(boundary = 40, boundaryChars = 200)
        )!!

        assertEquals(40, result.wordsRead)
        assertEquals(200, result.charsRead)
        assertEquals(40, result.wordsPerMinute)
        assertEquals(200, result.charsPerMinute)
    }

    /** Режим B бере фактичний час секундоміра, а не обрану тривалість. */
    @Test
    fun `timer mode uses the actual elapsed time`() {
        val result = MeasurementCalculator.evaluate(
            input(durationSeconds = 60, elapsedMillis = 30_000)
        )!!

        assertEquals(200, result.wordsPerMinute)
    }

    /**
     * Режим A бере **обрану тривалість**, хоч би скільки насправді минуло
     * (`SPEC.md`, 4.8): там учитель тапає слово замість тиснути Стоп.
     */
    @Test
    fun `tap-stop mode uses the chosen duration`() {
        val result = MeasurementCalculator.evaluate(
            input(
                mode = MeasurementMode.TAP_STOP,
                durationSeconds = 120,
                elapsedMillis = 7_000,
                boundary = 45,
                boundaryChars = 220
            )
        )!!

        // 45 слів за 120 с — це рівно 22.5, і «від нуля» дає 23.
        assertEquals(23, result.wordsPerMinute)
    }

    /** У режимі A без межі підсумку немає: невідомо, скільки учень прочитав. */
    @Test
    fun `tap-stop mode without a boundary has no result`() {
        assertNull(
            MeasurementCalculator.evaluate(input(mode = MeasurementMode.TAP_STOP, boundary = null))
        )
    }

    /** Режим B без часу підсумку не дає: Старт не натискали. */
    @Test
    fun `timer mode without elapsed time has no result`() {
        assertNull(MeasurementCalculator.evaluate(input(elapsedMillis = 0)))
    }

    /** Помилки за межею читання не рахуються, але позначки лишаються. */
    @Test
    fun `errors past the boundary are not counted`() {
        val result = MeasurementCalculator.evaluate(
            input(
                mode = MeasurementMode.ERRORS,
                boundary = 10,
                boundaryChars = 50,
                errors = setOf(2, 5, 10, 11, 40)
            )
        )!!

        assertEquals("Три позначки в межах прочитаного.", 3, result.errors)
        assertEquals(10, result.wordsRead)
    }

    /** Перенесли межу далі — позначки за нею знову в грі. */
    @Test
    fun `moving the boundary brings the marks back`() {
        val marks = setOf(2, 5, 10, 11, 40)

        val near = MeasurementCalculator.evaluate(
            input(mode = MeasurementMode.ERRORS, boundary = 10, errors = marks)
        )!!
        val far = MeasurementCalculator.evaluate(
            input(mode = MeasurementMode.ERRORS, boundary = 50, errors = marks)
        )!!

        assertEquals(3, near.errors)
        assertEquals(5, far.errors)
    }

    /** «Чиста» швидкість — та сама формула, просто інший чисельник. */
    @Test
    fun `clean speed drops the errors`() {
        val result = MeasurementCalculator.evaluate(
            input(
                mode = MeasurementMode.ERRORS,
                elapsedMillis = 60_000,
                boundary = 10,
                errors = setOf(1, 2)
            )
        )!!

        assertEquals(10, result.wordsPerMinute)
        assertEquals(8, result.cleanWordsPerMinute)
        assertEquals(20.0, result.errorPercent, 0.001)
    }

    /** Без помилок чиста швидкість дорівнює звичайній. */
    @Test
    fun `clean speed equals the plain one without errors`() {
        val result = MeasurementCalculator.evaluate(input(boundary = 10))!!

        assertEquals(result.wordsPerMinute, result.cleanWordsPerMinute)
        assertEquals(0.0, result.errorPercent, 0.001)
    }

    /**
     * Час у підсумку округлюється до секунди — на екрані мм:сс, а не мілісекунди.
     * У формулу при цьому йде повний час.
     */
    @Test
    fun `seconds are rounded only for display`() {
        val result = MeasurementCalculator.evaluate(input(elapsedMillis = 47_500))!!

        assertEquals(48, result.secondsRounded)
        assertEquals(126, result.wordsPerMinute)
    }
}

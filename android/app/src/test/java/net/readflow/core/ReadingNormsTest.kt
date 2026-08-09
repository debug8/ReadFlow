package net.readflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Правило оцінки за нормою (`SPEC.md`, 4.9).
 * Числа ті самі, що в `desktop/ReadFlow.Tests/ReadingNormsTests.cs`.
 */
class ReadingNormsTest {

    private val catalog = NormsCatalog(
        version = 1,
        grades = listOf(
            GradeNorms(
                grade = 2,
                label = "2 клас",
                semesters = listOf(
                    ReadingNorm(2, 1, 35, 45),
                    ReadingNorm(2, 2, 50, 60)
                )
            )
        ),
        labels = NormLabels.of("нижче норми", "у межах норми", "вище норми")
    )

    @Test
    fun `below within above`() {
        assertEquals(NormEvaluation.BELOW, catalog.evaluate(49, 2, 2))
        assertEquals(NormEvaluation.WITHIN, catalog.evaluate(55, 2, 2))
        assertEquals(NormEvaluation.ABOVE, catalog.evaluate(61, 2, 2))
    }

    /**
     * Межі входять у норму: учень, який прочитав рівно 50 слів за норми 50–60,
     * читає в нормі, а не нижче. «Строго більше» в наказі МОН немає.
     */
    @Test
    fun `boundaries are inclusive`() {
        assertEquals("Min має входити в норму.", NormEvaluation.WITHIN, catalog.evaluate(50, 2, 2))
        assertEquals("Max має входити в норму.", NormEvaluation.WITHIN, catalog.evaluate(60, 2, 2))
    }

    @Test
    fun `unknown grade or semester is unknown`() {
        assertEquals("Клас не обраний.", NormEvaluation.UNKNOWN, catalog.evaluate(55, 0, 2))
        assertEquals("Такого класу немає.", NormEvaluation.UNKNOWN, catalog.evaluate(55, 9, 2))
        assertEquals("Такого семестру немає.", NormEvaluation.UNKNOWN, catalog.evaluate(55, 2, 3))
    }

    @Test
    fun `zero speed is below the norm`() {
        assertEquals(NormEvaluation.BELOW, catalog.evaluate(0, 2, 1))
    }

    /** Порожній довідник нікого не валить — просто немає оцінки. */
    @Test
    fun `empty catalog evaluates to unknown`() {
        assertEquals(NormEvaluation.UNKNOWN, NormsCatalog.Empty.evaluate(55, 2, 2))
        assertEquals("", NormsCatalog.Empty.describe(NormEvaluation.UNKNOWN))
        assertNull(NormsCatalog.Empty.find(2, 2))
    }

    @Test
    fun `labels come from the catalog`() {
        assertEquals("нижче норми", catalog.describe(NormEvaluation.BELOW))
        assertEquals("у межах норми", catalog.describe(NormEvaluation.WITHIN))
        assertEquals("вище норми", catalog.describe(NormEvaluation.ABOVE))
        assertEquals("Для Unknown підпису немає.", "", catalog.describe(NormEvaluation.UNKNOWN))
    }

    /** Порожні підписи в довіднику замінюються запасними, а не порожнечею на екрані. */
    @Test
    fun `blank labels fall back to the defaults`() {
        val labels = NormLabels.of(" ", null, "")

        assertEquals(NormLabels.DEFAULT_BELOW, labels.below)
        assertEquals(NormLabels.DEFAULT_WITHIN, labels.within)
        assertEquals(NormLabels.DEFAULT_ABOVE, labels.above)
    }
}

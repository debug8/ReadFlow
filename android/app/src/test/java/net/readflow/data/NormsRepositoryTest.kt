package net.readflow.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.readflow.core.NormEvaluation
import net.readflow.core.NormsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Розбір `norms.json`.
 *
 * Перевіряє не стільки код, скільки те, що Gradle-таск `syncSharedAssets`
 * реально доклав `shared/norms.json` в APK — і що спільний із десктопом
 * довідник читається без правок.
 *
 * Robolectric потрібен через `org.json`: у чистому JVM-тесті він заглушений.
 */
@RunWith(RobolectricTestRunner::class)
class NormsRepositoryTest {

    private val repository = AssetNormsRepository(ApplicationProvider.getApplicationContext())

    /** Спільний довідник доїхав у assets і читається. */
    @Test
    fun `shared norms are readable`() = runTest {
        val catalog = repository.load()

        assertFalse("Довідник із shared/ не прочитався.", catalog.isEmpty)
        assertEquals(1, catalog.version)
        assertEquals(4, catalog.grades.size)
    }

    /** Норми з довідника збігаються з тими, за якими оцінює десктоп. */
    @Test
    fun `shared norms carry the expected numbers`() = runTest {
        val catalog = repository.load()

        val second = catalog.find(2, 2)
        assertNotNull(second)
        assertEquals(50, second!!.min)
        assertEquals(60, second.max)

        assertEquals(NormEvaluation.WITHIN, catalog.evaluate(50, 2, 2))
        assertEquals(NormEvaluation.BELOW, catalog.evaluate(49, 2, 2))
    }

    /** Підписи оцінок і застереження теж приходять із файлу, а не з коду. */
    @Test
    fun `labels source and note come from the file`() = runTest {
        val catalog = repository.load()

        assertEquals("нижче норми", catalog.describe(NormEvaluation.BELOW))
        assertTrue("Немає джерела норм.", catalog.source.isNotEmpty())
        assertTrue("Немає застереження.", catalog.note.isNotEmpty())
    }

    /** Класи в списку налаштувань ідуть по порядку, а не як у файлі. */
    @Test
    fun `grades are sorted`() = runTest {
        val grades = repository.load().grades.map { it.grade }

        assertEquals(listOf(1, 2, 3, 4), grades)
    }

    // --- Межові випадки розбору ---

    /** Битий JSON нічого не валить: просто немає оцінки. */
    @Test
    fun `broken json gives an empty catalog`() {
        assertEquals(NormsCatalog.Empty, NormsParser.parse("{ це не json"))
        assertEquals(NormsCatalog.Empty, NormsParser.parse(""))
        assertEquals(NormsCatalog.Empty, NormsParser.parse(null))
    }

    /**
     * Довідник новішої версії не читаємо: краще лишитися без оцінки, ніж
     * показати число, зрозуміле нам не так, як його задумали.
     */
    @Test
    fun `newer version is refused`() {
        val json = """
            { "version": 2, "grades": [ { "grade": 1, "label": "1 клас",
              "semesters": [ { "semester": 1, "min": 10, "max": 20 } ] } ] }
        """.trimIndent()

        assertTrue(NormsParser.parse(json).isEmpty)
    }

    /** Норма з переверненими межами пропускається, решта класу лишається. */
    @Test
    fun `inverted bounds are skipped and the rest survives`() {
        val json = """
            { "version": 1, "grades": [ { "grade": 1, "label": "1 клас", "semesters": [
                { "semester": 1, "min": 30, "max": 10 },
                { "semester": 2, "min": 20, "max": 30 } ] } ] }
        """.trimIndent()

        val catalog = NormsParser.parse(json)

        assertNull("Перевернуту норму треба пропустити.", catalog.find(1, 1))
        assertNotNull("Здорова норма мусить лишитися.", catalog.find(1, 2))
    }

    /** Клас без жодної цілої норми в довідник не потрапляє. */
    @Test
    fun `grade without usable norms is dropped`() {
        val json = """
            { "version": 1, "grades": [ { "grade": 1, "label": "1 клас", "semesters": [
                { "semester": 1, "min": 30, "max": 10 } ] } ] }
        """.trimIndent()

        assertTrue(NormsParser.parse(json).isEmpty)
    }

    /** Довідник без блоку evaluation читається — з запасними підписами. */
    @Test
    fun `missing evaluation block falls back to the default labels`() {
        val json = """
            { "version": 1, "grades": [ { "grade": 1, "label": "1 клас", "semesters": [
                { "semester": 1, "min": 10, "max": 20 } ] } ] }
        """.trimIndent()

        val catalog = NormsParser.parse(json)

        assertFalse(catalog.isEmpty)
        assertEquals("нижче норми", catalog.describe(NormEvaluation.BELOW))
    }
}

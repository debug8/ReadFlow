package net.readflow.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.readflow.core.TextStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Перевіряє не стільки код репозиторію, скільки те, що Gradle-таск
 * `syncSharedAssets` реально доклав файли з `shared/` в APK — і що реєстр
 * не розійшовся з текстами.
 */
@RunWith(RobolectricTestRunner::class)
class AssetSampleRepositoryTest {

    private val repository = AssetSampleRepository(ApplicationProvider.getApplicationContext())

    /** Реєстр із shared/ доїхав у assets і читається. */
    @Test
    fun `index from shared is readable`() = runTest {
        val samples = repository.list()

        assertTrue("Реєстр порожній — перевір таск syncSharedAssets.", samples.isNotEmpty())
        assertTrue(samples.any { it.id == "sample-01" })
    }

    /** Текст кожного зразка читається й не порожній. */
    @Test
    fun `every registered sample has a readable text`() = runTest {
        for (sample in repository.list()) {
            val text = repository.load(sample)

            assertTrue("Текст ${sample.file} не читається.", text.isNotEmpty())
        }
    }

    /**
     * Поле `words` в реєстрі має збігатися з фактичним підрахунком.
     * README папки samples вимагає заповнювати його підрахунком, а не «на око»;
     * цей тест не дає числу протухнути після правки тексту.
     */
    @Test
    fun `words field matches the actual count`() = runTest {
        for (sample in repository.list()) {
            val actual = TextStatsCalculator.calculate(repository.load(sample)).wordCount

            assertEquals(
                "У index.json для ${sample.id} записано ${sample.words} слів, а насправді $actual.",
                actual,
                sample.words
            )
        }
    }
}

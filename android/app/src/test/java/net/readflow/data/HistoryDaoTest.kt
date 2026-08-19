package net.readflow.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.readflow.model.Attempt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room-сховище історії: запис доживає до читання, видалення прибирає рядок,
 * а список приходить новішими згори. База — у памʼяті через Robolectric,
 * без емулятора.
 *
 * Це та частина Задачі 8, яку не покриє фейк: саме тут перевіряється, що
 * автоінкремент, сортування й міст «сутність ↔ модель» справді працюють.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {

    private lateinit var database: HistoryDatabase
    private lateinit var repository: RoomHistoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HistoryDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = RoomHistoryRepository(database.attemptDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun attempt(name: String, at: Long, wpm: Int = 80) = Attempt(
        studentName = name,
        createdAt = at,
        grade = 2,
        wordsPerMinute = wpm,
        charsPerMinute = wpm * 5,
        errors = 1,
        errorPercent = 2.5
    )

    /** Збережений запис читається назад із призначеним ключем. */
    @Test
    fun `saved attempt is read back`() = runTest {
        val id = repository.save(attempt("Іван", at = 1000))

        val stored = repository.snapshot()

        assertEquals(1, stored.size)
        assertEquals(id, stored.first().id)
        assertEquals("Іван", stored.first().studentName)
        assertEquals(80, stored.first().wordsPerMinute)
        assertTrue("Ключ має бути призначений", id > 0)
    }

    /** Новіші записи — згори. */
    @Test
    fun `history is newest first`() = runTest {
        repository.save(attempt("Старий", at = 1_000))
        repository.save(attempt("Новий", at = 5_000))

        val list = repository.history.first()

        assertEquals(listOf("Новий", "Старий"), list.map { it.studentName })
    }

    /** Видалення прибирає саме той запис. */
    @Test
    fun `delete removes the record`() = runTest {
        val keep = repository.save(attempt("Лишити", at = 2_000))
        val drop = repository.save(attempt("Прибрати", at = 3_000))

        repository.delete(drop)

        val list = repository.snapshot()
        assertEquals(1, list.size)
        assertEquals(keep, list.first().id)
    }
}

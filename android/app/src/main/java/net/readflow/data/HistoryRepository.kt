package net.readflow.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.readflow.model.Attempt

/**
 * Історія замірів.
 *
 * Інтерфейс — щоб ViewModel лишалася на звичайних JVM-тестах: Room тягне за
 * собою SQLite й Android, а тут потрібен лише фейк у памʼяті. Те саме рішення,
 * що з нормами, зразками й налаштуваннями.
 */
interface HistoryRepository {

    /** Уся історія, новіші згори; нове значення на кожну зміну. */
    val history: Flow<List<Attempt>>

    /** Зберегти запис; повертає призначений ключ. */
    suspend fun save(attempt: Attempt): Long

    suspend fun delete(id: Long)

    /** Одноразовий знімок — для експорту в CSV. */
    suspend fun snapshot(): List<Attempt>
}

/** Сховище на Room. */
class RoomHistoryRepository(private val dao: AttemptDao) : HistoryRepository {

    override val history: Flow<List<Attempt>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun save(attempt: Attempt): Long =
        dao.insert(AttemptEntity.of(attempt.copy(id = 0L)))

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun snapshot(): List<Attempt> = dao.getAll().map { it.toModel() }

    companion object {
        fun create(context: Context): RoomHistoryRepository =
            RoomHistoryRepository(HistoryDatabase.get(context).attemptDao())
    }
}

/**
 * Історія в памʼяті — для тестів і превʼю. Ключі роздаються за зростанням,
 * як робить автоінкремент Room; порядок — новіші згори.
 */
class InMemoryHistoryRepository(initial: List<Attempt> = emptyList()) : HistoryRepository {

    private val state = MutableStateFlow(initial.sortedByDescending { it.createdAt })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override val history: Flow<List<Attempt>> = state.asStateFlow()

    override suspend fun save(attempt: Attempt): Long {
        val id = nextId++
        state.update { current ->
            (current + attempt.copy(id = id)).sortedByDescending { it.createdAt }
        }
        return id
    }

    override suspend fun delete(id: Long) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun snapshot(): List<Attempt> = state.value
}

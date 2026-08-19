package net.readflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Доступ до таблиці `attempts`.
 *
 * Новіші записи — згори: історію читають зверху вниз, а не гортають до кінця.
 * При однаковій мітці часу ключ (автоінкремент) впорядковує стабільно.
 */
@Dao
interface AttemptDao {

    @Query("SELECT * FROM attempts ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<AttemptEntity>>

    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("DELETE FROM attempts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Одноразове читання — для експорту в CSV і тестів. */
    @Query("SELECT * FROM attempts ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<AttemptEntity>
}

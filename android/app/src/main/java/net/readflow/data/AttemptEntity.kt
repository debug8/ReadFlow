package net.readflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.readflow.model.Attempt

/**
 * Рядок таблиці `attempts` — сховищна проєкція [Attempt].
 *
 * Room тримається окремо від доменної моделі: ViewModel і CSV-експортер бачать
 * лише [Attempt] і не тягнуть за собою анотації Room, тож лишаються на
 * звичайних JVM-тестах.
 */
@Entity(tableName = "attempts")
data class AttemptEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val studentName: String,

    val createdAt: Long,

    val grade: Int,

    val wordsPerMinute: Int,

    val charsPerMinute: Int,

    val errors: Int,

    val errorPercent: Double
) {
    fun toModel(): Attempt = Attempt(
        id = id,
        studentName = studentName,
        createdAt = createdAt,
        grade = grade,
        wordsPerMinute = wordsPerMinute,
        charsPerMinute = charsPerMinute,
        errors = errors,
        errorPercent = errorPercent
    )

    companion object {
        /** Новий запис для вставки: `id = 0`, ключ призначить Room. */
        fun of(attempt: Attempt): AttemptEntity = AttemptEntity(
            id = attempt.id,
            studentName = attempt.studentName,
            createdAt = attempt.createdAt,
            grade = attempt.grade,
            wordsPerMinute = attempt.wordsPerMinute,
            charsPerMinute = attempt.charsPerMinute,
            errors = attempt.errors,
            errorPercent = attempt.errorPercent
        )
    }
}

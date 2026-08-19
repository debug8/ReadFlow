package net.readflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * База історії замірів. Одна таблиця — `attempts` (`SPEC_ANDROID.md`, розділ 5).
 *
 * `exportSchema = false`: додаток офлайновий і локальний, версійних міграцій
 * схеми ще немає, тож окрема тека зі схемами лише плодила б файли.
 */
@Database(entities = [AttemptEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun attemptDao(): AttemptDao

    companion object {

        private const val DB_NAME = "history.db"

        @Volatile
        private var instance: HistoryDatabase? = null

        /** Єдиний екземпляр на процес: відкривати базу на кожен виклик марно. */
        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): HistoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HistoryDatabase::class.java,
                DB_NAME
            ).build()
    }
}

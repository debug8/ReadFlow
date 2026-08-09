package net.readflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.readflow.model.Settings
import net.readflow.model.ThemeChoice
import java.io.IOException

/**
 * Налаштування вчителя.
 *
 * Інтерфейс — щоб ViewModel лишалася на звичайних JVM-тестах: DataStore тягне
 * за собою файлову систему й Android.
 */
interface SettingsRepository {

    /** Поточні налаштування; нове значення на кожну зміну. */
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Сховище на DataStore Preferences.
 *
 * Читання ніколи не падає: пошкоджений файл дає порожні налаштування, а не
 * виняток при старті — те саме правило, що й для норм та зразків.
 */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.dataStore

    override val settings: Flow<Settings> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { preferences ->
            val updated = transform(preferences.toSettings())

            preferences[FONT_SIZE] = updated.fontSizeSp
            preferences[LINE_SPACING] = updated.lineSpacing
            preferences[DURATION] = updated.durationSeconds
            preferences[THEME] = updated.theme.name
            preferences[GRADE] = updated.grade
            preferences[SEMESTER] = updated.semester
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()

        return Settings(
            fontSizeSp = this[FONT_SIZE] ?: defaults.fontSizeSp,
            lineSpacing = this[LINE_SPACING] ?: defaults.lineSpacing,
            durationSeconds = this[DURATION] ?: defaults.durationSeconds,
            // Невідома назва теми (відкат на стару версію) — системна,
            // а не падіння на valueOf.
            theme = this[THEME]?.let { name ->
                ThemeChoice.entries.firstOrNull { it.name == name }
            } ?: defaults.theme,
            grade = this[GRADE] ?: defaults.grade,
            semester = this[SEMESTER] ?: defaults.semester
        )
    }

    private companion object {
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val DURATION = intPreferencesKey("duration_seconds")
        val THEME = stringPreferencesKey("theme")
        val GRADE = intPreferencesKey("grade")
        val SEMESTER = intPreferencesKey("semester")
    }
}

/** Налаштування в памʼяті — для тестів і превʼю. */
class InMemorySettingsRepository(initial: Settings = Settings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<Settings> = state.asStateFlow()

    override suspend fun update(transform: (Settings) -> Settings) {
        state.update(transform)
    }
}

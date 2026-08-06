package net.readflow.data

import android.content.Context
import net.readflow.model.TextSample
import org.json.JSONObject
import java.io.IOException

/**
 * Джерело текстів-зразків.
 *
 * Інтерфейс потрібен не заради абстракції як такої: він дає змогу тестувати
 * ViewModel звичайними JVM-тестами, без Android і без Robolectric.
 */
interface SampleRepository {

    /** Список зразків із реєстру. Порожній список — реєстру немає або він битий. */
    suspend fun list(): List<TextSample>

    /** Текст зразка. Порожній рядок — файл не читається. */
    suspend fun load(sample: TextSample): String
}

/**
 * Читає зразки з `assets/samples/`, куди їх кладе Gradle-таск `syncSharedAssets`
 * із папки `shared/samples/` — спільної для десктопа й Android.
 *
 * Розбір навмисно на `org.json` зі складу Android: це нуль зайвих залежностей
 * для файлу з десятка рядків.
 */
class AssetSampleRepository(context: Context) : SampleRepository {

    private val assets = context.applicationContext.assets

    override suspend fun list(): List<TextSample> {
        val raw = readAsset("$SAMPLES_DIR/$INDEX_FILE") ?: return emptyList()

        return try {
            val array = JSONObject(raw).getJSONArray("samples")

            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)

                TextSample(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    file = item.getString("file"),
                    grade = item.optInt("grade", 0),
                    level = item.optString("level", ""),
                    words = item.optInt("words", 0)
                )
            }
        } catch (e: org.json.JSONException) {
            // Битий реєстр не має вбивати додаток: список просто буде порожній,
            // а вставка з буфера й ручний ввід працюють як раніше.
            emptyList()
        }
    }

    override suspend fun load(sample: TextSample): String =
        readAsset("$SAMPLES_DIR/${sample.file}").orEmpty()

    private fun readAsset(path: String): String? = try {
        assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val SAMPLES_DIR = "samples"
        const val INDEX_FILE = "index.json"
    }
}

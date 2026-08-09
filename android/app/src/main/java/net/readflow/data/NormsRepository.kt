package net.readflow.data

import android.content.Context
import net.readflow.core.GradeNorms
import net.readflow.core.NormLabels
import net.readflow.core.NormsCatalog
import net.readflow.core.ReadingNorm
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Джерело норм техніки читання.
 *
 * Інтерфейс, як і в зразків, потрібен тестам ViewModel: вони мають лишатися
 * звичайними JVM-тестами, без Android.
 */
interface NormsRepository {

    /** Довідник норм. [NormsCatalog.Empty] — файлу немає або він битий. */
    suspend fun load(): NormsCatalog
}

/**
 * Читає `assets/norms.json`, куди файл кладе Gradle-таск `syncSharedAssets`
 * із `shared/norms.json` — спільного з десктопом.
 *
 * Зовнішнього файлу поруч із застосунком, як на десктопі, тут немає: на Android
 * «поруч» нічого не лежить, а APK перезбирається однією командою.
 */
class AssetNormsRepository(context: Context) : NormsRepository {

    private val assets = context.applicationContext.assets

    override suspend fun load(): NormsCatalog {
        val raw = try {
            assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            return NormsCatalog.Empty
        }

        return NormsParser.parse(raw)
    }

    private companion object {
        const val FILE_NAME = "norms.json"
    }
}

/**
 * Розбір `norms.json`.
 *
 * **Довідник не має права нікого валити** (`SPEC.md`, 4.9): відсутній,
 * пошкоджений або новішої версії файл означає лише те, що оцінка не
 * показується. Окрема норма з переверненими межами пропускається, решта
 * класів лишається.
 */
object NormsParser {

    fun parse(json: String?): NormsCatalog {
        if (json.isNullOrBlank()) {
            return NormsCatalog.Empty
        }

        return try {
            val root = JSONObject(json)
            val version = root.optInt("version", 0)

            // Довідник новішої версії не читаємо: краще лишитися без оцінки,
            // ніж показати число, зрозуміле нам не так, як його задумали.
            if (version > NormsCatalog.SUPPORTED_VERSION) {
                return NormsCatalog.Empty
            }

            val gradesJson = root.optJSONArray("grades") ?: return NormsCatalog.Empty
            val grades = ArrayList<GradeNorms>(gradesJson.length())

            for (i in 0 until gradesJson.length()) {
                val item = gradesJson.optJSONObject(i) ?: continue
                val grade = item.optInt("grade", 0)

                if (grade <= 0) {
                    continue
                }

                val semestersJson = item.optJSONArray("semesters")
                val semesters = ArrayList<ReadingNorm>()

                for (j in 0 until (semestersJson?.length() ?: 0)) {
                    val norm = semestersJson?.optJSONObject(j) ?: continue
                    val semester = norm.optInt("semester", 0)
                    val min = norm.optInt("min", -1)
                    val max = norm.optInt("max", -1)

                    // Перевернуті або відʼємні межі — пропускаємо саме цю норму.
                    if (semester <= 0 || min < 0 || max < min) {
                        continue
                    }

                    semesters.add(ReadingNorm(grade, semester, min, max))
                }

                if (semesters.isEmpty()) {
                    continue
                }

                grades.add(
                    GradeNorms(
                        grade = grade,
                        label = item.optString("label", "$grade"),
                        semesters = semesters
                    )
                )
            }

            if (grades.isEmpty()) {
                return NormsCatalog.Empty
            }

            val evaluation = root.optJSONObject("evaluation")

            NormsCatalog(
                version = version,
                grades = grades.sortedBy { it.grade },
                labels = NormLabels.of(
                    below = evaluation?.optString("below"),
                    within = evaluation?.optString("within"),
                    above = evaluation?.optString("above")
                ),
                source = root.optString("source", ""),
                note = root.optString("note", "")
            )
        } catch (e: JSONException) {
            NormsCatalog.Empty
        }
    }
}

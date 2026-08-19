package net.readflow.ui

import net.readflow.core.MeasurementMode
import net.readflow.core.MeasurementResult
import net.readflow.core.NormEvaluation
import net.readflow.core.NormsCatalog
import net.readflow.model.Attempt
import net.readflow.model.Settings
import net.readflow.model.TextSample
import net.readflow.model.TextStats
import net.readflow.model.WordToken

/**
 * Стан головного екрана — один незмінний знімок.
 *
 * Усе, що бачить користувач, приходить звідси й нізвідки більше: так стан
 * переживає поворот екрана й не розповзається по компонентах. Кожне поле має
 * значення за замовчуванням, щоб `UiState()` завжди був валідним.
 */
data class UiState(

    /** Текст, який ввів або вставив учитель. Порожній — стан «нічого не введено». */
    val text: String = "",

    /** Статистика тексту. Оновлюється з дебаунсом, тому відстає від [text] на мить. */
    val stats: TextStats = TextStats.Empty,

    /** Чи розгорнутий рядок статистики в повний список. */
    val isStatsExpanded: Boolean = false,

    /** Доступні тексти-зразки з `shared/samples/`. */
    val samples: List<TextSample> = emptyList(),

    /** Чи показаний нижній аркуш вибору зразка. */
    val isSampleSheetVisible: Boolean = false,

    /** Чи показане підтвердження очищення. У стані, а не в композиції, — щоб переживало поворот. */
    val isClearConfirmVisible: Boolean = false,

    /**
     * Текст, до якого належать [stats] і [words].
     *
     * Це не те саме, що [text]: поле вводу оновлюється миттєво, а розбір —
     * з дебаунсом. Режим читання малюється саме звідси, бо [words] тримають
     * індекси символів, і на «свіжішому» рядку вони показували б не ті слова.
     */
    val countedText: String = "",

    /** Слова з їхніми межами — для рендеру й нумерації в режимі читання. */
    val words: List<WordToken> = emptyList(),

    /** Режим читання замість поля вводу. */
    val isReadingMode: Boolean = false,

    /** Обраний режим заміру (сегментований перемикач угорі). */
    val mode: MeasurementMode = MeasurementMode.TIMER,

    /** Обрана тривалість заміру, с. */
    val durationSeconds: Int = DEFAULT_DURATION_SECONDS,

    /** Замір іде просто зараз. */
    val isTimerRunning: Boolean = false,

    /** Час на секундомірі, мс. */
    val elapsedMillis: Long = 0L,

    /**
     * Номер слова-межі (від 1) або `null`. Межа означає, докуди учень дочитав:
     * слова після неї в підрахунок не йдуть.
     */
    val boundaryWordNumber: Int? = null,

    /**
     * Номери слів, позначених як помилка.
     *
     * Позначки за межею читання лишаються, хоч і не рахуються: перенесли межу —
     * і вони знову в грі (`SPEC.md`, 4.7).
     */
    val errorWordNumbers: Set<Int> = emptySet(),

    /** Підсумок заміру або `null`, якщо його ще немає. */
    val result: MeasurementResult? = null,

    /** Оцінка результату відносно норми обраного класу й семестру. */
    val evaluation: NormEvaluation = NormEvaluation.UNKNOWN,

    /** Імʼя учня (необовʼязкове) — вводиться перед заміром, іде в історію. */
    val studentName: String = "",

    /** Чи показаний нижній аркуш підсумку заміру. */
    val isResultSheetVisible: Boolean = false,

    /** Збережена історія замірів, новіші згори. */
    val history: List<Attempt> = emptyList(),

    /** Чи показаний нижній аркуш історії. */
    val isHistorySheetVisible: Boolean = false,

    /** Налаштування вчителя (тема, кегль, клас…). */
    val settings: Settings = Settings(),

    /** Чи показаний нижній аркуш налаштувань. */
    val isSettingsSheetVisible: Boolean = false,

    /** Довідник норм із `shared/norms.json`. Порожній — оцінка не показується. */
    val norms: NormsCatalog = NormsCatalog.Empty
) {
    /** Порожній екран: підказка й дві великі кнопки замість статистики. */
    val isEmpty: Boolean get() = text.isEmpty()

    /** Замір можна почати, лише коли є що читати. */
    val canMeasure: Boolean get() = words.isNotEmpty()

    /** Історію можна експортувати, лише коли в ній щось є. */
    val hasHistory: Boolean get() = history.isNotEmpty()

    /** Підпис оцінки з довідника; порожній рядок, якщо оцінки немає. */
    val evaluationLabel: String get() = norms.describe(evaluation)

    companion object {
        /** Тривалість заміру за замовчуванням, с. */
        const val DEFAULT_DURATION_SECONDS = 60

        /** Чіпи вибору тривалості (`SPEC_ANDROID.md`, 2.1). */
        val DURATION_CHOICES = listOf(30, 60, 120)
    }
}

package net.readflow.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.readflow.core.CountingOptions
import net.readflow.core.MeasurementCalculator
import net.readflow.core.MeasurementInput
import net.readflow.core.MeasurementMode
import net.readflow.core.MonotonicClock
import net.readflow.core.NormEvaluation
import net.readflow.core.TextStatsCalculator
import net.readflow.data.AssetNormsRepository
import net.readflow.data.AssetSampleRepository
import net.readflow.data.DataStoreSettingsRepository
import net.readflow.data.NormsRepository
import net.readflow.data.SampleRepository
import net.readflow.data.SettingsRepository
import net.readflow.model.Settings
import net.readflow.model.TextSample
import net.readflow.model.TextStats
import net.readflow.model.ThemeChoice
import net.readflow.model.WordToken

/**
 * Єдина ViewModel застосунку.
 *
 * Тримає весь стан екрана в одному [StateFlow]. Залежності передаються ззовні,
 * щоб тести обходилися без Android: репозиторії підмінюються фейками,
 * [computeDispatcher] — тестовим диспетчером, а [clock] — віртуальним часом
 * корутинного планувальника.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val samples: SampleRepository,
    private val settingsStore: SettingsRepository,
    private val normsStore: NormsRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: MonotonicClock = MonotonicClock.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())

    /** Стан для екрана. Змінюється лише через методи нижче. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Минула обрана тривалість заміру. Подія, а не поле стану: сигнал лунає
     * один раз, а поле довелося б потім скидати руками — і воно спрацювало б
     * удруге після повороту екрана.
     */
    private val _durationReached = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val durationReached: SharedFlow<Unit> = _durationReached.asSharedFlow()

    /** Окремий потік тексту: сам текст оновлюється миттєво, а статистика — з дебаунсом. */
    private val textInput = MutableStateFlow("")

    private var timerJob: Job? = null
    private var startedAt = 0L
    private var durationSignalled = false

    init {
        viewModelScope.launch {
            textInput
                .debounce(STATS_DEBOUNCE_MS)
                .mapLatest { text ->
                    // Підрахунок на 3000 слів не має чіплятися до головного потоку.
                    withContext(computeDispatcher) {
                        // Розбір на слова робиться один раз: він однаково потрібен
                        // і статистиці, і режиму читання.
                        val words = TextStatsCalculator.getWords(text)

                        Counted(
                            text = text,
                            words = words,
                            stats = TextStatsCalculator.calculate(
                                text,
                                words,
                                CountingOptions.Default
                            )
                        )
                    }
                }
                .collect { counted ->
                    _uiState.update {
                        it.copy(
                            countedText = counted.text,
                            words = counted.words,
                            stats = counted.stats
                        ).recalculated()
                    }
                }
        }

        viewModelScope.launch {
            val loaded = samples.list()
            _uiState.update { it.copy(samples = loaded) }
        }

        viewModelScope.launch {
            val catalog = normsStore.load()
            _uiState.update { it.copy(norms = catalog).recalculated() }
        }

        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        settings = settings,
                        // Тривалість живе у двох місцях: у налаштуваннях (де її
                        // зберігають) і в стані заміру (де її показують чіпи).
                        // Джерело правди — налаштування; чіпи пишуть саме туди.
                        durationSeconds = settings.durationSeconds
                    ).recalculated()
                }
            }
        }
    }

    // --- Текст ---

    /** Користувач змінив текст у полі вводу або вставив його з буфера. */
    fun onTextChange(text: String) {
        stopTimer()

        _uiState.update { current ->
            current.copy(
                text = text,
                // Новий текст — новий замір: старі межа, помилки й час
                // указували б на слова, яких уже немає.
                boundaryWordNumber = null,
                errorWordNumbers = emptySet(),
                elapsedMillis = 0L,
                isTimerRunning = false,
                result = null,
                evaluation = NormEvaluation.UNKNOWN,
                // Порожній текст нічого читати: режим читання сам вимикається,
                // інакше екран лишився б із порожньою зоною й без поля вводу.
                isReadingMode = current.isReadingMode && text.isNotEmpty()
            )
        }

        textInput.value = text
    }

    /** Перемикач «Читання» над текстом. На порожньому тексті нічого не робить. */
    fun toggleReadingMode() {
        _uiState.update { current ->
            if (current.isEmpty) current else current.copy(isReadingMode = !current.isReadingMode)
        }
    }

    /** Тап по «Очистити»: спершу питаємо, бо палець ширший за кнопку. */
    fun requestClear() {
        _uiState.update { current ->
            if (current.isEmpty) current else current.copy(isClearConfirmVisible = true)
        }
    }

    /** «Скасувати» в підтвердженні. */
    fun cancelClear() {
        _uiState.update { current -> current.copy(isClearConfirmVisible = false) }
    }

    /** Підтверджене очищення. */
    fun clearText() {
        _uiState.update { current -> current.copy(isClearConfirmVisible = false) }
        onTextChange("")
    }

    /** Тап по рядку статистики розгортає й згортає його. */
    fun toggleStatsExpanded() {
        _uiState.update { current -> current.copy(isStatsExpanded = !current.isStatsExpanded) }
    }

    // --- Зразки ---

    /** Кнопка «Обрати зразок». */
    fun showSampleSheet() {
        _uiState.update { current -> current.copy(isSampleSheetVisible = true) }
    }

    fun hideSampleSheet() {
        _uiState.update { current -> current.copy(isSampleSheetVisible = false) }
    }

    /** Учитель обрав зразок зі списку: вантажимо текст і закриваємо аркуш. */
    fun onSampleSelected(sample: TextSample) {
        viewModelScope.launch {
            val text = samples.load(sample)

            hideSampleSheet()

            if (text.isNotEmpty()) {
                onTextChange(text)
            }
        }
    }

    // --- Режими й тапи по словах ---

    /**
     * Перемикач режиму A / B / C.
     *
     * Вихід із режиму «Помилки» **очищає** позначки, а не ховає їх
     * (`SPEC.md`, 4.7): прихований стан, який мовчки повертається при
     * повторному вмиканні, — саме те, через що потім не сходяться числа.
     */
    fun onModeChange(mode: MeasurementMode) {
        _uiState.update { current ->
            if (current.mode == mode) {
                current
            } else {
                current.copy(
                    mode = mode,
                    errorWordNumbers = if (mode.marksErrors) current.errorWordNumbers else emptySet()
                ).recalculated()
            }
        }
    }

    /**
     * Короткий тап по слову: помилка в режимі C, межа читання в A і B
     * (`SPEC.md`, 4.7, таблиця розведення кліків).
     */
    fun onWordTap(number: Int) {
        val mode = _uiState.value.mode

        if (mode.marksErrors) {
            toggleError(number)
        } else {
            toggleBoundary(number)
        }
    }

    /**
     * Довгий тап: завжди межа читання — там, де на десктопі правий клік.
     * Підказку «Слово №N» показує сам режим читання, незалежно від цього.
     */
    fun onWordLongPress(number: Int) {
        toggleBoundary(number)
    }

    /** Повторний тап по слову-межі знімає межу; тап по іншому — переносить. */
    private fun toggleBoundary(number: Int) {
        // У режимі A межа заміняє собою Стоп (`SPEC.md`, 4.8).
        if (_uiState.value.mode.usesFixedDuration) {
            stopTimer()
        }

        _uiState.update { current ->
            current.copy(
                boundaryWordNumber = if (current.boundaryWordNumber == number) null else number,
                isTimerRunning = if (current.mode.usesFixedDuration) false else current.isTimerRunning
            ).recalculated()
        }
    }

    /** Повторний тап по позначеному слову знімає позначку. */
    private fun toggleError(number: Int) {
        _uiState.update { current ->
            val updated = current.errorWordNumbers.toMutableSet()

            if (!updated.remove(number)) {
                updated.add(number)
            }

            current.copy(errorWordNumbers = updated).recalculated()
        }
    }

    // --- Таймер ---

    /** Велика кнопка внизу. */
    fun toggleMeasurement() {
        if (_uiState.value.isTimerRunning) stopMeasurement() else startMeasurement()
    }

    /**
     * Старт заміру. Завжди починає **новий** замір: попередні час, межа й
     * помилки стосувалися минулої спроби, і мовчки продовжувати їх — найкоротший
     * шлях до числа, якого вчитель не очікує.
     */
    fun startMeasurement() {
        val state = _uiState.value

        if (state.isTimerRunning || !state.canMeasure) {
            return
        }

        startedAt = clock.millis()
        durationSignalled = false

        _uiState.update { current ->
            current.copy(
                isTimerRunning = true,
                elapsedMillis = 0L,
                boundaryWordNumber = null,
                errorWordNumbers = emptySet(),
                result = null,
                evaluation = NormEvaluation.UNKNOWN
            )
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                onTick()
            }
        }
    }

    /** Стоп: час фіксується, підсумок рахується. */
    fun stopMeasurement() {
        if (!_uiState.value.isTimerRunning) {
            return
        }

        val elapsed = clock.millis() - startedAt
        stopTimer()

        _uiState.update { current ->
            current.copy(isTimerRunning = false, elapsedMillis = elapsed).recalculated()
        }
    }

    /** Чіп тривалості. Пишеться в налаштування, щоб пережити перезапуск. */
    fun onDurationChange(seconds: Int) {
        _uiState.update { current -> current.copy(durationSeconds = seconds).recalculated() }
        viewModelScope.launch { settingsStore.update { it.copy(durationSeconds = seconds) } }
    }

    private fun onTick() {
        val elapsed = clock.millis() - startedAt
        val state = _uiState.value

        _uiState.update { current -> current.copy(elapsedMillis = elapsed) }

        val duration = state.durationSeconds * 1000L

        if (durationSignalled || duration <= 0L || elapsed < duration) {
            return
        }

        // Задана тривалість — позначка, а не межа (`SPEC.md`, 4.8): сигнал лунає
        // один раз, а відлік триває, доки вчитель не натисне Стоп.
        durationSignalled = true
        _durationReached.tryEmit(Unit)
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // --- Налаштування ---

    fun showSettingsSheet() {
        _uiState.update { current -> current.copy(isSettingsSheetVisible = true) }
    }

    fun hideSettingsSheet() {
        _uiState.update { current -> current.copy(isSettingsSheetVisible = false) }
    }

    fun onFontSizeChange(sp: Int) = updateSettings { it.copy(fontSizeSp = sp) }

    fun onLineSpacingChange(spacing: Float) = updateSettings { it.copy(lineSpacing = spacing) }

    fun onThemeChange(theme: ThemeChoice) = updateSettings { it.copy(theme = theme) }

    fun onGradeChange(grade: Int) = updateSettings { it.copy(grade = grade) }

    fun onSemesterChange(semester: Int) = updateSettings { it.copy(semester = semester) }

    private fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    override fun onCleared() {
        stopTimer()
        super.onCleared()
    }

    /**
     * Перерахувати підсумок і оцінку.
     *
     * Робиться в стані, а не в композиції: інакше кожен кадр рахував би те
     * саме, а тест не міг би перевірити число без екрана.
     */
    private fun UiState.recalculated(): UiState {
        val boundaryChars = boundaryWordNumber
            ?.let { number -> words.getOrNull(number - 1) }
            ?.let { word -> TextStatsCalculator.countCharsNoSpaces(countedText, word.end) }
            ?: 0

        val result = MeasurementCalculator.evaluate(
            MeasurementInput(
                mode = mode,
                durationSeconds = durationSeconds,
                elapsedMillis = elapsedMillis,
                isRunning = isTimerRunning,
                totalWords = stats.wordCount,
                totalCharsNoSpaces = stats.charCountNoSpaces,
                boundaryWordNumber = boundaryWordNumber,
                boundaryCharsNoSpaces = boundaryChars,
                errorWordNumbers = errorWordNumbers
            )
        )

        return copy(
            result = result,
            evaluation = result
                ?.let { norms.evaluate(it.wordsPerMinute, settings.grade, settings.semester) }
                ?: NormEvaluation.UNKNOWN
        )
    }

    /** Результат одного проходу розбору: текст і все, що з нього порахували. */
    private data class Counted(
        val text: String,
        val words: List<WordToken>,
        val stats: TextStats
    )

    companion object {

        /** Дебаунс перерахунку статистики, мс (`SPEC_ANDROID.md`, розділ 5). */
        const val STATS_DEBOUNCE_MS = 300L

        /**
         * Крок оновлення лічильника, мс. 100 мс — секунди змінюються без
         * помітного запізнення, а навантаження лишається непомітним.
         * На точність часу це не впливає: час завжди береться з годинника.
         */
        const val TICK_MS = 100L

        /** Фабрика для екрана: репозиторії потребують `Context`. */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext

            return viewModelFactory {
                initializer {
                    MainViewModel(
                        samples = AssetSampleRepository(appContext),
                        settingsStore = DataStoreSettingsRepository(appContext),
                        normsStore = AssetNormsRepository(appContext)
                    )
                }
            }
        }
    }
}

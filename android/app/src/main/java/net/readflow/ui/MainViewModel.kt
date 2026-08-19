package net.readflow.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
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
import net.readflow.data.HistoryRepository
import net.readflow.data.NormsRepository
import net.readflow.data.RoomHistoryRepository
import net.readflow.data.SampleRepository
import net.readflow.data.SettingsRepository
import net.readflow.model.Attempt
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
    private val historyStore: HistoryRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: MonotonicClock = MonotonicClock.Default,
    /**
     * Стінний час для мітки запису історії — epoch-мілісекунди.
     *
     * Окремо від [clock]: той монотонний (аптайм) і в дату не годиться, а тут
     * потрібен саме календарний час. У тестах підмінюється фіксованим.
     */
    private val now: () -> Long = { System.currentTimeMillis() },
    /**
     * Збереження стану поза життям процесу (`SPEC_ANDROID.md`, розділ 5).
     *
     * Поворот екрана переживає сама ViewModel (її утримує фреймворк), а от
     * убивство процесу в фоні — ні: тоді відновлення йде звідси. За
     * замовчуванням порожній handle — щоб тести, яким збереження байдуже,
     * не мусили його передавати.
     */
    private val savedState: SavedStateHandle = SavedStateHandle()
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

    /**
     * Результат записано в історію. Подія, а не поле стану: підтвердження
     * зʼявляється один раз, а полем воно продзвеніло б удруге після повороту.
     */
    private val _resultSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resultSaved: SharedFlow<Unit> = _resultSaved.asSharedFlow()

    /** Окремий потік тексту: сам текст оновлюється миттєво, а статистика — з дебаунсом. */
    private val textInput = MutableStateFlow("")

    private var timerJob: Job? = null
    private var startedAt = 0L
    private var durationSignalled = false

    init {
        restoreState()

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
            historyStore.history.collect { records ->
                _uiState.update { it.copy(history = records) }
            }
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
                // Підсумок стосувався минулого тексту — ховаємо аркуш.
                isResultSheetVisible = false,
                // Порожній текст нічого читати: режим читання сам вимикається,
                // інакше екран лишився б із порожньою зоною й без поля вводу.
                isReadingMode = current.isReadingMode && text.isNotEmpty()
            )
        }

        textInput.value = text
        persist()
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
        persist()
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
            ).recalculated().let { updated ->
                // У режимі A межа заміняє собою Стоп, тож і аркуш підсумку
                // показуємо тут — коли межа є. Знята межа ховає аркуш.
                if (updated.mode.usesFixedDuration) {
                    updated.copy(isResultSheetVisible = updated.result != null)
                } else {
                    updated
                }
            }
        }
        persist()
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
        persist()
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
                evaluation = NormEvaluation.UNKNOWN,
                isResultSheetVisible = false
            )
        }

        // Старт скидає межу, помилки й час — зберігаємо цей скинутий стан,
        // щоб відновлення не повернуло дані минулої спроби.
        persist()

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
            current.copy(isTimerRunning = false, elapsedMillis = elapsed)
                .recalculated()
                // Після Стоп показуємо аркуш підсумку (`SPEC_ANDROID.md`, 2.1),
                // але лише коли підсумок справді є.
                .let { it.copy(isResultSheetVisible = it.result != null) }
        }
        persist()
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

    // --- Підсумок, учень і історія ---

    /** Імʼя учня перед заміром: іде в запис історії. Порожнє — теж дозволено. */
    fun onStudentNameChange(name: String) {
        _uiState.update { current -> current.copy(studentName = name) }
        persist()
    }

    /** Знову відкрити аркуш підсумку (тап по рядку результату). */
    fun showResultSheet() {
        _uiState.update { current ->
            if (current.result != null) current.copy(isResultSheetVisible = true) else current
        }
    }

    /** Закрити аркуш підсумку, лишивши сам результат на екрані. */
    fun hideResultSheet() {
        _uiState.update { current -> current.copy(isResultSheetVisible = false) }
    }

    /**
     * «Ще раз»: закрити аркуш і скинути замір під новий прохід того самого
     * тексту. Текст і імʼя учня лишаються — це той самий учень і той самий
     * текст, змінюється лише спроба.
     */
    fun measureAgain() {
        stopTimer()
        _uiState.update { current ->
            current.copy(
                isResultSheetVisible = false,
                isTimerRunning = false,
                elapsedMillis = 0L,
                boundaryWordNumber = null,
                errorWordNumbers = emptySet(),
                result = null,
                evaluation = NormEvaluation.UNKNOWN
            )
        }
        persist()
    }

    /**
     * Записати поточний підсумок в історію.
     *
     * Без результату не робить нічого: кнопка «Зберегти» й так активна лише за
     * наявного підсумку, але подвійний тап не має створювати другий запис ані
     * порожній.
     */
    fun saveResultToHistory() {
        val state = _uiState.value
        val result = state.result ?: return

        val attempt = Attempt(
            studentName = state.studentName.trim(),
            createdAt = now(),
            grade = state.settings.grade,
            wordsPerMinute = result.wordsPerMinute,
            charsPerMinute = result.charsPerMinute,
            errors = result.errors,
            errorPercent = result.errorPercent
        )

        viewModelScope.launch {
            historyStore.save(attempt)
            _resultSaved.tryEmit(Unit)
        }
    }

    /** Кнопка «Історія». */
    fun showHistorySheet() {
        _uiState.update { current -> current.copy(isHistorySheetVisible = true) }
    }

    fun hideHistorySheet() {
        _uiState.update { current -> current.copy(isHistorySheetVisible = false) }
    }

    /** Свайп у списку історії. */
    fun deleteAttempt(id: Long) {
        viewModelScope.launch { historyStore.delete(id) }
    }

    // --- Збереження стану поза життям процесу ---

    /**
     * Відновити стан після вбивства процесу.
     *
     * Зберігаємо й повертаємо саме **входи** заміру (текст, режим, межа,
     * помилки, час, імʼя), а не готовий підсумок: щойно текст перерахується,
     * `recalculated()` відтворить ті самі числа за тими самими правилами —
     * одне джерело правди замість двох, що неминуче розійшлися б.
     *
     * Замір, який тривав у мить убивства, відновлюється **зупиненим**: годинник
     * монотонний і разом із процесом обнулився, тож чесно продовжити відлік
     * нема від чого.
     */
    private fun restoreState() {
        val text = savedState.get<String>(KEY_TEXT) ?: return

        val modeName = savedState.get<String>(KEY_MODE)
        val mode = MeasurementMode.entries.firstOrNull { it.name == modeName }
            ?: MeasurementMode.TIMER
        val boundary = savedState.get<Int>(KEY_BOUNDARY)?.takeIf { it > 0 }
        val errors = savedState.get<IntArray>(KEY_ERRORS)?.toSet() ?: emptySet()
        val elapsed = savedState.get<Long>(KEY_ELAPSED) ?: 0L
        val studentName = savedState.get<String>(KEY_NAME).orEmpty()

        _uiState.update {
            it.copy(
                text = text,
                mode = mode,
                boundaryWordNumber = boundary,
                errorWordNumbers = errors,
                elapsedMillis = elapsed,
                isTimerRunning = false,
                studentName = studentName
            )
        }

        // Запускаємо розбір відновленого тексту — статистика, слова й підсумок
        // відтворяться, щойно він завершиться.
        textInput.value = text
    }

    /** Записати входи заміру в handle — щоб пережили вбивство процесу. */
    private fun persist() {
        val state = _uiState.value

        savedState[KEY_TEXT] = state.text
        savedState[KEY_MODE] = state.mode.name
        savedState[KEY_BOUNDARY] = state.boundaryWordNumber ?: -1
        savedState[KEY_ERRORS] = state.errorWordNumbers.toIntArray()
        savedState[KEY_ELAPSED] = state.elapsedMillis
        savedState[KEY_NAME] = state.studentName
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

        // Ключі збереження стану (SavedStateHandle).
        private const val KEY_TEXT = "ss_text"
        private const val KEY_MODE = "ss_mode"
        private const val KEY_BOUNDARY = "ss_boundary"
        private const val KEY_ERRORS = "ss_errors"
        private const val KEY_ELAPSED = "ss_elapsed"
        private const val KEY_NAME = "ss_student"

        /** Фабрика для екрана: репозиторії потребують `Context`. */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext

            return viewModelFactory {
                initializer {
                    MainViewModel(
                        samples = AssetSampleRepository(appContext),
                        settingsStore = DataStoreSettingsRepository(appContext),
                        normsStore = AssetNormsRepository(appContext),
                        historyStore = RoomHistoryRepository.create(appContext),
                        savedState = createSavedStateHandle()
                    )
                }
            }
        }
    }
}

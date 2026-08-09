package net.readflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.readflow.core.MeasurementMode
import net.readflow.core.MonotonicClock
import net.readflow.core.NormEvaluation
import net.readflow.core.GradeNorms
import net.readflow.core.NormLabels
import net.readflow.core.NormsCatalog
import net.readflow.core.ReadingNorm
import net.readflow.data.InMemorySettingsRepository
import net.readflow.data.NormsRepository
import net.readflow.data.SampleRepository
import net.readflow.data.SettingsRepository
import net.readflow.model.Settings
import net.readflow.model.TextSample
import net.readflow.model.ThemeChoice
import net.readflow.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel не залежить від Android: репозиторій підмінено фейком, а обидва
 * диспетчери — тестовим із віртуальним часом. Тому це звичайні JVM-тести.
 *
 * Назви латиницею навмисно: Kotlin робить із них імена class-файлів, а кирилиця
 * в назві файлу ламає збірку там, де кодування шляхів не UTF-8.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val sampleOne = TextSample(
        id = "sample-01",
        title = "Приклад: короткий текст",
        file = "sample-01.txt",
        grade = 1,
        level = "легкий",
        words = 15
    )

    private class FakeSamples(
        private val items: List<TextSample> = emptyList(),
        private val texts: Map<String, String> = emptyMap()
    ) : SampleRepository {
        override suspend fun list(): List<TextSample> = items
        override suspend fun load(sample: TextSample): String = texts[sample.id].orEmpty()
    }

    /**
     * Довідник норм у тестах збирається руками, а не читається з файлу:
     * розбір JSON перевіряється окремо (`NormsRepositoryTest`), а тут важлива
     * лише сама оцінка.
     */
    private class FakeNorms(private val catalog: NormsCatalog = NormsCatalog.Empty) :
        NormsRepository {
        override suspend fun load(): NormsCatalog = catalog
    }

    private val settingsStore = InMemorySettingsRepository()

    /**
     * Годинник заміру йде **віртуальним часом планувальника**. Тому тест на
     * дві хвилини виконується миттєво й не флейкає: `advanceTimeBy(120_000)`
     * рухає і корутини, і годинник однаково.
     */
    private val clock = MonotonicClock { dispatcher.scheduler.currentTime }

    private fun viewModel(
        samples: SampleRepository = FakeSamples(),
        settings: SettingsRepository = settingsStore,
        norms: NormsRepository = FakeNorms()
    ) = MainViewModel(samples, settings, norms, dispatcher, clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Початковий стан порожній. */
    @Test
    fun `initial state is empty`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        assertEquals("", vm.uiState.value.text)
        assertEquals(0, vm.uiState.value.stats.wordCount)
        assertTrue(vm.uiState.value.isEmpty)
    }

    /** Текст у стані оновлюється миттєво, без очікування дебаунсу. */
    @Test
    fun `text reaches the state immediately`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onTextChange("комп'ютер")

        assertEquals("комп'ютер", vm.uiState.value.text)
    }

    /**
     * Статистика чекає на паузу в 300 мс. Це не «приблизно швидко»: тест зупиняє
     * віртуальний час за мілісекунду до порога й переконується, що чисел ще немає.
     */
    @Test
    fun `stats appear only after the debounce`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onTextChange("комп'ютер синьо-жовтий 2024")

        advanceTimeBy(MainViewModel.STATS_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals("До паузи статистика не рахується.", 0, vm.uiState.value.stats.wordCount)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(3, vm.uiState.value.stats.wordCount)
        assertEquals(23, vm.uiState.value.stats.letterCount)
    }

    /** Швидкий набір не дає проміжних перерахунків: рахується лише останній текст. */
    @Test
    fun `fast typing recalculates only the final text`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onTextChange("а")
        advanceTimeBy(100)
        vm.onTextChange("а б")
        advanceTimeBy(100)
        vm.onTextChange("а б в")
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.stats.wordCount)
    }

    /** «Очистити» скидає і текст, і статистику. */
    @Test
    fun `clear resets text and stats`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onTextChange("мама мила раму")
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.stats.wordCount)

        vm.clearText()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.text)
        assertEquals(0, vm.uiState.value.stats.wordCount)
        assertTrue(vm.uiState.value.isEmpty)
    }

    /** Тап по «Очистити» спершу лише питає — текст на місці. */
    @Test
    fun `clear asks before wiping the text`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        advanceUntilIdle()

        vm.requestClear()

        assertTrue(vm.uiState.value.isClearConfirmVisible)
        assertEquals("мама мила раму", vm.uiState.value.text)
        assertEquals(3, vm.uiState.value.stats.wordCount)
    }

    /** «Скасувати» закриває підтвердження й нічого не чіпає. */
    @Test
    fun `cancelling the confirmation keeps the text`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        advanceUntilIdle()
        vm.requestClear()

        vm.cancelClear()

        assertFalse(vm.uiState.value.isClearConfirmVisible)
        assertEquals("мама мила раму", vm.uiState.value.text)
    }

    /** Підтверджене очищення закриває діалог і скидає текст зі статистикою. */
    @Test
    fun `confirmed clear wipes text and closes the dialog`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        advanceUntilIdle()
        vm.requestClear()

        vm.clearText()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isClearConfirmVisible)
        assertEquals("", vm.uiState.value.text)
        assertEquals(0, vm.uiState.value.stats.wordCount)
    }

    /** На порожньому екрані питати нема про що. */
    @Test
    fun `clear does not ask when there is nothing to clear`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.requestClear()

        assertFalse(vm.uiState.value.isClearConfirmVisible)
    }

    /** Список зразків підтягується під час створення екрана. */
    @Test
    fun `samples are loaded on start`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel(FakeSamples(items = listOf(sampleOne)))

        advanceUntilIdle()

        assertEquals(listOf(sampleOne), vm.uiState.value.samples)
    }

    /** Вибір зразка вставляє його текст, рахує статистику й закриває аркуш. */
    @Test
    fun `choosing a sample loads its text and closes the sheet`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel(
            FakeSamples(
                items = listOf(sampleOne),
                texts = mapOf("sample-01" to "Ліс прокинувся, пташки співали.")
            )
        )
        advanceUntilIdle()

        vm.showSampleSheet()
        assertTrue(vm.uiState.value.isSampleSheetVisible)

        vm.onSampleSelected(sampleOne)
        advanceUntilIdle()

        assertEquals("Ліс прокинувся, пташки співали.", vm.uiState.value.text)
        assertEquals(4, vm.uiState.value.stats.wordCount)
        assertFalse(vm.uiState.value.isSampleSheetVisible)
    }

    /** Порожній зразок не затирає вже введений текст. */
    @Test
    fun `empty sample does not wipe the current text`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel(FakeSamples(items = listOf(sampleOne)))
        vm.onTextChange("важливий текст")
        advanceUntilIdle()

        vm.onSampleSelected(sampleOne)
        advanceUntilIdle()

        assertEquals("важливий текст", vm.uiState.value.text)
    }

    /** Рядок статистики розгортається й згортається. */
    @Test
    fun `stats row toggles`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        assertFalse(vm.uiState.value.isStatsExpanded)
        vm.toggleStatsExpanded()
        assertTrue(vm.uiState.value.isStatsExpanded)
        vm.toggleStatsExpanded()
        assertFalse(vm.uiState.value.isStatsExpanded)
    }

    /** Слова з їхніми межами приходять разом зі статистикою, з того самого розбору. */
    @Test
    fun `words arrive together with the stats`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onTextChange("Мама мила раму.")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.words.size)
        assertEquals(state.stats.wordCount, state.words.size)
        assertEquals("Мама", state.words[0].text)
        assertEquals(1, state.words[0].number)
        assertEquals(10, state.words[2].start)
    }

    /**
     * Текст, до якого належать слова, зберігається окремо. Поле вводу вже
     * змінилося, а межі слів ще від старого рядка — режим читання мусить
     * малювати саме той рядок, з якого ці межі порахували.
     */
    @Test
    fun `counted text lags behind the input and stays consistent with the words`() =
        runTest(dispatcher, timeout = TEST_TIMEOUT) {
            val vm = viewModel()

            vm.onTextChange("мама")
            advanceUntilIdle()
            assertEquals("мама", vm.uiState.value.countedText)

            vm.onTextChange("мама мила раму")
            assertEquals("мама мила раму", vm.uiState.value.text)
            assertEquals("Слова ще від попереднього тексту.", "мама", vm.uiState.value.countedText)
            assertEquals(1, vm.uiState.value.words.size)

            advanceUntilIdle()
            assertEquals("мама мила раму", vm.uiState.value.countedText)
            assertEquals(3, vm.uiState.value.words.size)
        }

    /** Перемикач «Читання» вмикає й вимикає режим. */
    @Test
    fun `reading mode toggles`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isReadingMode)
        vm.toggleReadingMode()
        assertTrue(vm.uiState.value.isReadingMode)
        vm.toggleReadingMode()
        assertFalse(vm.uiState.value.isReadingMode)
    }

    /** На порожньому тексті читати нічого — режим не вмикається. */
    @Test
    fun `reading mode does not turn on for empty text`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.toggleReadingMode()

        assertFalse(vm.uiState.value.isReadingMode)
    }

    /** «Очистити» в режимі читання повертає до поля вводу, а не лишає порожню зону. */
    @Test
    fun `clearing the text leaves the reading mode`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        vm.toggleReadingMode()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isReadingMode)

        vm.clearText()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isReadingMode)
    }

    /** Правка тексту режим читання не вимикає — лише його спорожнення. */
    @Test
    fun `editing the text keeps the reading mode`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange("мама")
        vm.toggleReadingMode()
        advanceUntilIdle()

        vm.onTextChange("мама мила")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isReadingMode)
    }

    // --- Задача 5: таймер і режим B ---

    /** Замір не починається на порожньому екрані: читати нема чого. */
    @Test
    fun `measurement does not start without text`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.startMeasurement()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isTimerRunning)
    }

    /** Лічильник іде монотонним годинником, а не накопиченням тіків. */
    @Test
    fun `timer counts the elapsed time`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        vm.startMeasurement()
        assertTrue(vm.uiState.value.isTimerRunning)

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(30_000L, vm.uiState.value.elapsedMillis)

        vm.stopMeasurement()
    }

    /** Доки замір іде, підсумку немає: проміжний WPM — це вигадка. */
    @Test
    fun `no result while running`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        vm.startMeasurement()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(null, vm.uiState.value.result)

        vm.stopMeasurement()
    }

    /** Режим B: WPM за фактичним часом. 10 слів за 30 с — це 20 слів/хв. */
    @Test
    fun `timer mode computes wpm from the actual time`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        vm.startMeasurement()
        advanceTimeBy(30_000)
        runCurrent()
        vm.stopMeasurement()
        advanceUntilIdle()

        val result = vm.uiState.value.result!!
        assertFalse(vm.uiState.value.isTimerRunning)
        assertEquals(10, result.wordsRead)
        assertEquals(20, result.wordsPerMinute)
        assertEquals(30, result.secondsRounded)
    }

    /**
     * Сигнал про кінець тривалості лунає **один раз**, а відлік триває далі
     * (`SPEC.md`, 4.8): задана тривалість — позначка, а не межа.
     */
    @Test
    fun `duration signal fires once and the timer keeps running`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        var signals = 0
        val watcher = launch { vm.durationReached.collect { signals++ } }
        runCurrent()

        vm.onDurationChange(30)
        vm.startMeasurement()

        advanceTimeBy(29_000)
        runCurrent()
        assertEquals("До кінця тривалості сигналу немає.", 0, signals)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, signals)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals("Сигнал лунає один раз на замір.", 1, signals)
        assertTrue("Відлік триває після сигналу.", vm.uiState.value.isTimerRunning)

        watcher.cancel()

        // Обовʼязково: незупинений таймер повісив би `runTest` назавжди.
        vm.stopMeasurement()
    }

    /** Старт після Стопу починає новий замір, а не продовжує старий. */
    @Test
    fun `start begins a new measurement`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        vm.startMeasurement()
        advanceTimeBy(30_000)
        runCurrent()
        vm.stopMeasurement()
        advanceUntilIdle()
        assertEquals(30_000L, vm.uiState.value.elapsedMillis)

        vm.startMeasurement()
        runCurrent()

        assertEquals(0L, vm.uiState.value.elapsedMillis)
        assertEquals(null, vm.uiState.value.result)

        vm.stopMeasurement()
    }

    /** Одна кнопка на Старт і Стоп. */
    @Test
    fun `toggle switches between start and stop`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        vm.toggleMeasurement()
        runCurrent()
        assertTrue(vm.uiState.value.isTimerRunning)

        advanceTimeBy(5_000)
        vm.toggleMeasurement()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isTimerRunning)
    }

    /** Новий текст скидає замір: старі час і межа стосувалися іншого тексту. */
    @Test
    fun `new text resets the measurement`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.startMeasurement()
        advanceTimeBy(10_000)
        runCurrent()

        vm.onTextChange("зовсім інший текст")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isTimerRunning)
        assertEquals(0L, vm.uiState.value.elapsedMillis)
        assertEquals(null, vm.uiState.value.result)

        vm.stopMeasurement()
    }

    // --- Задача 6: режими A і C ---

    /** Режим A: тап ставить межу й заміняє собою Стоп (`SPEC.md`, 4.8). */
    @Test
    fun `tap-stop mode sets the boundary and stops the timer`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.TAP_STOP)
        vm.onDurationChange(60)
        vm.startMeasurement()
        advanceTimeBy(7_000)
        runCurrent()

        vm.onWordTap(5)
        advanceUntilIdle()

        assertFalse("Межа заміняє Стоп.", vm.uiState.value.isTimerRunning)
        assertEquals(5, vm.uiState.value.boundaryWordNumber)

        // Час береться з обраної тривалості, а не з семи секунд секундоміра.
        assertEquals(5, vm.uiState.value.result!!.wordsPerMinute)
    }

    /** Повторний тап по слову-межі знімає межу, тап по іншому — переносить. */
    @Test
    fun `tapping the boundary again removes it`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.TAP_STOP)

        vm.onWordTap(5)
        assertEquals(5, vm.uiState.value.boundaryWordNumber)

        vm.onWordTap(7)
        assertEquals(7, vm.uiState.value.boundaryWordNumber)

        vm.onWordTap(7)
        assertEquals(null, vm.uiState.value.boundaryWordNumber)
    }

    /** Режим C: короткий тап позначає помилку, повторний — знімає. */
    @Test
    fun `errors mode toggles the mark`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.ERRORS)

        vm.onWordTap(3)
        assertEquals(setOf(3), vm.uiState.value.errorWordNumbers)

        vm.onWordTap(3)
        assertTrue(vm.uiState.value.errorWordNumbers.isEmpty())
    }

    /** У режимі C межу ставить довгий тап — там, де на десктопі правий клік. */
    @Test
    fun `long press sets the boundary in the errors mode`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.ERRORS)

        vm.onWordLongPress(6)

        assertEquals(6, vm.uiState.value.boundaryWordNumber)
        assertTrue("Довгий тап не позначає помилку.", vm.uiState.value.errorWordNumbers.isEmpty())
    }

    /**
     * Три режими справді поєднуються: межа з A обмежує зону помилок C,
     * а час приходить із секундоміра B.
     */
    @Test
    fun `boundary limits the errors and the timer gives the time`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.ERRORS)

        vm.startMeasurement()
        advanceTimeBy(30_000)
        runCurrent()
        vm.stopMeasurement()
        advanceUntilIdle()

        vm.onWordTap(2)
        vm.onWordTap(9)
        vm.onWordLongPress(5)
        advanceUntilIdle()

        val result = vm.uiState.value.result!!
        assertEquals("Прочитано до межі.", 5, result.wordsRead)
        assertEquals("Помилка за межею не рахується.", 1, result.errors)
        assertEquals("Час — фактичний, 30 с.", 30, result.secondsRounded)
        // 5 слів за 30 с — це 10 слів/хв; без однієї помилки — 8.
        assertEquals(10, result.wordsPerMinute)
        assertEquals(8, result.cleanWordsPerMinute)
    }

    /**
     * Вихід із режиму «Помилки» **очищає** позначки (`SPEC.md`, 4.7):
     * прихований стан, що мовчки повертається, — саме те, через що потім
     * не сходяться числа.
     */
    @Test
    fun `leaving the errors mode clears the marks`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.ERRORS)
        vm.onWordTap(3)
        assertEquals(setOf(3), vm.uiState.value.errorWordNumbers)

        vm.onModeChange(MeasurementMode.TIMER)
        vm.onModeChange(MeasurementMode.ERRORS)

        assertTrue("Позначки не мають повертатися.", vm.uiState.value.errorWordNumbers.isEmpty())
    }

    /** Межа при зміні режиму лишається: вона не належить жодному з них окремо. */
    @Test
    fun `boundary survives a mode change`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.onModeChange(MeasurementMode.TIMER)
        vm.onWordTap(4)

        vm.onModeChange(MeasurementMode.ERRORS)

        assertEquals(4, vm.uiState.value.boundaryWordNumber)
    }

    // --- Задача 7: налаштування й норми ---

    /** Тривалість із чіпа їде в налаштування, щоб пережити перезапуск. */
    @Test
    fun `duration is stored in the settings`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onDurationChange(120)
        advanceUntilIdle()

        assertEquals(120, vm.uiState.value.durationSeconds)
        assertEquals(120, vm.uiState.value.settings.durationSeconds)
    }

    /** Збережені налаштування підтягуються при створенні екрана. */
    @Test
    fun `saved settings are applied on start`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val store = InMemorySettingsRepository(
            Settings(fontSizeSp = 26, theme = ThemeChoice.DARK, grade = 3, durationSeconds = 30)
        )
        val vm = viewModel(settings = store)

        advanceUntilIdle()

        assertEquals(26, vm.uiState.value.settings.fontSizeSp)
        assertEquals(ThemeChoice.DARK, vm.uiState.value.settings.theme)
        assertEquals(3, vm.uiState.value.settings.grade)
        assertEquals(30, vm.uiState.value.durationSeconds)
    }

    /** Кожне налаштування доходить до сховища. */
    @Test
    fun `settings changes reach the store`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()

        vm.onFontSizeChange(24)
        vm.onLineSpacingChange(1.8f)
        vm.onThemeChange(ThemeChoice.LIGHT)
        vm.onGradeChange(2)
        vm.onSemesterChange(2)
        advanceUntilIdle()

        val settings = vm.uiState.value.settings
        assertEquals(24, settings.fontSizeSp)
        assertEquals(1.8f, settings.lineSpacing, 0.001f)
        assertEquals(ThemeChoice.LIGHT, settings.theme)
        assertEquals(2, settings.grade)
        assertEquals(2, settings.semester)
    }

    /** Оцінка рахується за обраним класом і семестром. */
    @Test
    fun `result is evaluated against the selected grade`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel(norms = FakeNorms(NORMS))
        advanceUntilIdle()

        vm.onGradeChange(2)
        vm.onSemesterChange(2)
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        // 10 слів за 10 с — це 60 слів/хв, верхня межа норми 2 класу, 2 семестр.
        vm.startMeasurement()
        advanceTimeBy(10_000)
        runCurrent()
        vm.stopMeasurement()
        advanceUntilIdle()

        assertEquals(60, vm.uiState.value.result!!.wordsPerMinute)
        assertEquals(NormEvaluation.WITHIN, vm.uiState.value.evaluation)
        assertEquals("у межах норми", vm.uiState.value.evaluationLabel)
    }

    /** Без обраного класу оцінки немає — і це не помилка. */
    @Test
    fun `without a grade there is no evaluation`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel(norms = FakeNorms(NORMS))
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()

        vm.startMeasurement()
        advanceTimeBy(10_000)
        runCurrent()
        vm.stopMeasurement()
        advanceUntilIdle()

        assertEquals(NormEvaluation.UNKNOWN, vm.uiState.value.evaluation)
        assertEquals("", vm.uiState.value.evaluationLabel)
    }

    /** Зміна класу переоцінює вже готовий результат, не перезаміряючи. */
    @Test
    fun `changing the grade re-evaluates the existing result`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel(norms = FakeNorms(NORMS))
        vm.onTextChange(TEXT_OF_TEN_WORDS)
        advanceUntilIdle()
        vm.startMeasurement()
        advanceTimeBy(10_000)
        runCurrent()
        vm.stopMeasurement()
        advanceUntilIdle()

        vm.onGradeChange(4)
        vm.onSemesterChange(2)
        advanceUntilIdle()

        // 60 слів/хв проти норми 90–95 для 4 класу, 2 семестр.
        assertEquals(NormEvaluation.BELOW, vm.uiState.value.evaluation)
    }

    /** Стан незмінний: кожна зміна дає новий об'єкт, а не править старий. */
    @Test
    fun `state is immutable - every change creates a new object`() = runTest(dispatcher, timeout = TEST_TIMEOUT) {
        val vm = viewModel()
        val before = vm.uiState.value

        vm.onTextChange("текст")

        assertNotSame(before, vm.uiState.value)
        assertEquals("", before.text)
    }

    private companion object {

        /**
         * Запобіжник від зависань. Незупинений таймер — це вічний цикл `delay`
         * у `viewModelScope`, і `runTest` докручував би планувальник до простою
         * нескінченно: тест не падав би, а вішав усю збірку. З таймаутом він
         * падає й називає себе.
         */
        val TEST_TIMEOUT = 15.seconds

        /** Рівно десять слів — щоб числа в тестах читалися з першого погляду. */
        const val TEXT_OF_TEN_WORDS =
            "один два три чотири пʼять шість сім вісім девʼять десять"

        /**
         * Мінімальний довідник норм. Збирається руками, а не з JSON: розбір
         * файлу тягне за собою `org.json`, а це вже Android — і тест перестав
         * би бути звичайним JVM-тестом.
         */
        val NORMS = NormsCatalog(
            version = 1,
            grades = listOf(
                GradeNorms(
                    grade = 2,
                    label = "2 клас",
                    semesters = listOf(ReadingNorm(2, 1, 35, 45), ReadingNorm(2, 2, 50, 60))
                ),
                GradeNorms(
                    grade = 4,
                    label = "4 клас",
                    semesters = listOf(ReadingNorm(4, 2, 90, 95))
                )
            ),
            labels = NormLabels.of("нижче норми", "у межах норми", "вище норми")
        )
    }
}

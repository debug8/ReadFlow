package net.readflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.readflow.data.SampleRepository
import net.readflow.model.TextSample
import net.readflow.ui.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    private fun viewModel(samples: SampleRepository = FakeSamples()) =
        MainViewModel(samples, dispatcher)

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
    fun `initial state is empty`() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals("", vm.uiState.value.text)
        assertEquals(0, vm.uiState.value.stats.wordCount)
        assertTrue(vm.uiState.value.isEmpty)
    }

    /** Текст у стані оновлюється миттєво, без очікування дебаунсу. */
    @Test
    fun `text reaches the state immediately`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onTextChange("комп'ютер")

        assertEquals("комп'ютер", vm.uiState.value.text)
    }

    /**
     * Статистика чекає на паузу в 300 мс. Це не «приблизно швидко»: тест зупиняє
     * віртуальний час за мілісекунду до порога й переконується, що чисел ще немає.
     */
    @Test
    fun `stats appear only after the debounce`() = runTest(dispatcher) {
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
    fun `fast typing recalculates only the final text`() = runTest(dispatcher) {
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
    fun `clear resets text and stats`() = runTest(dispatcher) {
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
    fun `clear asks before wiping the text`() = runTest(dispatcher) {
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
    fun `cancelling the confirmation keeps the text`() = runTest(dispatcher) {
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
    fun `confirmed clear wipes text and closes the dialog`() = runTest(dispatcher) {
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
    fun `clear does not ask when there is nothing to clear`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.requestClear()

        assertFalse(vm.uiState.value.isClearConfirmVisible)
    }

    /** Список зразків підтягується під час створення екрана. */
    @Test
    fun `samples are loaded on start`() = runTest(dispatcher) {
        val vm = viewModel(FakeSamples(items = listOf(sampleOne)))

        advanceUntilIdle()

        assertEquals(listOf(sampleOne), vm.uiState.value.samples)
    }

    /** Вибір зразка вставляє його текст, рахує статистику й закриває аркуш. */
    @Test
    fun `choosing a sample loads its text and closes the sheet`() = runTest(dispatcher) {
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
    fun `empty sample does not wipe the current text`() = runTest(dispatcher) {
        val vm = viewModel(FakeSamples(items = listOf(sampleOne)))
        vm.onTextChange("важливий текст")
        advanceUntilIdle()

        vm.onSampleSelected(sampleOne)
        advanceUntilIdle()

        assertEquals("важливий текст", vm.uiState.value.text)
    }

    /** Рядок статистики розгортається й згортається. */
    @Test
    fun `stats row toggles`() = runTest(dispatcher) {
        val vm = viewModel()

        assertFalse(vm.uiState.value.isStatsExpanded)
        vm.toggleStatsExpanded()
        assertTrue(vm.uiState.value.isStatsExpanded)
        vm.toggleStatsExpanded()
        assertFalse(vm.uiState.value.isStatsExpanded)
    }

    /** Слова з їхніми межами приходять разом зі статистикою, з того самого розбору. */
    @Test
    fun `words arrive together with the stats`() = runTest(dispatcher) {
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
        runTest(dispatcher) {
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
    fun `reading mode toggles`() = runTest(dispatcher) {
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
    fun `reading mode does not turn on for empty text`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.toggleReadingMode()

        assertFalse(vm.uiState.value.isReadingMode)
    }

    /** «Очистити» в режимі читання повертає до поля вводу, а не лишає порожню зону. */
    @Test
    fun `clearing the text leaves the reading mode`() = runTest(dispatcher) {
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
    fun `editing the text keeps the reading mode`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTextChange("мама")
        vm.toggleReadingMode()
        advanceUntilIdle()

        vm.onTextChange("мама мила")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isReadingMode)
    }

    /** Тап по слову доходить до ViewModel разом із номером. */
    @Test
    fun `word tap reaches the view model`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        advanceUntilIdle()

        vm.onWordTap(2)

        assertEquals(2, vm.uiState.value.tappedWordNumber)
    }

    /** Після правки тексту старий номер слова скидається: він указував би не туди. */
    @Test
    fun `word tap is forgotten when the text changes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTextChange("мама мила раму")
        advanceUntilIdle()
        vm.onWordTap(3)

        vm.onTextChange("мама мила раму знову")
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.tappedWordNumber)
    }

    /** Стан незмінний: кожна зміна дає новий об'єкт, а не править старий. */
    @Test
    fun `state is immutable - every change creates a new object`() = runTest(dispatcher) {
        val vm = viewModel()
        val before = vm.uiState.value

        vm.onTextChange("текст")

        assertNotSame(before, vm.uiState.value)
        assertEquals("", before.text)
    }
}

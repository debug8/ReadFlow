package net.readflow

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import net.readflow.core.MeasurementCalculator
import net.readflow.core.MeasurementInput
import net.readflow.core.MeasurementMode
import net.readflow.core.NormEvaluation
import net.readflow.core.NormLabels
import net.readflow.core.NormsCatalog
import net.readflow.core.GradeNorms
import net.readflow.core.ReadingNorm
import net.readflow.core.TextStatsCalculator
import net.readflow.model.Settings
import net.readflow.ui.SettingsSheetContent
import net.readflow.ui.SettingsTags
import net.readflow.model.TextSample
import net.readflow.model.TextStats
import net.readflow.ui.MainScreenContent
import net.readflow.ui.MainScreenTags
import net.readflow.ui.SampleSheetContent
import net.readflow.ui.UiState
import net.readflow.ui.theme.ReadFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Екран перевіряється на JVM через Robolectric, без емулятора.
 *
 * Це не заміна перевірці на живому телефоні: тест бачить дерево компонентів,
 * але не бачить, чи зручно влучати пальцем і як воно виглядає.
 */
@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val filledStats = TextStats(
        wordCount = 15,
        charCount = 106,
        charCountNoSpaces = 91,
        letterCount = 87,
        averageWordLength = 5.8,
        sentenceCount = 3,
        paragraphCount = 2
    )

    private fun setScreen(
        state: UiState = UiState(),
        onTextChange: (String) -> Unit = {},
        onClear: () -> Unit = {},
        onToggleStats: () -> Unit = {},
        onChooseSample: () -> Unit = {},
        onToggleReadingMode: () -> Unit = {},
        onWordTap: (Int) -> Unit = {},
        onRequestClear: () -> Unit = {},
        onCancelClear: () -> Unit = {},
        onModeChange: (MeasurementMode) -> Unit = {}
    ) {
        composeRule.setContent {
            ReadFlowTheme {
                MainScreenContent(
                    state = state,
                    onTextChange = onTextChange,
                    onClear = onClear,
                    onToggleStats = onToggleStats,
                    onChooseSample = onChooseSample,
                    onToggleReadingMode = onToggleReadingMode,
                    onWordTap = onWordTap,
                    onRequestClear = onRequestClear,
                    onCancelClear = onCancelClear,
                    onModeChange = onModeChange
                )
            }
        }
    }

    /** Стан із уже розібраним текстом — таким його бачить екран після дебаунсу. */
    private fun readingState(text: String, isReadingMode: Boolean = true) = UiState(
        text = text,
        countedText = text,
        words = TextStatsCalculator.getWords(text),
        isReadingMode = isReadingMode
    )

    /** На екрані видно всі три зони й рядок кнопок. */
    @Test
    fun `all zones are on screen`() {
        setScreen()

        composeRule.onNodeWithTag(MainScreenTags.TEXT_ZONE).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.ACTION_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.STATS_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.CONTROL_PANEL).assertIsDisplayed()
    }

    /** Порожній стан: підказка й дві великі кнопки, без «Очистити». */
    @Test
    fun `empty state shows hint and two buttons without clear`() {
        setScreen()

        composeRule.onNodeWithText("Вставте текст або оберіть зразок").assertIsDisplayed()
        composeRule.onNodeWithText("Вставити").assertIsDisplayed()
        composeRule.onNodeWithText("Обрати зразок").assertIsDisplayed()
        composeRule.onNodeWithText("Очистити").assertDoesNotExist()
    }

    /** «Очистити» з'являється лише тоді, коли є що чистити, і просить підтвердження. */
    @Test
    fun `clear button asks before wiping the text`() {
        var asked = false
        var cleared = false
        setScreen(
            state = UiState(text = "щось"),
            onClear = { cleared = true },
            onRequestClear = { asked = true }
        )

        composeRule.onNodeWithTag(MainScreenTags.CLEAR_BUTTON).assertIsDisplayed().performClick()

        assertEquals("Тап по «Очистити» лише питає.", true, asked)
        assertEquals("Текст не чиститься до підтвердження.", false, cleared)
    }

    /** У порожньому стані кнопки «Очистити» немає. */
    @Test
    fun `clear button is hidden on the empty screen`() {
        setScreen()

        composeRule.onNodeWithTag(MainScreenTags.CLEAR_BUTTON).assertDoesNotExist()
    }

    /** Підтвердження пояснює, що буде, і чистить лише по «Очистити» в діалозі. */
    @Test
    fun `clear confirmation wipes the text only when confirmed`() {
        var cleared = false
        setScreen(
            state = UiState(text = "щось", isClearConfirmVisible = true),
            onClear = { cleared = true }
        )

        composeRule.onNodeWithText("Очистити текст?").assertIsDisplayed()
        composeRule.onNodeWithText("Скасувати").assertIsDisplayed()

        composeRule.onNodeWithTag(MainScreenTags.CLEAR_CONFIRM).performClick()

        assertEquals(true, cleared)
    }

    /** «Скасувати» лишає текст на місці. */
    @Test
    fun `cancel closes the confirmation without clearing`() {
        var cleared = false
        var cancelled = false
        setScreen(
            state = UiState(text = "щось", isClearConfirmVisible = true),
            onClear = { cleared = true },
            onCancelClear = { cancelled = true }
        )

        composeRule.onNodeWithText("Скасувати").performClick()

        assertEquals(true, cancelled)
        assertEquals(false, cleared)
    }

    /** Введений текст передається назовні, у ViewModel. */
    @Test
    fun `typed text is passed out`() {
        var captured = ""
        setScreen(onTextChange = { captured = it })

        composeRule.onNodeWithTag(MainScreenTags.TEXT_ZONE).performTextInput("мавпʼячий")

        assertEquals("мавпʼячий", captured)
    }

    /** Компактний рядок показує чотири числа зі специфікації. */
    @Test
    fun `stats row shows four values`() {
        setScreen(state = UiState(text = "щось", stats = filledStats))

        composeRule.onNodeWithText("15").assertIsDisplayed()
        composeRule.onNodeWithText("106").assertIsDisplayed()
        composeRule.onNodeWithText("87").assertIsDisplayed()
        composeRule.onNodeWithText("5,8").assertIsDisplayed()
    }

    /** Десятковий роздільник — кома, незалежно від локалі телефона. */
    @Test
    fun `average uses ukrainian decimal separator`() {
        setScreen(state = UiState(stats = filledStats))

        composeRule.onNodeWithText("5.8").assertDoesNotExist()
        composeRule.onNodeWithText("5,8").assertIsDisplayed()
    }

    /** Згорнутий рядок ховає додаткові показники. */
    @Test
    fun `collapsed stats hide the extra values`() {
        setScreen(state = UiState(stats = filledStats, isStatsExpanded = false))

        composeRule.onNodeWithText("Речення").assertDoesNotExist()
        composeRule.onNodeWithText("Без пробілів").assertDoesNotExist()
    }

    /** Розгорнутий рядок додає три показники. */
    @Test
    fun `expanded stats add three more values`() {
        setScreen(state = UiState(stats = filledStats, isStatsExpanded = true))

        composeRule.onNodeWithText("Без пробілів").assertIsDisplayed()
        composeRule.onNodeWithText("Речення").assertIsDisplayed()
        composeRule.onNodeWithText("Абзаци").assertIsDisplayed()
        composeRule.onNodeWithText("91").assertIsDisplayed()
    }

    /** Тап по рядку статистики просить розгорнути його. */
    @Test
    fun `tap on stats row asks to toggle`() {
        var toggled = false
        setScreen(state = UiState(stats = filledStats), onToggleStats = { toggled = true })

        composeRule.onNodeWithTag(MainScreenTags.STATS_ROW).performClick()

        assertEquals(true, toggled)
    }

    /** Кнопка «Обрати зразок» просить відкрити аркуш. */
    @Test
    fun `choose sample button asks to open the sheet`() {
        var asked = false
        setScreen(onChooseSample = { asked = true })

        composeRule.onNodeWithText("Обрати зразок").performClick()

        assertEquals(true, asked)
    }

    /** Перемикача «Читання» немає, доки немає тексту. */
    @Test
    fun `reading toggle is hidden on the empty screen`() {
        setScreen()

        composeRule.onNodeWithTag(MainScreenTags.READING_TOGGLE).assertDoesNotExist()
    }

    /** З'явився текст — з'явився й перемикач. */
    @Test
    fun `reading toggle appears when there is text`() {
        setScreen(state = readingState("Мама мила раму.", isReadingMode = false))

        composeRule.onNodeWithTag(MainScreenTags.READING_TOGGLE).assertIsDisplayed()
    }

    /** Тап по перемикачу просить змінити режим. */
    @Test
    fun `reading toggle asks to switch the mode`() {
        var asked = false
        setScreen(
            state = readingState("Мама мила раму.", isReadingMode = false),
            onToggleReadingMode = { asked = true }
        )

        composeRule.onNodeWithTag(MainScreenTags.READING_TOGGLE).performClick()

        assertEquals(true, asked)
    }

    /** У режимі читання поле вводу замінене текстом, а кнопок вставки немає. */
    @Test
    fun `reading mode replaces the input field`() {
        setScreen(state = readingState("Мама мила раму."))

        composeRule.onNodeWithTag(MainScreenTags.READING_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText("Мама мила раму.").assertIsDisplayed()
        composeRule.onNodeWithText("Вставити").assertDoesNotExist()
        composeRule.onNodeWithText("Обрати зразок").assertDoesNotExist()
    }

    /** Короткий тап по слову віддає його номер назовні. */
    @Test
    fun `short tap on a word passes its number out`() {
        var tapped: Int? = null
        setScreen(state = readingState("Мама"), onWordTap = { tapped = it })

        composeRule.onNodeWithTag(MainScreenTags.READING_TEXT).performClick()

        assertEquals(1, tapped)
    }

    /** Довгий тап показує «Слово №N», а короткий — ні. */
    @Test
    fun `long tap shows the word number`() {
        setScreen(state = readingState("Мама"))

        composeRule.onNodeWithTag(MainScreenTags.WORD_TOOLTIP).assertDoesNotExist()

        composeRule.onNodeWithTag(MainScreenTags.READING_TEXT).performTouchInput { longClick() }

        composeRule.onNodeWithText("Слово №1").assertIsDisplayed()
    }

    /**
     * Разом із підказкою підсвічується й саме слово: інакше незрозуміло, до чого
     * той номер. Перевіряється по стилях у тексті, а не по пікселях.
     */
    @Test
    fun `long tap highlights the word itself`() {
        setScreen(state = readingState("Мама"))

        assertEquals("До тапа слово без стилю.", 0, readingSpanCount())

        composeRule.onNodeWithTag(MainScreenTags.READING_TEXT).performTouchInput { longClick() }

        assertEquals("Після тапа слово підсвічене.", 1, readingSpanCount())
    }

    /** Скільки слів у зоні читання мають власний стиль. */
    private fun readingSpanCount(): Int = composeRule
        .onNodeWithTag(MainScreenTags.READING_TEXT)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        ?.firstOrNull()
        ?.spanStyles
        ?.size
        ?: -1

    /**
     * Головні кнопки лишаються читабельними, коли поруч з'явилися перемикач
     * режиму й «Очистити». На живому телефоні чотири елементи в один рядок
     * стиснули підписи так, що текст став вертикальним.
     */
    @Test
    fun `main buttons stay wide when the text is there`() {
        setScreen(state = readingState("Мама мила раму.", isReadingMode = false))

        composeRule.onNodeWithText("Вставити").assertWidthIsAtLeast(120.dp)
        composeRule.onNodeWithText("Обрати зразок").assertWidthIsAtLeast(120.dp)
    }

    // --- Задачі 5–7: замір, режими, налаштування ---

    /** Перемикач режимів з'являється разом із текстом і показує всі три режими. */
    @Test
    fun `mode selector shows all three modes`() {
        setScreen(state = readingState("Мама мила раму.", isReadingMode = false))

        composeRule.onNodeWithTag(MainScreenTags.MODE_SELECTOR).assertIsDisplayed()
        composeRule.onNodeWithText("Тап").assertIsDisplayed()
        composeRule.onNodeWithText("Таймер").assertIsDisplayed()
        composeRule.onNodeWithText("Помилки").assertIsDisplayed()
    }

    /** Тап по сегменту просить змінити режим. */
    @Test
    fun `mode selector asks to change the mode`() {
        var chosen: MeasurementMode? = null
        setScreen(
            state = readingState("Мама мила раму.", isReadingMode = false),
            onModeChange = { chosen = it }
        )

        composeRule.onNodeWithText("Помилки").performClick()

        assertEquals(MeasurementMode.ERRORS, chosen)
    }

    /** Кнопка заміру неактивна, доки нема тексту, і активна, коли він є. */
    @Test
    fun `start button is disabled without text`() {
        setScreen()

        composeRule.onNodeWithTag(MainScreenTags.START_STOP).assertIsNotEnabled()
    }

    /** Під час заміру кнопка стає «Стоп». */
    @Test
    fun `running measurement shows stop and the elapsed time`() {
        setScreen(
            state = readingState("Мама мила раму.", isReadingMode = false)
                .copy(isTimerRunning = true, elapsedMillis = 95_000)
        )

        composeRule.onNodeWithText("Стоп").assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.TIMER_VALUE).assertTextEquals("01:35")
    }

    /** До заміру підсумку на екрані немає. */
    @Test
    fun `result row is hidden without a result`() {
        setScreen(state = readingState("Мама мила раму.", isReadingMode = false))

        composeRule.onNodeWithTag(MainScreenTags.RESULT_ROW).assertDoesNotExist()
    }

    /** Підсумок показує швидкість, помилки й оцінку за нормою. */
    @Test
    fun `result row shows the numbers and the norm verdict`() {
        val text = "один два три чотири пʼять шість сім вісім девʼять десять"
        val base = readingState(text, isReadingMode = false)
        val result = MeasurementCalculator.evaluate(
            MeasurementInput(
                mode = MeasurementMode.ERRORS,
                durationSeconds = 60,
                elapsedMillis = 10_000,
                isRunning = false,
                // Числа задані прямо, а не взяті зі стану: тест про рядок
                // підсумку, а не про підрахунок — той перевірений окремо.
                totalWords = 10,
                totalCharsNoSpaces = 46,
                errorWordNumbers = setOf(1)
            )
        )!!

        setScreen(
            state = base.copy(
                mode = MeasurementMode.ERRORS,
                elapsedMillis = 10_000,
                result = result,
                evaluation = NormEvaluation.WITHIN,
                norms = NORMS,
                settings = Settings(grade = 2, semester = 2)
            )
        )

        composeRule.onNodeWithTag(MainScreenTags.RESULT_ROW).assertIsDisplayed()
        // 10 слів за 10 с — це 60 слів/хв; без однієї помилки — 54.
        composeRule.onNodeWithText("60").assertIsDisplayed()
        composeRule.onNodeWithText("54").assertIsDisplayed()
        composeRule.onNodeWithText("у межах норми").assertIsDisplayed()
    }

    /** Аркуш налаштувань показує клас із довідника, а не з коду. */
    @Test
    fun `settings sheet lists grades from the norms catalog`() {
        composeRule.setContent {
            ReadFlowTheme {
                SettingsSheetContent(settings = Settings(grade = 2, semester = 2), norms = NORMS)
            }
        }

        // assertExists, а не assertIsDisplayed: аркуш у тесті не має висоти
        // екрана, тож нижні рядки існують у дереві, але лежать за краєм.
        composeRule.onNodeWithText("2 клас").assertExists()
        composeRule.onNodeWithText("4 клас").assertExists()
        composeRule.onNodeWithText("Норма: 50–60 слів за хвилину").assertExists()
    }

    /** Без довідника норм клас обирати нема з чого — і це пояснено словами. */
    @Test
    fun `settings sheet explains a missing norms catalog`() {
        composeRule.setContent {
            ReadFlowTheme {
                SettingsSheetContent(settings = Settings(), norms = NormsCatalog.Empty)
            }
        }

        composeRule.onNodeWithTag(SettingsTags.NORM_HINT).assertExists()
    }

    /** Аркуш зразків групує тексти за класами й показує кількість слів. */
    @Test
    fun `sample sheet groups by grade`() {
        val samples = listOf(
            TextSample("sample-01", "Короткий текст", "sample-01.txt", 1, "легкий", 15),
            TextSample("sample-02", "Довший текст", "sample-02.txt", 2, "середній", 122)
        )
        var chosen: TextSample? = null

        composeRule.setContent {
            ReadFlowTheme { SampleSheetContent(samples = samples, onSampleSelected = { chosen = it }) }
        }

        composeRule.onNodeWithText("1 клас").assertIsDisplayed()
        composeRule.onNodeWithText("2 клас").assertIsDisplayed()
        composeRule.onNodeWithText("легкий · 15 слів").assertIsDisplayed()
        composeRule.onNodeWithText("середній · 122 слова").assertIsDisplayed()

        composeRule.onNodeWithText("Довший текст").performClick()

        assertEquals("sample-02", chosen?.id)
    }

    /** Порожній список зразків пояснює, що робити, а не показує порожнечу. */
    @Test
    fun `empty sample list explains what to do`() {
        composeRule.setContent {
            ReadFlowTheme { SampleSheetContent(samples = emptyList(), onSampleSelected = {}) }
        }

        composeRule.onNodeWithText(
            "Зразків ще немає. Додайте тексти в shared/samples/ і перезберіть додаток."
        ).assertIsDisplayed()
    }

    private companion object {

        /** Довідник норм для екранних тестів — рівно два класи. */
        val NORMS = NormsCatalog(
            version = 1,
            grades = listOf(
                GradeNorms(2, "2 клас", listOf(ReadingNorm(2, 2, 50, 60))),
                GradeNorms(4, "4 клас", listOf(ReadingNorm(4, 2, 90, 95)))
            ),
            labels = NormLabels.of("нижче норми", "у межах норми", "вище норми")
        )
    }
}

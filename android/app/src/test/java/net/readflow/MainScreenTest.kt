package net.readflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        onChooseSample: () -> Unit = {}
    ) {
        composeRule.setContent {
            ReadFlowTheme {
                MainScreenContent(
                    state = state,
                    onTextChange = onTextChange,
                    onClear = onClear,
                    onToggleStats = onToggleStats,
                    onChooseSample = onChooseSample
                )
            }
        }
    }

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

    /** «Очистити» з'являється лише тоді, коли є що чистити. */
    @Test
    fun `clear button appears only when there is text`() {
        var cleared = false
        setScreen(state = UiState(text = "щось"), onClear = { cleared = true })

        composeRule.onNodeWithText("Очистити").assertIsDisplayed().performClick()

        assertEquals(true, cleared)
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
}

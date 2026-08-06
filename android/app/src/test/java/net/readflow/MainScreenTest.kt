package net.readflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import net.readflow.ui.MainScreenContent
import net.readflow.ui.MainScreenTags
import net.readflow.ui.UiState
import net.readflow.ui.theme.ReadFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Перевірка критерію «видно три зони» — на JVM через Robolectric, без емулятора.
 *
 * Це не заміна перевірці на живому телефоні: тест бачить дерево компонентів,
 * але не бачить, чи зручно влучати пальцем і як воно виглядає.
 */
@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(onTextChange: (String) -> Unit = {}) {
        composeRule.setContent {
            ReadFlowTheme { MainScreenContent(state = UiState(), onTextChange = onTextChange) }
        }
    }

    /** На екрані видно всі три зони. */
    @Test
    fun `all three zones are on screen`() {
        setScreen()

        composeRule.onNodeWithTag(MainScreenTags.TEXT_ZONE).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.STATS_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.CONTROL_PANEL).assertIsDisplayed()
    }

    /** Порожній стан показує підказку українською. */
    @Test
    fun `empty state shows the ukrainian hint`() {
        setScreen()

        composeRule.onNodeWithText("Вставте текст або оберіть зразок").assertIsDisplayed()
    }

    /** Введений текст передається назовні, у ViewModel. */
    @Test
    fun `typed text is passed out`() {
        var captured = ""
        setScreen(onTextChange = { captured = it })

        composeRule.onNodeWithTag(MainScreenTags.TEXT_ZONE).performTextInput("мавпʼячий")

        assertEquals("мавпʼячий", captured)
    }

    /** Рядок статистики показує чотири підписи зі специфікації. */
    @Test
    fun `stats row shows four labels`() {
        setScreen()

        composeRule.onNodeWithText("Слова").assertIsDisplayed()
        composeRule.onNodeWithText("Знаки").assertIsDisplayed()
        composeRule.onNodeWithText("Букви").assertIsDisplayed()
        composeRule.onNodeWithText("Сер. довжина").assertIsDisplayed()
    }

    /** Нижня панель показує лічильник і кнопку Старт. */
    @Test
    fun `control panel shows timer and start button`() {
        setScreen()

        composeRule.onNodeWithText("00:00").assertIsDisplayed()
        composeRule.onNodeWithText("Старт").assertIsDisplayed()
    }
}

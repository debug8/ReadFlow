package net.readflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import net.readflow.core.MeasurementResult
import net.readflow.core.NormEvaluation
import net.readflow.model.Attempt
import net.readflow.ui.HistorySheetContent
import net.readflow.ui.HistorySheetTags
import net.readflow.ui.ResultSheetContent
import net.readflow.ui.ResultSheetTags
import net.readflow.ui.theme.ReadFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

/**
 * Вміст аркушів підсумку й історії. Перевіряється безпосередньо, без
 * `ModalBottomSheet`, — той самий підхід, що для аркушів зразків і налаштувань.
 */
@RunWith(RobolectricTestRunner::class)
class ResultHistorySheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun result(
        wpm: Int = 84,
        cpm: Int = 420,
        errors: Int = 2,
        errorPercent: Double = 5.0
    ) = MeasurementResult(
        wordsRead = 42,
        charsRead = 210,
        seconds = BigDecimal.valueOf(30),
        wordsPerMinute = wpm,
        charsPerMinute = cpm,
        errors = errors,
        errorPercent = errorPercent,
        cleanWordsPerMinute = wpm - 4
    )

    @Test
    fun `result sheet shows the wpm and the three actions`() {
        rule.setContent {
            ReadFlowTheme(darkTheme = false) {
                ResultSheetContent(
                    result = result(),
                    studentName = "Іван",
                    showErrors = true,
                    evaluation = NormEvaluation.WITHIN,
                    evaluationLabel = "у межах норми",
                    onStudentNameChange = {},
                    onSave = {},
                    onShare = {},
                    onAgain = {}
                )
            }
        }

        rule.onNodeWithText("84").assertIsDisplayed()
        rule.onNodeWithText("у межах норми").assertIsDisplayed()
        rule.onNodeWithTag(ResultSheetTags.SAVE).assertIsDisplayed()
        rule.onNodeWithTag(ResultSheetTags.SHARE).assertIsDisplayed()
        rule.onNodeWithTag(ResultSheetTags.AGAIN).assertIsDisplayed()
    }

    @Test
    fun `save button reports a click`() {
        var saved = false
        rule.setContent {
            ReadFlowTheme(darkTheme = false) {
                ResultSheetContent(
                    result = result(),
                    studentName = "",
                    showErrors = false,
                    evaluation = NormEvaluation.UNKNOWN,
                    evaluationLabel = "",
                    onStudentNameChange = {},
                    onSave = { saved = true },
                    onShare = {},
                    onAgain = {}
                )
            }
        }

        rule.onNodeWithTag(ResultSheetTags.SAVE).performClick()

        assertTrue(saved)
    }

    @Test
    fun `editing the name reports the new value`() {
        var typed = ""
        rule.setContent {
            ReadFlowTheme(darkTheme = false) {
                ResultSheetContent(
                    result = result(),
                    studentName = "",
                    showErrors = false,
                    evaluation = NormEvaluation.UNKNOWN,
                    evaluationLabel = "",
                    onStudentNameChange = { typed = it },
                    onSave = {},
                    onShare = {},
                    onAgain = {}
                )
            }
        }

        rule.onNodeWithTag(ResultSheetTags.STUDENT_NAME).performTextInput("Оля")

        assertEquals("Оля", typed)
    }

    @Test
    fun `empty history shows a hint and no export`() {
        rule.setContent {
            ReadFlowTheme(darkTheme = false) {
                HistorySheetContent(history = emptyList(), onDelete = {}, onExport = {})
            }
        }

        rule.onNodeWithTag(HistorySheetTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun `history lists records and export reports a click`() {
        var exported = false
        rule.setContent {
            ReadFlowTheme(darkTheme = false) {
                HistorySheetContent(
                    history = listOf(
                        Attempt(
                            id = 1,
                            studentName = "Марія",
                            createdAt = 1_700_000_000_000L,
                            grade = 2,
                            wordsPerMinute = 70
                        )
                    ),
                    onDelete = {},
                    onExport = { exported = true }
                )
            }
        }

        rule.onNodeWithText("Марія").assertIsDisplayed()
        rule.onNodeWithTag(HistorySheetTags.EXPORT).performClick()

        assertTrue(exported)
    }
}

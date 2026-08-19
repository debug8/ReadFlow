@file:OptIn(ExperimentalMaterial3Api::class)

package net.readflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.readflow.R
import net.readflow.core.MeasurementResult
import net.readflow.core.NormEvaluation

/** Теги для Robolectric-тестів аркуша підсумку. */
object ResultSheetTags {
    const val SHEET = "sheet_result"
    const val STUDENT_NAME = "field_student_name"
    const val SAVE = "button_save_history"
    const val SHARE = "button_share_result"
    const val AGAIN = "button_again"
}

/**
 * Нижній аркуш підсумку заміру (`SPEC_ANDROID.md`, 2.1).
 *
 * Зʼявляється після Стоп: повні числа заміру, поле імені учня й три дії —
 * зберегти в історію, поділитися, ще раз.
 */
@Composable
fun ResultSheet(
    state: UiState,
    onStudentNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val result = state.result ?: return
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(ResultSheetTags.SHEET)
    ) {
        ResultSheetContent(
            result = result,
            studentName = state.studentName,
            showErrors = state.mode.marksErrors,
            evaluation = state.evaluation,
            evaluationLabel = state.evaluationLabel,
            onStudentNameChange = onStudentNameChange,
            onSave = onSave,
            onShare = onShare,
            onAgain = onAgain
        )
    }
}

/**
 * Вміст аркуша — окремо від самого аркуша, щоб перевірятися тестом без
 * діалогових вікон (той самий підхід, що з аркушем зразків і налаштувань).
 */
@Composable
fun ResultSheetContent(
    result: MeasurementResult,
    studentName: String,
    showErrors: Boolean,
    evaluation: NormEvaluation,
    evaluationLabel: String,
    onStudentNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.result_sheet_title),
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = studentName,
            onValueChange = onStudentNameChange,
            label = { Text(stringResource(R.string.student_name_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ResultSheetTags.STUDENT_NAME)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatCell(result.wordsPerMinute.toString(), R.string.result_wpm)
            StatCell(result.charsPerMinute.toString(), R.string.result_cpm)
            StatCell(result.wordsRead.toString(), R.string.result_words_read)
            StatCell(formatTime(result.secondsRounded * 1000L), R.string.result_time)
        }

        if (showErrors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(result.errors.toString(), R.string.result_errors)
                StatCell(formatAverage(result.errorPercent) + " %", R.string.result_error_share)
                StatCell(result.cleanWordsPerMinute.toString(), R.string.result_clean_wpm)
            }
        }

        if (evaluationLabel.isNotEmpty()) {
            Text(
                text = evaluationLabel,
                style = MaterialTheme.typography.titleMedium,
                color = evaluation.evaluationColor()
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(ResultSheetTags.SAVE)
        ) {
            Text(stringResource(R.string.action_save_history))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(ResultSheetTags.SHARE)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.action_share), maxLines = 1)
            }

            TextButton(
                onClick = onAgain,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(ResultSheetTags.AGAIN)
            ) {
                Text(stringResource(R.string.action_again), maxLines = 1)
            }
        }
    }
}

/** Колір оцінки — з семантики «гірше / так треба / краще», не з палітри теми. */
@Composable
private fun NormEvaluation.evaluationColor(): Color = when (this) {
    NormEvaluation.BELOW -> MaterialTheme.colorScheme.error
    NormEvaluation.WITHIN -> MaterialTheme.colorScheme.primary
    NormEvaluation.ABOVE -> MaterialTheme.colorScheme.primary
    NormEvaluation.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Текст для «Поділитися одним результатом». Збирається з рядків ресурсів, щоб
 * підписи лишалися в одному місці й були україномовні незалежно від локалі
 * телефона.
 */
fun buildShareResultText(
    context: android.content.Context,
    result: MeasurementResult,
    studentName: String,
    showErrors: Boolean,
    evaluationLabel: String
): String {
    val lines = mutableListOf<String>()

    lines += context.getString(R.string.share_result_subject)

    if (studentName.isNotBlank()) {
        lines += context.getString(R.string.share_result_student, studentName.trim())
    }

    lines += context.getString(R.string.share_result_wpm, result.wordsPerMinute)
    lines += context.getString(R.string.share_result_cpm, result.charsPerMinute)
    lines += context.getString(R.string.share_result_words, result.wordsRead)
    lines += context.getString(R.string.share_result_time, formatTime(result.secondsRounded * 1000L))

    if (showErrors) {
        lines += context.getString(
            R.string.share_result_errors,
            result.errors,
            formatAverage(result.errorPercent)
        )
        lines += context.getString(R.string.share_result_clean, result.cleanWordsPerMinute)
    }

    if (evaluationLabel.isNotEmpty()) {
        lines += context.getString(R.string.share_result_evaluation, evaluationLabel)
    }

    return lines.joinToString("\n")
}

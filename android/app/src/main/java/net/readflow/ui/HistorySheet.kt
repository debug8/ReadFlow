@file:OptIn(ExperimentalMaterial3Api::class)

package net.readflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.readflow.R
import net.readflow.model.Attempt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Теги для Robolectric-тестів аркуша історії. */
object HistorySheetTags {
    const val SHEET = "sheet_history"
    const val EXPORT = "button_export_csv"
    const val EMPTY = "text_history_empty"
    const val LIST = "list_history"
}

/**
 * Нижній аркуш історії замірів (`SPEC_ANDROID.md`, 2.1).
 *
 * Список збережених спроб, свайп для видалення й експорт усієї історії в CSV
 * через системний «Поділитися».
 */
@Composable
fun HistorySheet(
    history: List<Attempt>,
    onDelete: (Long) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(HistorySheetTags.SHEET)
    ) {
        HistorySheetContent(
            history = history,
            onDelete = onDelete,
            onExport = onExport
        )
    }
}

/** Вміст аркуша історії — окремо, щоб перевірятися тестом без діалога. */
@Composable
fun HistorySheetContent(
    history: List<Attempt>,
    onDelete: (Long) -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge
            )

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(HistorySheetTags.EXPORT)
                ) {
                    Text(stringResource(R.string.action_export_csv))
                }
            }
        }

        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag(HistorySheetTags.EMPTY)
            )
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HistorySheetTags.LIST)
        ) {
            items(history, key = { it.id }) { attempt ->
                SwipeableHistoryRow(attempt = attempt, onDelete = { onDelete(attempt.id) })
                HorizontalDivider()
            }
        }
    }
}

/** Рядок історії зі свайпом у будь-який бік для видалення. */
@Composable
private fun SwipeableHistoryRow(attempt: Attempt, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { DeleteBackground() },
        content = { HistoryRow(attempt) }
    )
}

/** Червоне тло з іконкою кошика, що визирає з-під рядка при свайпі. */
@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.history_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun HistoryRow(attempt: Attempt) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val name = attempt.studentName.ifBlank { stringResource(R.string.history_no_name) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            if (attempt.grade > 0) {
                Text(
                    text = stringResource(R.string.samples_grade, attempt.grade),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(
                R.string.history_row_stats,
                attempt.wordsPerMinute,
                formatHistoryDate(attempt.createdAt)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val historyDateFormat =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag("uk"))

/** Дата запису у місцевому поясі телефона. */
private fun formatHistoryDate(epochMillis: Long): String =
    historyDateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

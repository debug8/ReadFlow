@file:OptIn(ExperimentalMaterial3Api::class)

package net.readflow.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.readflow.R
import net.readflow.core.MeasurementMode
import net.readflow.core.MeasurementResult
import net.readflow.core.NormEvaluation
import net.readflow.core.TextStatsCalculator
import net.readflow.model.Settings
import net.readflow.model.TextSample
import net.readflow.model.TextStats
import net.readflow.model.ThemeChoice
import net.readflow.model.WordMark
import net.readflow.model.WordToken
import net.readflow.ui.theme.ReadFlowTheme
import java.util.Locale

/** Теги для тестів: екран перевіряється на JVM через Robolectric, без емулятора. */
object MainScreenTags {
    const val TEXT_ZONE = "zone_text"
    const val STATS_ROW = "zone_stats"
    const val CONTROL_PANEL = "zone_controls"
    const val ACTION_ROW = "row_actions"
    const val READING_TOGGLE = "toggle_reading"
    const val CLEAR_BUTTON = "button_clear"
    const val CLEAR_CONFIRM = "button_clear_confirm"
    const val READING_TEXT = "text_reading"
    const val WORD_TOOLTIP = "tooltip_word"
    const val MODE_SELECTOR = "selector_mode"
    const val TIMER_VALUE = "value_timer"
    const val START_STOP = "button_start_stop"
    const val RESULT_ROW = "row_result"
    const val SETTINGS_BUTTON = "button_settings"
}

/**
 * Точка входу застосунку: тема залежить від налаштувань, тож вона всередині,
 * а не навколо — інакше вибір «світла / темна» не було б звідки прочитати.
 */
@Composable
fun ReadFlowApp(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(LocalContext.current))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Сигнал про кінець заміру — подія, а не стан: інакше після повороту
    // екрана телефон дзвенів би вдруге.
    LaunchedEffect(Unit) {
        viewModel.durationReached.collect { MeasurementAlerts.signalDurationReached(context) }
    }

    // Екран не гасне лише під час активного заміру (`SPEC_ANDROID.md`, 2.1).
    val view = LocalView.current
    DisposableEffect(state.isTimerRunning) {
        view.keepScreenOn = state.isTimerRunning
        onDispose { view.keepScreenOn = false }
    }

    ReadFlowTheme(darkTheme = state.settings.theme.isDark()) {
        MainScreenContent(
            state = state,
            onTextChange = viewModel::onTextChange,
            onPaste = { viewModel.onTextChange(readClipboardText(context).orEmpty()) },
            onClear = viewModel::clearText,
            onToggleStats = viewModel::toggleStatsExpanded,
            onChooseSample = viewModel::showSampleSheet,
            onSampleSelected = viewModel::onSampleSelected,
            onDismissSampleSheet = viewModel::hideSampleSheet,
            onToggleReadingMode = viewModel::toggleReadingMode,
            onWordTap = viewModel::onWordTap,
            onWordLongPress = viewModel::onWordLongPress,
            onRequestClear = viewModel::requestClear,
            onCancelClear = viewModel::cancelClear,
            onModeChange = viewModel::onModeChange,
            onDurationChange = viewModel::onDurationChange,
            onToggleMeasurement = viewModel::toggleMeasurement,
            onShowSettings = viewModel::showSettingsSheet,
            onDismissSettings = viewModel::hideSettingsSheet,
            onFontSizeChange = viewModel::onFontSizeChange,
            onLineSpacingChange = viewModel::onLineSpacingChange,
            onThemeChange = viewModel::onThemeChange,
            onGradeChange = viewModel::onGradeChange,
            onSemesterChange = viewModel::onSemesterChange
        )
    }
}

/** Вибір теми в налаштуваннях; системна питає систему. */
@Composable
private fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/**
 * Розмітка екрана без ViewModel — щоб її можна було і показати в Preview,
 * і перевірити тестом, передавши потрібний стан напряму.
 */
@Composable
fun MainScreenContent(
    state: UiState,
    onTextChange: (String) -> Unit = {},
    onPaste: () -> Unit = {},
    onClear: () -> Unit = {},
    onToggleStats: () -> Unit = {},
    onChooseSample: () -> Unit = {},
    onSampleSelected: (TextSample) -> Unit = {},
    onDismissSampleSheet: () -> Unit = {},
    onToggleReadingMode: () -> Unit = {},
    onWordTap: (Int) -> Unit = {},
    onWordLongPress: (Int) -> Unit = {},
    onRequestClear: () -> Unit = {},
    onCancelClear: () -> Unit = {},
    onModeChange: (MeasurementMode) -> Unit = {},
    onDurationChange: (Int) -> Unit = {},
    onToggleMeasurement: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    onDismissSettings: () -> Unit = {},
    onFontSizeChange: (Int) -> Unit = {},
    onLineSpacingChange: (Float) -> Unit = {},
    onThemeChange: (ThemeChoice) -> Unit = {},
    onGradeChange: (Int) -> Unit = {},
    onSemesterChange: (Int) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TopRow(
                mode = state.mode,
                showModes = !state.isEmpty,
                onModeChange = onModeChange,
                onShowSettings = onShowSettings
            )

            val zoneModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(MainScreenTags.TEXT_ZONE)

            if (state.isReadingMode) {
                ReadingView(
                    text = state.countedText,
                    words = state.words,
                    fontSizeSp = state.settings.fontSizeSp,
                    lineSpacing = state.settings.lineSpacing,
                    onWordTap = onWordTap,
                    onWordLongPress = onWordLongPress,
                    markOf = rememberWordMarks(state),
                    modifier = zoneModifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                TextZone(
                    text = state.text,
                    onTextChange = onTextChange,
                    modifier = zoneModifier
                )
            }

            ActionRow(
                showSecondary = !state.isEmpty,
                isReadingMode = state.isReadingMode,
                onPaste = onPaste,
                onChooseSample = onChooseSample,
                onRequestClear = onRequestClear,
                onToggleReadingMode = onToggleReadingMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.ACTION_ROW)
            )

            HorizontalDivider()

            StatsRow(
                stats = state.stats,
                isExpanded = state.isStatsExpanded,
                onToggle = onToggleStats,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.STATS_ROW)
            )

            HorizontalDivider()

            state.result?.let { result ->
                ResultRow(
                    result = result,
                    showErrors = state.mode.marksErrors,
                    evaluation = state.evaluation,
                    evaluationLabel = state.evaluationLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MainScreenTags.RESULT_ROW)
                )

                HorizontalDivider()
            }

            ControlPanel(
                durationSeconds = state.durationSeconds,
                elapsedMillis = state.elapsedMillis,
                isRunning = state.isTimerRunning,
                canMeasure = state.canMeasure,
                onDurationChange = onDurationChange,
                onToggleMeasurement = onToggleMeasurement,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.CONTROL_PANEL)
            )
        }

        if (state.isSampleSheetVisible) {
            SampleSheet(
                samples = state.samples,
                onSampleSelected = onSampleSelected,
                onDismiss = onDismissSampleSheet
            )
        }

        if (state.isSettingsSheetVisible) {
            SettingsSheet(
                settings = state.settings,
                norms = state.norms,
                onDismiss = onDismissSettings,
                onFontSizeChange = onFontSizeChange,
                onLineSpacingChange = onLineSpacingChange,
                onDurationChange = onDurationChange,
                onThemeChange = onThemeChange,
                onGradeChange = onGradeChange,
                onSemesterChange = onSemesterChange
            )
        }

        if (state.isClearConfirmVisible) {
            ClearConfirmDialog(onConfirm = onClear, onDismiss = onCancelClear)
        }
    }
}

/**
 * Позначки слів для режиму читання.
 *
 * Коли слово є і межею, і помилкою, показується **колір помилки**
 * (`SPEC.md`, 4.7). Причина не естетична: інакше тап по слову-межі не давав би
 * видимого відгуку, і вчитель вирішив би, що застосунок його не почув.
 */
@Composable
private fun rememberWordMarks(state: UiState): (WordToken) -> WordMark {
    val boundary = state.boundaryWordNumber
    val errors = state.errorWordNumbers

    return remember(boundary, errors) {
        { word ->
            when {
                word.number in errors -> WordMark.ERROR
                word.number == boundary -> WordMark.BOUNDARY
                else -> WordMark.NONE
            }
        }
    }
}

/** Сегментований перемикач режимів і кнопка налаштувань. */
@Composable
private fun TopRow(
    mode: MeasurementMode,
    showModes: Boolean,
    onModeChange: (MeasurementMode) -> Unit,
    onShowSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showModes) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .weight(1f)
                    .testTag(MainScreenTags.MODE_SELECTOR)
            ) {
                MeasurementMode.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = item == mode,
                        onClick = { onModeChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MeasurementMode.entries.size
                        ),
                        // Галочка з'їдає ширину, якої й так обмаль: на 360 dp
                        // разом із нею «Помилки» переносилось у два рядки.
                        icon = {},
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(text = stringResource(item.labelRes()), maxLines = 1)
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        IconButton(
            onClick = onShowSettings,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(MainScreenTags.SETTINGS_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.action_settings)
            )
        }
    }
}

/** Підпис режиму. Літери A/B/C зі специфікації лишилися в документації. */
private fun MeasurementMode.labelRes(): Int = when (this) {
    MeasurementMode.TAP_STOP -> R.string.mode_tap_stop
    MeasurementMode.TIMER -> R.string.mode_timer
    MeasurementMode.ERRORS -> R.string.mode_errors
}

@Composable
private fun TextZone(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.text_placeholder)) },
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun ActionRow(
    showSecondary: Boolean,
    isReadingMode: Boolean,
    onPaste: () -> Unit,
    onChooseSample: () -> Unit,
    onRequestClear: () -> Unit,
    onToggleReadingMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Два ряди, а не один: на телефоні 360 dp чотири елементи в рядок
        // стискали «Вставити» й «Обрати зразок» до однієї літери на рядок —
        // підпис ставав вертикальним. Другий ряд з'являється лише разом
        // із текстом, тож стан за замовчуванням лишається двома кнопками.
        if (!isReadingMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPaste,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.action_paste), maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onChooseSample,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.action_choose_sample), maxLines = 1)
                }
            }
        }

        if (showSecondary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = isReadingMode,
                    onClick = onToggleReadingMode,
                    label = {
                        Text(text = stringResource(R.string.action_reading_mode), maxLines = 1)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(MainScreenTags.READING_TOGGLE)
                )

                TextButton(
                    onClick = onRequestClear,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(MainScreenTags.CLEAR_BUTTON)
                ) {
                    Text(text = stringResource(R.string.action_clear), maxLines = 1)
                }
            }
        }
    }
}

/**
 * Підтвердження очищення.
 *
 * На десктопі його свідомо немає: там миша, а повернути текст — це Ctrl+V.
 * На телефоні «Очистити» стоїть поруч із перемикачем режиму, палець ширший
 * за кнопку, а буфер обміну до того часу вже інший.
 */
@Composable
private fun ClearConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_confirm_title)) },
        text = { Text(stringResource(R.string.clear_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(MainScreenTags.CLEAR_CONFIRM)
            ) {
                Text(stringResource(R.string.action_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Компактний рядок статистики; тап розгортає його в повний список.
 */
@Composable
private fun StatsRow(
    stats: TextStats,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(stats.wordCount.toString(), R.string.stat_words)
                StatCell(stats.charCount.toString(), R.string.stat_chars)
                StatCell(stats.letterCount.toString(), R.string.stat_letters)
                StatCell(formatAverage(stats.averageWordLength), R.string.stat_avg_word_length)
            }

            AnimatedVisibility(visible = isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell(stats.charCountNoSpaces.toString(), R.string.stat_chars_no_spaces)
                    StatCell(stats.sentenceCount.toString(), R.string.stat_sentences)
                    StatCell(stats.paragraphCount.toString(), R.string.stat_paragraphs)
                }
            }
        }
    }
}

/** Підсумок заміру: швидкість, знаки за хвилину, час і — у режимі C — помилки. */
@Composable
private fun ResultRow(
    result: MeasurementResult,
    showErrors: Boolean,
    evaluation: NormEvaluation,
    evaluationLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    style = MaterialTheme.typography.titleSmall,
                    color = evaluation.color()
                )
            }
        }
    }
}

/** Колір оцінки: не з палітри теми, а з семантики «гірше / так треба / краще». */
@Composable
private fun NormEvaluation.color(): Color = when (this) {
    NormEvaluation.BELOW -> MaterialTheme.colorScheme.error
    NormEvaluation.WITHIN -> MaterialTheme.colorScheme.primary
    NormEvaluation.ABOVE -> MaterialTheme.colorScheme.primary
    NormEvaluation.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun StatCell(value: String, labelRes: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Нижня панель керування: чіпи тривалості, великий лічильник і Старт/Стоп.
 */
@Composable
private fun ControlPanel(
    durationSeconds: Int,
    elapsedMillis: Long,
    isRunning: Boolean,
    canMeasure: Boolean,
    onDurationChange: (Int) -> Unit,
    onToggleMeasurement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UiState.DURATION_CHOICES.forEach { seconds ->
                    FilterChip(
                        selected = seconds == durationSeconds,
                        // Міняти тривалість посеред заміру означало б зсунути
                        // позначку, від якої вже відлічують.
                        enabled = !isRunning,
                        onClick = { onDurationChange(seconds) },
                        label = { Text(stringResource(R.string.duration_seconds, seconds)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Text(
                text = formatTime(elapsedMillis),
                style = MaterialTheme.typography.displaySmall,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.testTag(MainScreenTags.TIMER_VALUE)
            )

            Button(
                onClick = onToggleMeasurement,
                enabled = canMeasure,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag(MainScreenTags.START_STOP)
            ) {
                Text(
                    text = stringResource(
                        if (isRunning) R.string.action_stop else R.string.action_start
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SampleSheet(
    samples: List<TextSample>,
    onSampleSelected: (TextSample) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        SampleSheetContent(
            samples = samples,
            onSampleSelected = { sample ->
                scope.launch { sheetState.hide() }
                onSampleSelected(sample)
            }
        )
    }
}

/**
 * Вміст аркуша вибору — окремо від самого аркуша, щоб перевірятися тестом
 * без діалогових вікон.
 */
@Composable
fun SampleSheetContent(
    samples: List<TextSample>,
    onSampleSelected: (TextSample) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.samples_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        if (samples.isEmpty()) {
            Text(
                text = stringResource(R.string.samples_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            return
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            samples.groupBy { it.grade }.toSortedMap().forEach { (grade, items) ->
                item(key = "grade-$grade") {
                    Text(
                        text = stringResource(R.string.samples_grade, grade),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                items(items, key = { it.id }) { sample ->
                    SampleRow(sample = sample, onClick = { onSampleSelected(sample) })
                }
            }
        }
    }
}

@Composable
private fun SampleRow(sample: TextSample, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text = sample.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(
                R.string.samples_item_summary,
                sample.level,
                stringResource(UkrainianPlurals.words(sample.words), sample.words)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Час у форматі мм:сс. Години не потрібні: замір читання — це хвилини. */
private fun formatTime(millis: Long): String {
    val totalSeconds = (if (millis < 0) 0 else millis) / 1000
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/**
 * Дробові числа форматуються завжди українською локаллю: інтерфейс
 * україномовний, і десятковий роздільник не має стрибати між «5,8» і «5.8»
 * залежно від налаштувань телефона.
 */
private fun formatAverage(value: Double): String =
    String.format(Locale.forLanguageTag("uk"), "%.1f", value)

/** Текст із буфера обміну; `null` — у буфері не текст або він порожній. */
private fun readClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = manager?.primaryClip ?: return null

    if (clip.itemCount == 0) {
        return null
    }

    return clip.getItemAt(0).coerceToText(context).toString().takeIf { it.isNotEmpty() }
}

@Preview(showBackground = true, name = "Порожній екран")
@Composable
private fun MainScreenPreviewEmpty() {
    ReadFlowTheme(darkTheme = false) {
        MainScreenContent(state = UiState())
    }
}

@Preview(showBackground = true, name = "Режим читання")
@Composable
private fun MainScreenPreviewReading() {
    val text = "Мама мила раму. Комп'ютер працює, синьо-жовтий прапор майорить."

    ReadFlowTheme(darkTheme = false) {
        MainScreenContent(
            state = UiState(
                text = text,
                countedText = text,
                words = TextStatsCalculator.getWords(text),
                stats = TextStatsCalculator.calculate(text),
                isReadingMode = true,
                mode = MeasurementMode.ERRORS,
                boundaryWordNumber = 6,
                errorWordNumbers = setOf(2),
                elapsedMillis = 45_000
            )
        )
    }
}

@Preview(showBackground = true, name = "З текстом, темна тема")
@Composable
private fun MainScreenPreviewDark() {
    ReadFlowTheme(darkTheme = true) {
        MainScreenContent(
            state = UiState(
                text = "Це файл-заглушка.",
                stats = TextStats(15, 106, 91, 87, 5.8, 3, 2),
                isStatsExpanded = true,
                settings = Settings(grade = 2)
            )
        )
    }
}

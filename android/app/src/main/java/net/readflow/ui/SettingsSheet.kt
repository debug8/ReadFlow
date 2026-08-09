@file:OptIn(ExperimentalMaterial3Api::class)

package net.readflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.readflow.R
import net.readflow.core.NormsCatalog
import net.readflow.model.Settings
import net.readflow.model.ThemeChoice
import java.util.Locale
import kotlin.math.roundToInt

/** Теги налаштувань — окремо, щоб не роздувати [MainScreenTags]. */
object SettingsTags {
    const val SHEET = "sheet_settings"
    const val FONT_SIZE = "slider_font_size"
    const val LINE_SPACING = "slider_line_spacing"
    const val GRADE_ROW = "row_grade"
    const val NORM_HINT = "text_norm_hint"
}

@Composable
fun SettingsSheet(
    settings: Settings,
    norms: NormsCatalog,
    onDismiss: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onGradeChange: (Int) -> Unit,
    onSemesterChange: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SettingsSheetContent(
            settings = settings,
            norms = norms,
            onFontSizeChange = onFontSizeChange,
            onLineSpacingChange = onLineSpacingChange,
            onDurationChange = onDurationChange,
            onThemeChange = onThemeChange,
            onGradeChange = onGradeChange,
            onSemesterChange = onSemesterChange
        )
    }
}

/**
 * Вміст аркуша — окремо від самого аркуша, щоб перевірятися тестом без
 * діалогових вікон.
 *
 * Класи беруться **з довідника норм**, а не з константи в коді: список класів
 * і їхні підписи живуть у `shared/norms.json` (`SPEC.md`, 4.9), і зашитий тут
 * «1–4 клас» розійшовся б із довідником при першій же правці норм.
 */
@Composable
fun SettingsSheetContent(
    settings: Settings,
    norms: NormsCatalog,
    onFontSizeChange: (Int) -> Unit = {},
    onLineSpacingChange: (Float) -> Unit = {},
    onDurationChange: (Int) -> Unit = {},
    onThemeChange: (ThemeChoice) -> Unit = {},
    onGradeChange: (Int) -> Unit = {},
    onSemesterChange: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .testTag(SettingsTags.SHEET),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        SettingLabel(
            stringResource(
                R.string.settings_font_size_value,
                settings.fontSizeSp
            )
        )
        Slider(
            value = settings.fontSizeSp.toFloat(),
            onValueChange = { onFontSizeChange(it.roundToInt()) },
            valueRange = Settings.MIN_FONT_SIZE_SP.toFloat()..Settings.MAX_FONT_SIZE_SP.toFloat(),
            steps = Settings.MAX_FONT_SIZE_SP - Settings.MIN_FONT_SIZE_SP - 1,
            modifier = Modifier.testTag(SettingsTags.FONT_SIZE)
        )

        SettingLabel(
            stringResource(
                R.string.settings_line_spacing_value,
                formatSpacing(settings.lineSpacing)
            )
        )
        Slider(
            value = settings.lineSpacing,
            // Крок 0.1: дрібніше око не бачить, а повзунок стає нервовим.
            onValueChange = { onLineSpacingChange((it * 10).roundToInt() / 10f) },
            valueRange = Settings.MIN_LINE_SPACING..Settings.MAX_LINE_SPACING,
            steps = 11,
            modifier = Modifier.testTag(SettingsTags.LINE_SPACING)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingLabel(stringResource(R.string.settings_duration))
        ChipRow(
            options = UiState.DURATION_CHOICES,
            selected = settings.durationSeconds,
            label = { stringResource(R.string.duration_seconds, it) },
            onSelect = onDurationChange
        )

        SettingLabel(stringResource(R.string.settings_theme))
        ChipRow(
            options = ThemeChoice.entries.toList(),
            selected = settings.theme,
            label = { stringResource(it.labelRes()) },
            onSelect = onThemeChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingLabel(stringResource(R.string.settings_grade))

        if (norms.isEmpty) {
            // Довідник не прочитався — клас обирати нема з чого, і це не аварія:
            // замір працює як раніше, просто без оцінки за нормою.
            Text(
                text = stringResource(R.string.settings_norms_missing),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(SettingsTags.NORM_HINT)
            )
        } else {
            ChipRow(
                options = listOf(Settings.NO_GRADE) + norms.grades.map { it.grade },
                selected = settings.grade,
                label = { grade ->
                    if (grade == Settings.NO_GRADE) {
                        stringResource(R.string.settings_grade_none)
                    } else {
                        norms.grades.first { it.grade == grade }.label
                    }
                },
                onSelect = onGradeChange,
                modifier = Modifier.testTag(SettingsTags.GRADE_ROW)
            )

            if (settings.grade != Settings.NO_GRADE) {
                SettingLabel(stringResource(R.string.settings_semester))
                ChipRow(
                    options = listOf(1, 2),
                    selected = settings.semester,
                    label = { stringResource(R.string.settings_semester_value, it) },
                    onSelect = onSemesterChange
                )

                val norm = norms.find(settings.grade, settings.semester)

                Text(
                    text = if (norm == null) {
                        stringResource(R.string.settings_norm_unknown)
                    } else {
                        stringResource(R.string.settings_norm_range, norm.min, norm.max)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag(SettingsTags.NORM_HINT)
                )
            }

            if (norms.note.isNotEmpty()) {
                Text(
                    text = norms.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (norms.source.isNotEmpty()) {
                Text(
                    text = norms.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** Ряд чіпів вибору одного значення з кількох. */
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(text = label(option), maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

private fun ThemeChoice.labelRes(): Int = when (this) {
    ThemeChoice.SYSTEM -> R.string.theme_system
    ThemeChoice.LIGHT -> R.string.theme_light
    ThemeChoice.DARK -> R.string.theme_dark
}

private fun formatSpacing(value: Float): String =
    String.format(Locale.forLanguageTag("uk"), "%.1f", value)

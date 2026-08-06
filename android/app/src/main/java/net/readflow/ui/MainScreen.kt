package net.readflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.readflow.R
import net.readflow.ui.theme.ReadFlowTheme

/** Теги для тестів: екран перевіряється на JVM через Robolectric, без емулятора. */
object MainScreenTags {
    const val TEXT_ZONE = "zone_text"
    const val STATS_ROW = "zone_stats"
    const val CONTROL_PANEL = "zone_controls"
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MainScreenContent(
        state = state,
        onTextChange = viewModel::onTextChange
    )
}

/**
 * Розмітка екрана без ViewModel — щоб її можна було і показати в Preview,
 * і перевірити тестом, передавши потрібний стан напряму.
 *
 * Три зони згори вниз: текст займає весь вільний простір, статистика —
 * вузький рядок під ним, керування — внизу, у зоні великого пальця.
 */
@Composable
fun MainScreenContent(
    state: UiState,
    onTextChange: (String) -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TextZone(
                text = state.text,
                onTextChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(MainScreenTags.TEXT_ZONE)
            )

            HorizontalDivider()

            StatsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.STATS_ROW)
            )

            HorizontalDivider()

            ControlPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.CONTROL_PANEL)
            )
        }
    }
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
        modifier = modifier.padding(16.dp),
        placeholder = { Text(stringResource(R.string.text_placeholder)) },
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

/**
 * Компактний рядок статистики. Значення заповнюються в Задачі 3,
 * коли з'явиться `TextStatsCalculator`; поки що прочерки.
 */
@Composable
private fun StatsRow(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatCell(R.string.stat_words)
            StatCell(R.string.stat_chars)
            StatCell(R.string.stat_letters)
            StatCell(R.string.stat_avg_word_length)
        }
    }
}

@Composable
private fun StatCell(labelRes: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.stat_empty_value),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Нижня панель керування: великий лічильник і кнопка Старт.
 * Логіка таймера — Задача 5; тут поки заготовка потрібного розміру,
 * щоб було видно, скільки місця вона займає в зоні великого пальця.
 */
@Composable
private fun ControlPanel(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.timer_zero),
                style = MaterialTheme.typography.displaySmall
            )

            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_start),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Світла тема")
@Composable
private fun MainScreenPreviewLight() {
    ReadFlowTheme(darkTheme = false) {
        MainScreenContent(state = UiState(), onTextChange = {})
    }
}

@Preview(showBackground = true, name = "Темна тема")
@Composable
private fun MainScreenPreviewDark() {
    ReadFlowTheme(darkTheme = true) {
        MainScreenContent(state = UiState(), onTextChange = {})
    }
}

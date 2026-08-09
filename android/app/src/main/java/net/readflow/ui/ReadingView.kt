package net.readflow.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import net.readflow.R
import net.readflow.model.WordMark
import net.readflow.model.WordToken
import kotlin.math.roundToInt

/** Скільки тримається підказка «Слово №N», мс. */
private const val WORD_TOOLTIP_MS = 2000L

/**
 * Режим читання: той самий текст, але не редагується, зате реагує на тап по
 * слову.
 *
 * Короткий тап віддає номер слова назовні (Задачі 5–6 зроблять із нього межу
 * читання й позначку помилки), довгий — показує `Слово №N` над самим словом.
 *
 * @param text текст, до якого належать [words]; у режимі читання він
 *   відстає від поля вводу на дебаунс — і саме тому передається окремо,
 *   а не береться з `UiState.text`: індекси слів мусять збігатися з рядком.
 */
@Composable
fun ReadingView(
    text: String,
    words: List<WordToken>,
    modifier: Modifier = Modifier,
    onWordTap: (Int) -> Unit = {},
    markOf: (WordToken) -> WordMark = { WordMark.NONE }
) {
    val styles = WordStyles(
        boundary = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        ),
        error = SpanStyle(
            color = MaterialTheme.colorScheme.onErrorContainer,
            background = MaterialTheme.colorScheme.errorContainer
        )
    )

    // Текст будується один раз на зміну вмісту чи позначок, а не на кожен кадр:
    // на 3000 слів побудова помітно дорожча за перемальовування.
    val annotated = remember(text, words, styles, markOf) {
        buildReadingText(text, words, styles, markOf)
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var tooltipWord by remember { mutableStateOf<WordToken?>(null) }

    // Підказка зникає сама: окремої кнопки «закрити» на телефоні не тримають.
    LaunchedEffect(tooltipWord) {
        if (tooltipWord != null) {
            delay(WORD_TOOLTIP_MS)
            tooltipWord = null
        }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Box {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.READING_TEXT)
                    .pointerInput(words) {
                        detectTapGestures(
                            onTap = { position ->
                                val index = wordIndexAt(words, offsetAt(layout, position))

                                if (index >= 0) {
                                    tooltipWord = null
                                    onWordTap(words[index].number)
                                }
                            },
                            onLongPress = { position ->
                                val index = wordIndexAt(words, offsetAt(layout, position))
                                tooltipWord = if (index >= 0) words[index] else null
                            }
                        )
                    },
                onTextLayout = { layout = it }
            )

            val word = tooltipWord
            val measured = layout

            if (word != null && measured != null) {
                WordNumberTooltip(
                    number = word.number,
                    anchor = measured.tooltipAnchor(word),
                    onDismiss = { tooltipWord = null }
                )
            }
        }
    }
}

/** Спливаюча підказка з порядковим номером слова. */
@Composable
private fun WordNumberTooltip(number: Int, anchor: IntOffset, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.TopStart,
        offset = anchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp
        ) {
            Text(
                text = stringResource(R.string.word_number, number),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .testTag(MainScreenTags.WORD_TOOLTIP)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Точка, у якій показати підказку: трохи вище й лівіше початку слова.
 * Висота бульбашки наперед невідома, тому береться фіксований відступ —
 * помилка на кілька пікселів тут нічого не значить.
 */
@Composable
private fun TextLayoutResult.tooltipAnchor(word: WordToken): IntOffset {
    val lift = with(LocalDensity.current) { 40.dp.toPx() }
    val box = getBoundingBox(word.start.coerceIn(0, layoutInput.text.length - 1))

    return IntOffset(box.left.roundToInt(), (box.top - lift).roundToInt())
}

/** Символ під пальцем; `-1`, якщо текст ще не розкладений. */
private fun offsetAt(layout: TextLayoutResult?, position: Offset): Int =
    layout?.getOffsetForPosition(position) ?: -1

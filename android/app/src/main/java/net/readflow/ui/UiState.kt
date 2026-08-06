package net.readflow.ui

import net.readflow.model.TextSample
import net.readflow.model.TextStats

/**
 * Стан головного екрана — один незмінний знімок.
 *
 * Усе, що бачить користувач, приходить звідси й нізвідки більше: так стан
 * переживає поворот екрана й не розповзається по компонентах. Кожне поле має
 * значення за замовчуванням, щоб `UiState()` завжди був валідним.
 */
data class UiState(

    /** Текст, який ввів або вставив учитель. Порожній — стан «нічого не введено». */
    val text: String = "",

    /** Статистика тексту. Оновлюється з дебаунсом, тому відстає від [text] на мить. */
    val stats: TextStats = TextStats.Empty,

    /** Чи розгорнутий рядок статистики в повний список. */
    val isStatsExpanded: Boolean = false,

    /** Доступні тексти-зразки з `shared/samples/`. */
    val samples: List<TextSample> = emptyList(),

    /** Чи показаний нижній аркуш вибору зразка. */
    val isSampleSheetVisible: Boolean = false
) {
    /** Порожній екран: підказка й дві великі кнопки замість статистики. */
    val isEmpty: Boolean get() = text.isEmpty()
}

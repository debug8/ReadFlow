package net.readflow.ui

import net.readflow.model.TextSample
import net.readflow.model.TextStats
import net.readflow.model.WordToken

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
    val isSampleSheetVisible: Boolean = false,

    /** Чи показане підтвердження очищення. У стані, а не в композиції, — щоб переживало поворот. */
    val isClearConfirmVisible: Boolean = false,

    /**
     * Текст, до якого належать [stats] і [words].
     *
     * Це не те саме, що [text]: поле вводу оновлюється миттєво, а розбір —
     * з дебаунсом. Режим читання малюється саме звідси, бо [words] тримають
     * індекси символів, і на «свіжішому» рядку вони показували б не ті слова.
     */
    val countedText: String = "",

    /** Слова з їхніми межами — для рендеру й нумерації в режимі читання. */
    val words: List<WordToken> = emptyList(),

    /** Режим читання замість поля вводу. */
    val isReadingMode: Boolean = false,

    /**
     * Номер слова, по якому востаннє тапнули (від 1), або `null`.
     * У Задачі 6 із нього стане межа читання; поки що це лише відлуння тапа.
     */
    val tappedWordNumber: Int? = null
) {
    /** Порожній екран: підказка й дві великі кнопки замість статистики. */
    val isEmpty: Boolean get() = text.isEmpty()
}

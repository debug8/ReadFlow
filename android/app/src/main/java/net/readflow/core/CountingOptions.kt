package net.readflow.core

/**
 * Спосіб підрахунку абзаців. Обирає вчитель у налаштуваннях:
 * одного правила не вистачає, бо текст із Word і текст із `.txt`
 * розділені по-різному (див. `SPEC.md`, 4.6).
 */
enum class ParagraphMode {

    /** Абзац — кожен рядок, у якому є хоч один непробільний символ. «А\nБ\n\nВ» = 3. */
    NON_EMPTY_LINES,

    /** Абзац — блок сусідніх непорожніх рядків. «А\nБ\n\nВ» = 2. */
    BLANK_LINE_SEPARATED
}

/**
 * Параметри підрахунку, які може змінювати користувач.
 * Усе інше в [TextStatsCalculator] — жорсткі правила зі специфікації.
 */
data class CountingOptions(
    val paragraphs: ParagraphMode = ParagraphMode.NON_EMPTY_LINES
) {
    companion object {
        /** Значення за замовчуванням. Мусить збігатися з десктопною версією. */
        val Default = CountingOptions()
    }
}

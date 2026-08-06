namespace ReadFlow.Core
{
    /// <summary>
    /// Спосіб підрахунку абзаців. Обирає вчитель у панелі налаштувань:
    /// одного правила не вистачає, бо текст із Word і текст із `.txt`
    /// розділені по-різному (див. специфікацію, 4.6).
    /// </summary>
    public enum ParagraphMode
    {
        /// <summary>Абзац — кожен рядок, у якому є хоч один непробільний символ. «А\nБ\n\nВ» = 3.</summary>
        NonEmptyLines = 0,

        /// <summary>Абзац — блок сусідніх непорожніх рядків. «А\nБ\n\nВ» = 2.</summary>
        BlankLineSeparated = 1
    }

    /// <summary>
    /// Параметри підрахунку, які може змінювати користувач.
    /// Усе інше в <see cref="TextStatsCalculator"/> — жорсткі правила зі специфікації.
    /// </summary>
    public class CountingOptions
    {
        /// <summary>Значення за замовчуванням. Мусить збігатися з Android-версією.</summary>
        public static readonly CountingOptions Default = new CountingOptions();

        public CountingOptions()
        {
            Paragraphs = ParagraphMode.NonEmptyLines;
        }

        public ParagraphMode Paragraphs { get; set; }
    }
}

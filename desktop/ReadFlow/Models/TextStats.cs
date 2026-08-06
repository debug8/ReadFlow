using System.Globalization;

namespace ReadFlow.Models
{
    /// <summary>
    /// Незмінний знімок статистики тексту. Створюється лише
    /// через <c>TextStatsCalculator.Calculate</c>.
    /// </summary>
    public class TextStats
    {
        /// <summary>Статистика порожнього тексту: усюди нулі.</summary>
        public static readonly TextStats Empty = new TextStats(0, 0, 0, 0, 0, 0, 0);

        public TextStats(
            int wordCount,
            int charCount,
            int charCountNoSpaces,
            int letterCount,
            double averageWordLength,
            int sentenceCount,
            int paragraphCount)
        {
            WordCount = wordCount;
            CharCount = charCount;
            CharCountNoSpaces = charCountNoSpaces;
            LetterCount = letterCount;
            AverageWordLength = averageWordLength;
            SentenceCount = sentenceCount;
            ParagraphCount = paragraphCount;
        }

        /// <summary>Кількість слів.</summary>
        public int WordCount { get; private set; }

        /// <summary>Знаки разом із пробілами. Перенос рядка — один знак.</summary>
        public int CharCount { get; private set; }

        /// <summary>Знаки без пробілів, табуляцій і переносів.</summary>
        public int CharCountNoSpaces { get; private set; }

        /// <summary>Букви: Unicode-літери та цифри (див. специфікацію, 4.1).</summary>
        public int LetterCount { get; private set; }

        /// <summary>Середня довжина слова в буквах, округлена до 0.1.</summary>
        public double AverageWordLength { get; private set; }

        /// <summary>Кількість речень (наближено, за роздільниками <c>. ! ? …</c>).</summary>
        public int SentenceCount { get; private set; }

        /// <summary>Кількість абзаців за обраним режимом.</summary>
        public int ParagraphCount { get; private set; }

        public override string ToString()
        {
            return string.Format(
                CultureInfo.InvariantCulture,
                "слів={0}, знаків={1} ({2} без пробілів), букв={3}, сер.довжина={4}, речень={5}, абзаців={6}",
                WordCount, CharCount, CharCountNoSpaces, LetterCount,
                AverageWordLength, SentenceCount, ParagraphCount);
        }
    }
}

using System;

namespace ReadFlow.Core
{
    /// <summary>
    /// Формули швидкості читання (специфікація, 4.7).
    ///
    /// Чисті функції без стану й без залежностей від WPF — те саме має бути
    /// буквально перенесене в Android-версію.
    /// </summary>
    public static class SpeedCalculator
    {
        private const int SecondsPerMinute = 60;

        /// <summary>Слів за хвилину.</summary>
        public static int WordsPerMinute(int wordsRead, decimal seconds)
        {
            return PerMinute(wordsRead, seconds);
        }

        /// <summary>
        /// Знаків за хвилину. Знаки — без пробілів: пробіли не вимовляються.
        /// </summary>
        public static int CharsPerMinute(int charsRead, decimal seconds)
        {
            return PerMinute(charsRead, seconds);
        }

        /// <summary>
        /// Скільки приблизно хвилин займе текст на заданій швидкості.
        ///
        /// Зворотна задача до <see cref="WordsPerMinute"/>: потрібна, щоб біля
        /// зразка написати «≈ 2 хв» для обраного класу. Округлення — «від нуля»
        /// на точному дробі, як і скрізь у розділі 4, і не менше однієї хвилини:
        /// «≈ 0 хв» не сказало б учителю нічого.
        /// </summary>
        public static int MinutesToRead(int words, int wordsPerMinute)
        {
            if (words <= 0 || wordsPerMinute <= 0)
            {
                return 0;
            }

            var minutes = (int)Math.Round(
                (decimal)words / wordsPerMinute, 0, MidpointRounding.AwayFromZero);

            return minutes < 1 ? 1 : minutes;
        }

        private static int PerMinute(int amount, decimal seconds)
        {
            if (seconds <= 0m || amount <= 0)
            {
                return 0;
            }

            // Ділення в decimal, а не в double: 45 слів за 120 с — це рівно 22.5,
            // справжня середина. На double результат залежав би від платформи
            // (та сама пастка, що й у середній довжині слова, див. 4.4).
            var perMinute = amount * (decimal)SecondsPerMinute / seconds;

            return (int)Math.Round(perMinute, 0, MidpointRounding.AwayFromZero);
        }
    }
}

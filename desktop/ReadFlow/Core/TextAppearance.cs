using System.Windows;

namespace ReadFlow.Core
{
    /// <summary>
    /// Розмір шрифту й міжрядковий інтервал тексту.
    ///
    /// Значення за замовчуванням задає тема (ключі <c>BaseFontSize</c> і
    /// <c>TextLineHeight</c>), а вибір учителя перекриває їх. Механізм — власний
    /// ключ у <c>Application.Current.Resources</c>: WPF шукає спершу серед власних
    /// ключів словника й лише потім у <c>MergedDictionaries</c>, де лежить тема.
    /// Тобто перекриття виграє, а <c>DynamicResource</c> у розмітці оновлюється сам.
    ///
    /// <see cref="UseThemeValue"/> означає «вчитель повзунка не чіпав» — тоді
    /// перекриття знімається й діє значення теми. Це не формальність: тема
    /// «Високий контраст» навмисно має більший шрифт, і вмикають її саме тоді,
    /// коли зору бракує. Якби повзунок перекривав тему завжди, вона втратила б сенс.
    /// </summary>
    public static class TextAppearance
    {
        public const string FontSizeKey = "BaseFontSize";
        public const string LineHeightKey = "TextLineHeight";

        /// <summary>Ознака «брати значення з теми».</summary>
        public const double UseThemeValue = 0d;

        public static void ApplyFontSize(double value)
        {
            Apply(FontSizeKey, value);
        }

        public static void ApplyLineHeight(double value)
        {
            Apply(LineHeightKey, value);
        }

        /// <summary>
        /// Значення, яке зараз діє: перекриття, якщо воно є, інакше значення теми.
        /// </summary>
        public static double GetEffective(string key)
        {
            var app = Application.Current;
            if (app == null)
            {
                return UseThemeValue;
            }

            var value = app.TryFindResource(key);
            return value is double ? (double)value : UseThemeValue;
        }

        private static void Apply(string key, double value)
        {
            var app = Application.Current;
            if (app == null)
            {
                return;
            }

            if (value <= UseThemeValue)
            {
                // Прибираємо власний ключ — і знову «просвічує» тема.
                app.Resources.Remove(key);
            }
            else
            {
                app.Resources[key] = value;
            }
        }
    }
}

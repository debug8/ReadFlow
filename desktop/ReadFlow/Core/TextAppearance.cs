using System.Windows;
using System.Windows.Media;

namespace ReadFlow.Core
{
    /// <summary>
    /// Розмір шрифту, міжрядковий інтервал і гарнітура тексту.
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
        public const string FontFamilyKey = "TextFontFamily";

        /// <summary>Ознака «брати значення з теми».</summary>
        public const double UseThemeValue = 0d;

        /// <summary>
        /// Гарнітура «зручного для читання» режиму.
        ///
        /// Verdana з запасними варіантами: широкі літери й великий проміжок між
        /// ними, є на всіх Windows від 7, і нічого не треба вшивати в збірку.
        /// Спеціальні «дислексичні» шрифти сюди свідомо не беремо — дослідження
        /// не показують від них користі, а одне (Wery &amp; Diliberto, 2017) дало
        /// навіть гірші швидкість і точність, ніж звичайний Arial.
        /// </summary>
        public static readonly FontFamily ReadingFontFamily =
            new FontFamily("Verdana, Tahoma, Segoe UI, Global User Interface");

        /// <summary>
        /// Увімкнути або вимкнути гарнітуру, зручнішу для читання.
        ///
        /// Ключ задається завжди, навіть коли режим вимкнено. Прибрати його було б
        /// природніше, але тоді <c>DynamicResource</c> лишався б нерозвʼязаним, а це
        /// вже інша поведінка WPF, ніж «діє значення теми» у розмірів шрифту.
        /// </summary>
        public static void ApplyReadingFont(bool enabled)
        {
            var app = Application.Current;
            if (app == null)
            {
                return;
            }

            app.Resources[FontFamilyKey] = enabled ? ReadingFontFamily : SystemFonts.MessageFontFamily;
        }

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

using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Packaging;
using System.Linq;
using System.Reflection;
using System.Resources;
using System.Windows;
using ReadFlow.Models;
using ReadFlow.Properties;

namespace ReadFlow.Core
{
    /// <summary>
    /// Керує темами оформлення.
    ///
    /// Список тем не захардкоджений: він збирається з файлів <c>Themes/*.xaml</c>,
    /// вкладених у збірку як ресурси. Щоб додати тему, достатньо покласти новий
    /// <c>.xaml</c> у <c>Themes/</c> (Build Action = Page) — код не змінюється.
    ///
    /// <see cref="Apply"/> підміняє рівно ОДИН словник у
    /// <c>Application.Current.Resources.MergedDictionaries</c> — той, що містить
    /// ключ <see cref="ThemeMarkerKey"/>. Решта словників (рядки інтерфейсу тощо)
    /// лишаються на місці.
    /// </summary>
    public static class ThemeManager
    {
        /// <summary>Ключ, за яким словник упізнається як тема (і водночас — її назва у списку).</summary>
        public const string ThemeMarkerKey = "ThemeDisplayName";

        /// <summary>Тема, яка застосовується, якщо збережена відсутня або зникла.</summary>
        public const string DefaultThemeName = "light";

        private const string ThemeResourceFolder = "themes/";
        private const string BamlExtension = ".baml";

        private static IReadOnlyList<ThemeDescriptor> _availableThemes;

        /// <summary>Назва застосованої зараз теми, або <c>null</c>, якщо тему ще не застосовано.</summary>
        public static string CurrentThemeName { get; private set; }

        /// <summary>Усі знайдені теми, відсортовані за назвою для користувача.</summary>
        public static IReadOnlyList<ThemeDescriptor> AvailableThemes
        {
            get { return _availableThemes ?? (_availableThemes = Discover()); }
        }

        /// <summary>
        /// Застосувати тему, збережену в налаштуваннях. Викликається при старті застосунку.
        /// </summary>
        public static void ApplySaved()
        {
            string saved = null;
            try
            {
                saved = Settings.Default.Theme;
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося прочитати збережену тему — " + ex.Message);
            }

            Apply(string.IsNullOrWhiteSpace(saved) ? DefaultThemeName : saved);
        }

        /// <summary>
        /// Застосувати тему за технічною назвою (імʼям файлу без розширення, регістр не важливий).
        /// Якщо теми немає — відкат на <see cref="DefaultThemeName"/>, потім на першу наявну.
        /// </summary>
        public static void Apply(string themeName)
        {
            var app = Application.Current;
            if (app == null)
            {
                return;
            }

            var theme = FindByName(themeName)
                        ?? FindByName(DefaultThemeName)
                        ?? AvailableThemes.FirstOrDefault();

            if (theme == null)
            {
                Debug.WriteLine("ReadFlow: жодної теми не знайдено — інтерфейс лишається з типовими кольорами.");
                return;
            }

            ResourceDictionary dictionary;
            try
            {
                dictionary = new ResourceDictionary { Source = theme.Source };
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося завантажити тему '" + theme.Name + "' — " + ex.Message);
                return;
            }

            var merged = app.Resources.MergedDictionaries;
            var index = IndexOfThemeDictionary(merged);

            if (index >= 0)
            {
                merged[index] = dictionary;
            }
            else
            {
                merged.Add(dictionary);
            }

            CurrentThemeName = theme.Name;
            SaveThemeSetting(theme.Name);
        }

        /// <summary>Знайти тему за технічною назвою.</summary>
        public static ThemeDescriptor FindByName(string themeName)
        {
            if (string.IsNullOrWhiteSpace(themeName))
            {
                return null;
            }

            return AvailableThemes.FirstOrDefault(
                t => string.Equals(t.Name, themeName, StringComparison.OrdinalIgnoreCase));
        }

        private static int IndexOfThemeDictionary(IList<ResourceDictionary> merged)
        {
            for (var i = 0; i < merged.Count; i++)
            {
                if (merged[i] != null && merged[i].Contains(ThemeMarkerKey))
                {
                    return i;
                }
            }

            return -1;
        }

        private static void SaveThemeSetting(string themeName)
        {
            try
            {
                // Без цієї перевірки user.config перезаписувався б при кожному запуску:
                // ApplySaved() читає збережену тему й одразу писав би її назад.
                if (string.Equals(Settings.Default.Theme, themeName, StringComparison.Ordinal))
                {
                    return;
                }

                Settings.Default.Theme = themeName;
                Settings.Default.Save();
            }
            catch (Exception ex)
            {
                // Немає прав на запис user.config — тема просто не запамʼятається,
                // але застосунок має працювати далі.
                Debug.WriteLine("ReadFlow: не вдалося зберегти вибрану тему — " + ex.Message);
            }
        }

        private static IReadOnlyList<ThemeDescriptor> Discover()
        {
            var themes = new List<ThemeDescriptor>();

            EnsurePackSchemeRegistered();

            foreach (var name in EnumerateThemeNames())
            {
                try
                {
                    // Побудова Uri теж усередині try: якщо схема pack недоступна,
                    // це має означати «тем немає», а не падіння всієї ViewModel.
                    var source = BuildSourceUri(name);
                    var dictionary = new ResourceDictionary { Source = source };
                    var displayName = dictionary.Contains(ThemeMarkerKey)
                        ? dictionary[ThemeMarkerKey] as string
                        : null;

                    themes.Add(new ThemeDescriptor(
                        name,
                        string.IsNullOrWhiteSpace(displayName) ? name : displayName,
                        source));
                }
                catch (Exception ex)
                {
                    // Пошкоджений файл теми не має валити застосунок — просто не показуємо її.
                    Debug.WriteLine("ReadFlow: тему '" + name + "' пропущено — " + ex.Message);
                }
            }

            return themes
                .OrderBy(t => t.DisplayName, StringComparer.CurrentCulture)
                .ToList()
                .AsReadOnly();
        }

        /// <summary>
        /// Переконатися, що схему <c>pack://</c> зареєстровано в <see cref="UriParser"/>.
        ///
        /// У застосунку це робить WPF під час старту, тому в бою проблеми не видно.
        /// Але в юніт-тестах жоден WPF-компонент не ініціалізується, схема лишається
        /// невідомою — і <c>new Uri("pack://application:,,,/...")</c> падає з
        /// <see cref="UriFormatException"/>, бо <c>,,,</c> не є припустимим authority
        /// для звичайного URI.
        ///
        /// Реєстрацію виконує статичний конструктор <see cref="PackUriHelper"/>, тож
        /// достатньо звернутися до будь-якого його члена.
        /// </summary>
        private static void EnsurePackSchemeRegistered()
        {
            try
            {
                if (!UriParser.IsKnownScheme(PackUriHelper.UriSchemePack))
                {
                    Debug.WriteLine("ReadFlow: схему pack не зареєстровано, теми будуть недоступні.");
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося зареєструвати схему pack — " + ex.Message);
            }
        }

        /// <summary>
        /// Перелічити назви тем із таблиці ресурсів збірки (<c>ReadFlow.g.resources</c>).
        /// Шляхи там зберігаються у нижньому регістрі: <c>themes/light.baml</c>.
        /// </summary>
        private static List<string> EnumerateThemeNames()
        {
            var names = new List<string>();
            var assembly = Assembly.GetExecutingAssembly();
            var resourceName = assembly.GetName().Name + ".g.resources";

            try
            {
                using (var stream = assembly.GetManifestResourceStream(resourceName))
                {
                    if (stream == null)
                    {
                        return names;
                    }

                    using (var reader = new ResourceReader(stream))
                    {
                        foreach (DictionaryEntry entry in reader)
                        {
                            var path = entry.Key as string;
                            if (path == null)
                            {
                                continue;
                            }

                            if (path.StartsWith(ThemeResourceFolder, StringComparison.OrdinalIgnoreCase) &&
                                path.EndsWith(BamlExtension, StringComparison.OrdinalIgnoreCase))
                            {
                                names.Add(Path.GetFileNameWithoutExtension(path));
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося прочитати ресурси збірки — " + ex.Message);
            }

            return names;
        }

        private static Uri BuildSourceUri(string themeName)
        {
            var assemblyName = Assembly.GetExecutingAssembly().GetName().Name;
            return new Uri(
                "pack://application:,,,/" + assemblyName + ";component/Themes/" + themeName + ".xaml",
                UriKind.Absolute);
        }
    }
}

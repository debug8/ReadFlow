using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using System.Resources;
using System.Text;
using System.Windows;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тести авто-виявлення тем.
    ///
    /// Раніше цей шлях коду не був покритий, і помилка проявилася лише через
    /// конструктор <c>MainViewModel</c>: побудова <c>pack://</c>-адреси падала,
    /// бо в тестовому хості WPF не реєструє схему <c>pack</c>. Тепер механізм
    /// перевіряється напряму.
    ///
    /// <see cref="ThemeManager.Apply"/> тут не викликається: він потребує живого
    /// <c>Application.Current</c>, а перемикання теми наживо перевіряється руками.
    /// </summary>
    [TestClass]
    public class ThemeManagerTests
    {
        [TestMethod]
        public void AvailableThemes_DiscoversAllThemeFiles()
        {
            StaRunner.Run(() =>
            {
                var themes = ThemeManager.AvailableThemes;

                if (themes.Count >= 3)
                {
                    return;
                }

                // ThemeManager навмисно глушить помилки завантаження тем, щоб не валити
                // застосунок у вчителя. Тут це обертається порожнім повідомленням, тому
                // тест повторює ті самі кроки вручну й показує, на якому саме впало.
                Assert.Fail(Diagnose(themes.Count));
            });
        }

        private static string Diagnose(int found)
        {
            var report = new StringBuilder();
            var assembly = typeof(ThemeManager).Assembly;
            var assemblyName = assembly.GetName().Name;

            report.AppendLine("Знайдено тем: " + found + ". Діагностика:");
            report.AppendLine("  збірка з ThemeManager: " + assemblyName);
            report.AppendLine("  схема pack зареєстрована: " + UriParser.IsKnownScheme("pack"));

            try
            {
                var resourceAssembly = Application.ResourceAssembly;
                report.AppendLine("  Application.ResourceAssembly: " +
                                  (resourceAssembly == null ? "null" : resourceAssembly.GetName().Name));
            }
            catch (Exception ex)
            {
                report.AppendLine("  Application.ResourceAssembly: помилка — " + ex.Message);
            }

            var entries = new List<string>();
            try
            {
                using (var stream = assembly.GetManifestResourceStream(assemblyName + ".g.resources"))
                {
                    if (stream == null)
                    {
                        report.AppendLine("  " + assemblyName + ".g.resources: НЕ ЗНАЙДЕНО");
                    }
                    else
                    {
                        using (var reader = new ResourceReader(stream))
                        {
                            foreach (DictionaryEntry entry in reader)
                            {
                                entries.Add(Convert.ToString(entry.Key));
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                report.AppendLine("  читання .g.resources: " + ex.GetType().Name + ": " + ex.Message);
            }

            report.AppendLine("  записів у .g.resources: " + entries.Count);
            report.AppendLine("  з них у themes/: " + string.Join(", ",
                entries.Where(e => e.StartsWith("themes/", StringComparison.OrdinalIgnoreCase))));

            try
            {
                var uri = new Uri(
                    "pack://application:,,,/" + assemblyName + ";component/Themes/Light.xaml",
                    UriKind.Absolute);
                var dictionary = new ResourceDictionary { Source = uri };
                report.AppendLine("  завантаження Light.xaml: успішно, ключів " + dictionary.Count);
            }
            catch (Exception ex)
            {
                report.AppendLine("  завантаження Light.xaml: " + ex.GetType().Name + ": " + ex.Message);
            }

            return report.ToString();
        }

        [TestMethod]
        public void AvailableThemes_HaveUkrainianDisplayNames()
        {
            StaRunner.Run(() =>
            {
                var displayNames = ThemeManager.AvailableThemes.Select(t => t.DisplayName).ToList();

                CollectionAssert.IsSubsetOf(
                    new[] { "Світла", "Темна", "Високий контраст" },
                    displayNames,
                    "Назви беруться з ключа ThemeDisplayName всередині кожної теми. Знайдено: " +
                    string.Join(", ", displayNames));
            });
        }

        [TestMethod]
        public void AvailableThemes_NameNeverFallsBackToFileName()
        {
            StaRunner.Run(() =>
            {
                foreach (var theme in ThemeManager.AvailableThemes)
                {
                    Assert.AreNotEqual(
                        theme.Name,
                        theme.DisplayName,
                        "У темі '" + theme.Name + "' не прочитався ThemeDisplayName — " +
                        "у списку вона показалася б технічною назвою файлу.");
                }
            });
        }

        [TestMethod]
        public void FindByName_IsCaseInsensitive()
        {
            StaRunner.Run(() =>
            {
                Assert.IsNotNull(ThemeManager.FindByName("light"));
                Assert.IsNotNull(ThemeManager.FindByName("Light"));
                Assert.IsNotNull(ThemeManager.FindByName("HIGHCONTRAST"));
            });
        }

        [TestMethod]
        public void FindByName_ReturnsNullForUnknownAndEmpty()
        {
            StaRunner.Run(() =>
            {
                Assert.IsNull(ThemeManager.FindByName("немаєТакої"));
                Assert.IsNull(ThemeManager.FindByName(null));
                Assert.IsNull(ThemeManager.FindByName("   "));
            });
        }

        [TestMethod]
        public void DefaultTheme_Exists()
        {
            StaRunner.Run(() =>
            {
                // Ланцюжок відкатів у Apply() спирається на те, що тема за
                // замовчуванням існує. Якщо light.xaml перейменують — тест впаде.
                Assert.IsNotNull(
                    ThemeManager.FindByName(ThemeManager.DefaultThemeName),
                    "Теми за замовчуванням '" + ThemeManager.DefaultThemeName + "' не існує.");
            });
        }

        [TestMethod]
        public void ThemeSources_ArePackUris()
        {
            StaRunner.Run(() =>
            {
                foreach (var theme in ThemeManager.AvailableThemes)
                {
                    Assert.AreEqual("pack", theme.Source.Scheme, "Тема '" + theme.Name + "'.");
                    Assert.IsTrue(
                        theme.Source.ToString().IndexOf("/Themes/", System.StringComparison.OrdinalIgnoreCase) >= 0,
                        "Адреса теми '" + theme.Name + "' не вказує в Themes/: " + theme.Source);
                }
            });
        }
    }
}

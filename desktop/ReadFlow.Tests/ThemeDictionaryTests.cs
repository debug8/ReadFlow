using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Markup;
using System.Windows.Media;
using System.Xml.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тема, у якій бракує ключа, ламає інтерфейс уже після перемикання — тобто в рантаймі,
    /// у вчителя. Тому набір ключів перевіряється тут, на етапі збірки.
    ///
    /// Тести читають файли тем із дерева вихідників, а не зі зібраної збірки: так вони
    /// бачать і щойно доданий файл теми, який ще забули прописати в csproj.
    /// </summary>
    [TestClass]
    public class ThemeDictionaryTests
    {
        private static readonly XNamespace XamlNs = "http://schemas.microsoft.com/winfx/2006/xaml";

        /// <summary>Ключі зі специфікації, розділ 5. Кожна тема визначає їх усі.</summary>
        private static readonly string[] RequiredKeys =
        {
            "ThemeDisplayName",
            "AppBackgroundBrush",
            "PanelBackgroundBrush",
            "TextBackgroundBrush",
            "TextForegroundBrush",
            "SecondaryForegroundBrush",
            "AccentBrush",
            "BorderBrush",
            "WordHoverBrush",
            "WordBoundaryBrush",
            "WordErrorBrush",
            "LineHighlightBrush",
            "ControlHoverBrush",
            "ControlSelectedBrush",
            "NormBelowBrush",
            "NormWithinBrush",
            "NormAboveBrush",
            "TimerActiveBrush",
            "BaseFontSize",
            "TextLineHeight"
        };

        [TestMethod]
        public void Themes_FolderContainsAtLeastThreeThemes()
        {
            var files = GetThemeFiles();

            CollectionAssert.IsSubsetOf(
                new[] { "Light.xaml", "Dark.xaml", "HighContrast.xaml" },
                files.Select(Path.GetFileName).ToList(),
                "Бракує однієї зі стандартних тем.");
        }

        [TestMethod]
        public void EveryTheme_DefinesAllRequiredKeys()
        {
            foreach (var file in GetThemeFiles())
            {
                var keys = ReadKeys(file);
                var missing = RequiredKeys.Except(keys).ToList();

                Assert.AreEqual(
                    0,
                    missing.Count,
                    "У темі '" + Path.GetFileName(file) + "' бракує ключів: " + string.Join(", ", missing));
            }
        }

        [TestMethod]
        public void AllThemes_HaveIdenticalKeySets()
        {
            var files = GetThemeFiles();
            var reference = files.First();
            var referenceKeys = ReadKeys(reference);

            foreach (var file in files.Skip(1))
            {
                var keys = ReadKeys(file);
                var missing = referenceKeys.Except(keys).ToList();
                var extra = keys.Except(referenceKeys).ToList();

                Assert.AreEqual(
                    0,
                    missing.Count + extra.Count,
                    "Набір ключів '" + Path.GetFileName(file) + "' відрізняється від '" +
                    Path.GetFileName(reference) + "'. Бракує: [" + string.Join(", ", missing) +
                    "], зайві: [" + string.Join(", ", extra) + "].");
            }
        }

        [TestMethod]
        public void EveryTheme_KeysDuplicatedWithinFile_AreAbsent()
        {
            foreach (var file in GetThemeFiles())
            {
                var all = ReadAllKeys(file);
                var duplicates = all.GroupBy(k => k).Where(g => g.Count() > 1).Select(g => g.Key).ToList();

                Assert.AreEqual(
                    0,
                    duplicates.Count,
                    "У темі '" + Path.GetFileName(file) + "' повторюються ключі: " + string.Join(", ", duplicates));
            }
        }

        /// <summary>
        /// Перевірка «тема справді завантажиться»: XAML парситься, а значення мають потрібні типи.
        /// Валідний XML із помилкою в кольорі інші тести не спіймають.
        /// </summary>
        [TestMethod]
        public void EveryTheme_LoadsAndValuesHaveExpectedTypes()
        {
            StaRunner.Run(() =>
            {
                foreach (var file in GetThemeFiles())
                {
                    var name = Path.GetFileName(file);
                    ResourceDictionary dictionary;

                    using (var stream = File.OpenRead(file))
                    {
                        dictionary = XamlReader.Load(stream) as ResourceDictionary;
                    }

                    Assert.IsNotNull(dictionary, "Тема '" + name + "' не є ResourceDictionary.");

                    foreach (var key in RequiredKeys)
                    {
                        Assert.IsTrue(dictionary.Contains(key), "У темі '" + name + "' немає ключа " + key);
                    }

                    Assert.IsInstanceOfType(dictionary["ThemeDisplayName"], typeof(string),
                        "ThemeDisplayName у '" + name + "' має бути рядком.");

                    foreach (var key in RequiredKeys.Where(k => k.EndsWith("Brush", StringComparison.Ordinal)))
                    {
                        Assert.IsInstanceOfType(dictionary[key], typeof(Brush),
                            "Ключ " + key + " у '" + name + "' має бути пензлем.");
                    }

                    foreach (var key in new[] { "BaseFontSize", "TextLineHeight" })
                    {
                        Assert.IsInstanceOfType(dictionary[key], typeof(double),
                            "Ключ " + key + " у '" + name + "' має бути числом.");
                        Assert.IsTrue((double)dictionary[key] > 0,
                            "Ключ " + key + " у '" + name + "' має бути додатним.");
                    }
                }
            });
        }

        private static List<string> ReadKeys(string file)
        {
            return ReadAllKeys(file).Distinct().ToList();
        }

        private static List<string> ReadAllKeys(string file)
        {
            var document = XDocument.Load(file);

            return document.Root
                .Elements()
                .Select(e => (string)e.Attribute(XamlNs + "Key"))
                .Where(k => !string.IsNullOrEmpty(k))
                .ToList();
        }

        private static List<string> GetThemeFiles()
        {
            var folder = FindThemesFolder();
            var files = Directory.GetFiles(folder, "*.xaml").OrderBy(f => f, StringComparer.Ordinal).ToList();

            Assert.AreNotEqual(0, files.Count, "У папці Themes немає жодного файлу теми: " + folder);
            return files;
        }

        private static string FindThemesFolder()
        {
            var directory = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory);

            while (directory != null)
            {
                var candidate = Path.Combine(directory.FullName, "ReadFlow", "Themes");
                if (Directory.Exists(candidate))
                {
                    return candidate;
                }

                directory = directory.Parent;
            }

            Assert.Fail("Не вдалося знайти папку ReadFlow\\Themes від " + AppDomain.CurrentDomain.BaseDirectory);
            return null;
        }
    }
}

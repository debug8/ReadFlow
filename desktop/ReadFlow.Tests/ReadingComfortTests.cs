using System;
using System.IO;
using System.Linq;
using System.Xml.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Зручність читання — Задача 9: підсвітка рядка й гарнітура тексту.
    ///
    /// Ані смуга підсвітки, ані застосування шрифту до ресурсів тут не
    /// перевіряються, і це навмисно. Смуга — арифметика по розкладці
    /// <c>TextBlock</c>, для неї потрібне зібране й виміряне вікно.
    /// А <c>TextAppearance</c> пише в <c>Application.Current.Resources</c>, якого
    /// в тестовому хості немає: створити <c>Application</c> теж не можна, бо
    /// <c>StaRunner</c> дає новий STA-потік на кожен тест, і звернення до чужого
    /// диспетчера впало б.
    ///
    /// Тому перевіряється те, що ламається непомітно: склад гарнітури, стан
    /// перемикачів і те, що вони не тягнуть за собою чужих налаштувань.
    /// </summary>
    [TestClass]
    public class ReadingComfortTests
    {
        [TestMethod]
        public void ReadingFont_HasFallbacksForOlderWindows()
        {
            var source = TextAppearance.ReadingFontFamily.Source;

            // Verdana першою — саме її широкі літери й проміжки нам потрібні.
            StringAssert.StartsWith(source, "Verdana");

            // Далі запасні: якщо Verdana в системі немає, WPF мовчки візьме
            // стандартний шрифт, і вчитель вирішить, що перемикач не працює.
            StringAssert.Contains(source, "Tahoma");
            StringAssert.Contains(source, "Segoe UI");
        }

        [TestMethod]
        public void ReadingFont_TogglesAndPersistsIntent()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { UseReadingFont = false };

                viewModel.UseReadingFont = true;
                Assert.IsTrue(viewModel.UseReadingFont);

                viewModel.UseReadingFont = false;
                Assert.IsFalse(viewModel.UseReadingFont);
            });
        }

        [TestMethod]
        public void ReadingFont_DoesNotTouchSizeOrLineHeight()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();
                viewModel.FontSize = 22;
                viewModel.LineHeight = 34;

                viewModel.UseReadingFont = true;

                // Розмір і гарнітура — різні речі: вчитель може захотіти
                // велику звичайну або дрібну широку.
                Assert.AreEqual(22d, viewModel.FontSize, 0.01);
                Assert.AreEqual(34d, viewModel.LineHeight, 0.01);
                Assert.IsFalse(viewModel.UsesThemeSizes);
            });
        }

        [TestMethod]
        public void ReadingFont_SurvivesThemeSwitch()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { UseReadingFont = true };

                foreach (var theme in viewModel.Themes)
                {
                    viewModel.SelectedTheme = theme;

                    // Гарнітура — вибір учителя, а не частина теми.
                    Assert.IsTrue(
                        viewModel.UseReadingFont,
                        "Тема '" + theme.Name + "' скинула вибір гарнітури.");
                }
            });
        }

        [TestMethod]
        public void LineHighlight_TogglesBothWays()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { ShowLineHighlight = false };

                viewModel.ShowLineHighlight = true;
                Assert.IsTrue(viewModel.ShowLineHighlight);

                viewModel.ShowLineHighlight = false;
                Assert.IsFalse(viewModel.ShowLineHighlight);
            });
        }

        [TestMethod]
        public void ComfortSwitches_AreIndependentOfEachOther()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { ShowLineHighlight = false, UseReadingFont = false };

                viewModel.ShowLineHighlight = true;
                Assert.IsFalse(viewModel.UseReadingFont);

                viewModel.UseReadingFont = true;
                Assert.IsTrue(viewModel.ShowLineHighlight);
            });
        }

        [TestMethod]
        public void ComfortSwitches_AreOffByDefaultInSettings()
        {
            // Обидва — допоміжні. Вчитель, який відкрив застосунок уперше,
            // має побачити звичайний текст без сторонньої підсвітки
            // (специфікація, 3: «стан за замовчуванням зрозумілий»).
            Assert.AreEqual(
                "False",
                DefaultOf("LineHighlight"),
                "Підсвітка рядка має бути вимкнена за замовчуванням.");

            Assert.AreEqual(
                "False",
                DefaultOf("ReadingFont"),
                "Шрифт для читання має бути вимкнений за замовчуванням.");
        }

        /// <summary>
        /// Значення за замовчуванням із <c>Settings.settings</c> у дереві вихідників.
        ///
        /// Саме з файла, а не з атрибутів згенерованого класу: клас <c>internal</c>,
        /// а головне — <c>Settings.Designer.cs</c> і <c>Settings.settings</c> легко
        /// розʼїжджаються, коли редагуєш їх руками замість дизайнера.
        /// </summary>
        private static string DefaultOf(string settingName)
        {
            var file = FindSettingsFile();
            var document = XDocument.Load(file);

            var setting = document.Descendants()
                .FirstOrDefault(e => e.Name.LocalName == "Setting"
                                     && (string)e.Attribute("Name") == settingName);

            Assert.IsNotNull(setting, "У Settings.settings немає налаштування " + settingName + ".");

            var value = setting.Elements().FirstOrDefault(e => e.Name.LocalName == "Value");
            Assert.IsNotNull(value, "У налаштування " + settingName + " немає значення.");

            return value.Value;
        }

        private static string FindSettingsFile()
        {
            var directory = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory);

            while (directory != null)
            {
                var candidate = Path.Combine(directory.FullName, "ReadFlow", "Properties", "Settings.settings");
                if (File.Exists(candidate))
                {
                    return candidate;
                }

                directory = directory.Parent;
            }

            Assert.Fail("Не вдалося знайти Settings.settings від " + AppDomain.CurrentDomain.BaseDirectory);
            return null;
        }
    }
}

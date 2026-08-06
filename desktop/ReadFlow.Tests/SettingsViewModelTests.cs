using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тести налаштувань із лівої панелі.
    ///
    /// Збереження між запусками тут не перевіряється: <c>Properties.Settings</c>
    /// пише в <c>user.config</c> тестового хоста, і такий тест залежав би від
    /// порядку виконання та стану машини. Перевіряється логіка, яку легко зламати:
    /// клампи, сентинел «як у темі» та взаємовиключність режимів A і B.
    /// </summary>
    [TestClass]
    public class SettingsViewModelTests
    {
        [TestMethod]
        public void TimerSeconds_IsClampedToSaneRange()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                viewModel.TimerSeconds = 0;
                Assert.AreEqual(MainViewModel.MinTimerSeconds, viewModel.TimerSeconds);

                viewModel.TimerSeconds = -5;
                Assert.AreEqual(MainViewModel.MinTimerSeconds, viewModel.TimerSeconds);

                viewModel.TimerSeconds = 999999;
                Assert.AreEqual(MainViewModel.MaxTimerSeconds, viewModel.TimerSeconds);

                viewModel.TimerSeconds = 90;
                Assert.AreEqual(90, viewModel.TimerSeconds);
            });
        }

        [TestMethod]
        public void SetTimerSecondsCommand_AppliesPreset()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                viewModel.SetTimerSecondsCommand.Execute("120");

                Assert.AreEqual(120, viewModel.TimerSeconds);
            });
        }

        [TestMethod]
        public void SetTimerSecondsCommand_IgnoresGarbage()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();
                viewModel.TimerSeconds = 60;

                viewModel.SetTimerSecondsCommand.Execute("не число");
                viewModel.SetTimerSecondsCommand.Execute(null);

                Assert.AreEqual(60, viewModel.TimerSeconds);
            });
        }

        [TestMethod]
        public void FontSize_IsClampedToSliderRange()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                viewModel.FontSize = 1000;
                Assert.AreEqual(MainViewModel.MaxFontSize, viewModel.FontSize);

                viewModel.FontSize = 1;
                Assert.AreEqual(MainViewModel.MinFontSize, viewModel.FontSize);
            });
        }

        [TestMethod]
        public void UseThemeSizes_ResetsOverrides()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                viewModel.FontSize = 28;
                viewModel.LineHeight = 44;
                Assert.IsFalse(viewModel.UsesThemeSizes, "Після зсуву повзунка діє вибір учителя.");

                viewModel.UseThemeSizesCommand.Execute(null);

                Assert.IsTrue(viewModel.UsesThemeSizes);
            });
        }

        [TestMethod]
        public void UseThemeSizesCommand_IsDisabledWhenAlreadyUsingTheme()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();
                viewModel.UseThemeSizesCommand.Execute(null);

                Assert.IsFalse(
                    viewModel.UseThemeSizesCommand.CanExecute(null),
                    "Кнопка «Як у темі» не має бути активною, коли розміри вже з теми.");

                viewModel.FontSize = 22;

                Assert.IsTrue(viewModel.UseThemeSizesCommand.CanExecute(null));
            });
        }

        [TestMethod]
        public void MeasurementModes_AreMutuallyExclusive()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                viewModel.IsClickStopMode = true;

                Assert.IsTrue(viewModel.IsClickStopMode);
                Assert.IsFalse(viewModel.IsTimerMode);
                Assert.AreEqual(MeasurementMode.ClickStop, viewModel.MeasurementMode);

                viewModel.IsTimerMode = true;

                Assert.IsFalse(viewModel.IsClickStopMode);
                Assert.IsTrue(viewModel.IsTimerMode);
                Assert.AreEqual(MeasurementMode.Timer, viewModel.MeasurementMode);
            });
        }

        [TestMethod]
        public void MarkErrors_IsIndependentOfMeasurementMode()
        {
            StaRunner.Run(() =>
            {
                // Режим C накладається на будь-який зі способів заміру — учитель
                // має могти відмічати помилки і під час заміру таймером.
                var viewModel = new MainViewModel { MarkErrors = true };

                viewModel.IsTimerMode = true;
                Assert.IsTrue(viewModel.MarkErrors);

                viewModel.IsClickStopMode = true;
                Assert.IsTrue(viewModel.MarkErrors);
            });
        }

        [TestMethod]
        public void ParagraphMode_RecalculatesImmediately()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Text = "А\nБ\n\nВ" };
                viewModel.ParagraphMode = ParagraphMode.NonEmptyLines;
                viewModel.RecalculateNow();
                Assert.AreEqual(3, viewModel.Stats.ParagraphCount);

                // Змінилося правило, а не текст: чекати дебаунсу немає сенсу,
                // числа мають оновитися одразу.
                viewModel.ParagraphMode = ParagraphMode.BlankLineSeparated;

                Assert.AreEqual(2, viewModel.Stats.ParagraphCount);
            });
        }

        [TestMethod]
        public void SettingsPanel_TogglesBothWays()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { IsSettingsExpanded = true };

                viewModel.ToggleSettingsCommand.Execute(null);
                Assert.IsFalse(viewModel.IsSettingsExpanded);

                viewModel.ToggleSettingsCommand.Execute(null);
                Assert.IsTrue(viewModel.IsSettingsExpanded);
            });
        }
    }
}

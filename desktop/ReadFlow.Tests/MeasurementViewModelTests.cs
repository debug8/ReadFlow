using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тести заміру у ViewModel.
    ///
    /// Сам відлік часу тут не перевіряється: він спирається на <c>Stopwatch</c>
    /// і реальний годинник, тож тест на «минула секунда» був би повільним
    /// і нестабільним. Формули покриті окремо в <see cref="SpeedCalculatorTests"/>,
    /// а тут — стан: що з чого вмикається й що обнуляється.
    ///
    /// Спосіб заміру й відмітка помилок задаються явно. Обидва зберігаються в
    /// <c>Properties.Settings</c>, а <c>Settings.Default</c> — один обʼєкт на весь
    /// тестовий процес: без цього тест залежав би і від минулого запуску, і від
    /// того, який тест відпрацював перед ним. Межа читання й помилки —
    /// у <see cref="ReadingBoundaryTests"/> та <see cref="ReadingErrorsTests"/>.
    /// </summary>
    [TestClass]
    public class MeasurementViewModelTests
    {
        private static MainViewModel TimerModeViewModel(string text)
        {
            return new MainViewModel
            {
                MeasurementMode = MeasurementMode.Timer,
                MarkErrors = false,
                Text = text
            };
        }

        [TestMethod]
        public void NewViewModel_HasNoResultAndZeroClock()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                Assert.IsFalse(viewModel.IsMeasuring);
                Assert.IsFalse(viewModel.HasResult);
                Assert.AreEqual("00:00", viewModel.ElapsedDisplay);
            });
        }

        [TestMethod]
        public void ToggleMeasurement_StartsAndStops()
        {
            StaRunner.Run(() =>
            {
                var viewModel = TimerModeViewModel("Мама мила раму");

                viewModel.ToggleMeasurementCommand.Execute(null);
                Assert.IsTrue(viewModel.IsMeasuring);

                viewModel.ToggleMeasurementCommand.Execute(null);
                Assert.IsFalse(viewModel.IsMeasuring);
            });
        }

        [TestMethod]
        public void StartingMeasurement_FlushesPendingDebounce()
        {
            StaRunner.Run(() =>
            {
                // Без дорахунку замір рахувався б за кількістю слів
                // попереднього тексту: дебаунс ще не спрацював.
                var viewModel = TimerModeViewModel("Мама мила раму");
                Assert.AreEqual(0, viewModel.WordsRead);

                viewModel.ToggleMeasurementCommand.Execute(null);

                Assert.AreEqual(3, viewModel.WordsRead);
            });
        }

        [TestMethod]
        public void Reset_ClearsClockAndResult()
        {
            StaRunner.Run(() =>
            {
                var viewModel = TimerModeViewModel("Мама мила раму");
                viewModel.ToggleMeasurementCommand.Execute(null);
                viewModel.ToggleMeasurementCommand.Execute(null);

                viewModel.ResetMeasurementCommand.Execute(null);

                Assert.IsFalse(viewModel.IsMeasuring);
                Assert.IsFalse(viewModel.HasResult);
                Assert.AreEqual("00:00", viewModel.ElapsedDisplay);
            });
        }

        [TestMethod]
        public void ChangingText_DropsPreviousResult()
        {
            StaRunner.Run(() =>
            {
                var viewModel = TimerModeViewModel("Мама мила раму");
                viewModel.ToggleMeasurementCommand.Execute(null);
                viewModel.ToggleMeasurementCommand.Execute(null);

                // Підсумок стосується конкретного тексту: змінили текст —
                // старі WPM більше ні про що не свідчать.
                viewModel.Text = "Зовсім інший текст";

                Assert.IsFalse(viewModel.HasResult);
            });
        }

        [TestMethod]
        public void CharsRead_ExcludesSpaces()
        {
            StaRunner.Run(() =>
            {
                var viewModel = TimerModeViewModel("Мама мила раму");
                viewModel.RecalculateNow();

                // 12 букв, три слова, два пробіли: 14 знаків усього, 12 без пробілів.
                Assert.AreEqual(14, viewModel.Stats.CharCount);
                Assert.AreEqual(12, viewModel.CharsRead);
            });
        }

        [TestMethod]
        public void WordsRead_IsWholeTextWithoutBoundary()
        {
            StaRunner.Run(() =>
            {
                // Поки вчитель не клікнув слово, прочитаним вважається весь текст
                // (специфікація, 4.7).
                var viewModel = TimerModeViewModel("Раз два три чотири пʼять");
                viewModel.RecalculateNow();

                Assert.IsFalse(viewModel.HasBoundary);
                Assert.AreEqual(viewModel.Stats.WordCount, viewModel.WordsRead);
            });
        }
    }
}

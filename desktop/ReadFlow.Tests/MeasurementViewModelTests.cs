using Microsoft.VisualStudio.TestTools.UnitTesting;
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
    /// </summary>
    [TestClass]
    public class MeasurementViewModelTests
    {
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
                var viewModel = new MainViewModel { Text = "Мама мила раму" };

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
                var viewModel = new MainViewModel { Text = "Мама мила раму" };
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
                var viewModel = new MainViewModel { Text = "Мама мила раму" };
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
                var viewModel = new MainViewModel { Text = "Мама мила раму" };
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
                var viewModel = new MainViewModel { Text = "Мама мила раму" };
                viewModel.RecalculateNow();

                // 12 букв, три слова, два пробіли: 14 знаків усього, 12 без пробілів.
                Assert.AreEqual(14, viewModel.Stats.CharCount);
                Assert.AreEqual(12, viewModel.CharsRead);
            });
        }

        [TestMethod]
        public void WordsRead_IsWholeTextUntilBoundaryExists()
        {
            StaRunner.Run(() =>
            {
                // Межа читання зʼявиться в Задачі 7; поки що прочитаним
                // вважається весь текст.
                var viewModel = new MainViewModel { Text = "Раз два три чотири пʼять" };
                viewModel.RecalculateNow();

                Assert.AreEqual(viewModel.Stats.WordCount, viewModel.WordsRead);
            });
        }
    }
}

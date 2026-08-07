using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.Models;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Межа читання — режим A «клік = стоп» (Задача 7).
    ///
    /// Перевіряється три речі: скільки слів і знаків вважається прочитаним,
    /// звідки береться час у формулі (специфікація, 4.8) і коли межа зникає.
    /// Час у режимі A заданий наперед, тому WPM тут — точне число, а не «приблизно»:
    /// тести не залежать від реального годинника.
    /// </summary>
    [TestClass]
    public class ReadingBoundaryTests
    {
        private const string Text = "Раз два три чотири пʼять";

        private static MainViewModel ClickStopViewModel()
        {
            var viewModel = new MainViewModel
            {
                MeasurementMode = MeasurementMode.ClickStop,
                TimerSeconds = 60,
                Text = Text
            };

            viewModel.RecalculateNow();
            return viewModel;
        }

        // ── Підрахунок знаків до межі ─────────────────────────────────────

        [TestMethod]
        public void CountCharsNoSpaces_CountsPrefixWithoutWhitespace()
        {
            // «Раз два три» — 9 букв, два пробіли.
            Assert.AreEqual(9, TextStatsCalculator.CountCharsNoSpaces(Text, 11));
        }

        [TestMethod]
        public void CountCharsNoSpaces_TreatsLineBreaksAsWhitespace()
        {
            // \r\n не потрапляє в підрахунок без пробілів, тому нормалізація
            // переносів тут не потрібна: Windows і Android дадуть однакове число.
            Assert.AreEqual(6, TextStatsCalculator.CountCharsNoSpaces("абв\r\nгде", 8));
        }

        [TestMethod]
        public void CountCharsNoSpaces_HandlesEdges()
        {
            Assert.AreEqual(0, TextStatsCalculator.CountCharsNoSpaces(Text, 0));
            Assert.AreEqual(0, TextStatsCalculator.CountCharsNoSpaces(Text, -5));
            Assert.AreEqual(0, TextStatsCalculator.CountCharsNoSpaces(null, 10));
            Assert.AreEqual(20, TextStatsCalculator.CountCharsNoSpaces(Text, 10000));
        }

        // ── Слово за номером ──────────────────────────────────────────────

        [TestMethod]
        public void WordByNumber_ReturnsWordOrNull()
        {
            var document = new ReadingDocument(Text, TextStatsCalculator.GetWords(Text));

            Assert.AreEqual("Раз", document.WordByNumber(1).Text);
            Assert.AreEqual("три", document.WordByNumber(3).Text);
            Assert.IsNull(document.WordByNumber(0));
            Assert.IsNull(document.WordByNumber(6));
            Assert.IsNull(document.WordByNumber(-1));
        }

        // ── Клік по слову ─────────────────────────────────────────────────

        [TestMethod]
        public void ClickingWord_LimitsWordsAndCharsRead()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                Assert.AreEqual(5, viewModel.WordsRead);

                viewModel.SelectWordCommand.Execute(3);

                Assert.AreEqual(3, viewModel.BoundaryWordNumber);
                Assert.IsTrue(viewModel.HasBoundary);
                Assert.AreEqual(3, viewModel.WordsRead);

                // «Раз два три» — 9 знаків без пробілів.
                Assert.AreEqual(9, viewModel.CharsRead);
            });
        }

        [TestMethod]
        public void ClickingSameWordAgain_ClearsBoundary()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();

                viewModel.SelectWordCommand.Execute(3);
                viewModel.SelectWordCommand.Execute(3);

                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
                Assert.IsFalse(viewModel.HasBoundary);
                Assert.AreEqual(5, viewModel.WordsRead);

                // Без межі підсумок у режимі A ні про що не свідчить.
                Assert.IsFalse(viewModel.HasResult);
            });
        }

        [TestMethod]
        public void ClickingAnotherWord_MovesBoundary()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();

                viewModel.SelectWordCommand.Execute(3);
                viewModel.SelectWordCommand.Execute(4);

                Assert.AreEqual(4, viewModel.BoundaryWordNumber);
                Assert.AreEqual(4, viewModel.WordsRead);
            });
        }

        [TestMethod]
        public void ClickingWordOutsideText_IsIgnored()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();

                viewModel.SelectWordCommand.Execute(99);
                viewModel.SelectWordCommand.Execute(null);
                viewModel.SelectWordCommand.Execute("не число");

                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
            });
        }

        // ── Режим A: час заданий, а не фактичний ──────────────────────────

        [TestMethod]
        public void ClickStop_CountsSpeedFromChosenDurationWithoutStartingTimer()
        {
            StaRunner.Run(() =>
            {
                // Вчитель сам відлічив хвилину й клікнув: таймер не запускали,
                // але підсумок має бути (специфікація, 4.8).
                var viewModel = ClickStopViewModel();

                viewModel.SelectWordCommand.Execute(3);

                Assert.IsTrue(viewModel.HasResult);
                Assert.AreEqual("01:00", viewModel.ResultElapsedDisplay);
                Assert.AreEqual(3, viewModel.WordsPerMinute);
                Assert.AreEqual(9, viewModel.CharsPerMinute);
            });
        }

        [TestMethod]
        public void ClickStop_UsesDurationNotStopwatch()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.TimerSeconds = 30;

                viewModel.SelectWordCommand.Execute(3);

                // 3 слова за 30 с — 6 слів за хвилину. Якби брався секундомір,
                // тут було б число в тисячах: між Стартом і кліком минули міліcекунди.
                Assert.AreEqual(6, viewModel.WordsPerMinute);
            });
        }

        [TestMethod]
        public void ClickStop_ClickStopsRunningTimer()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.ToggleMeasurementCommand.Execute(null);
                Assert.IsTrue(viewModel.IsMeasuring);

                viewModel.SelectWordCommand.Execute(3);

                Assert.IsFalse(viewModel.IsMeasuring);
                Assert.AreEqual(3, viewModel.WordsRead);
            });
        }

        [TestMethod]
        public void ClickStop_ChangingDurationUpdatesResult()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.SelectWordCommand.Execute(3);
                Assert.AreEqual(3, viewModel.WordsPerMinute);

                viewModel.TimerSeconds = 30;

                // Тривалість — знаменник формули; підсумок мусить наздогнати.
                Assert.AreEqual(6, viewModel.WordsPerMinute);
                Assert.AreEqual("00:30", viewModel.ResultElapsedDisplay);
            });
        }

        // ── Режим B: межа працює, але час рахує секундомір ─────────────────

        [TestMethod]
        public void TimerMode_ClickDoesNotStopMeasurement()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel
                {
                    MeasurementMode = MeasurementMode.Timer,
                    Text = Text
                };

                viewModel.ToggleMeasurementCommand.Execute(null);
                viewModel.SelectWordCommand.Execute(3);

                // У режимі B відлік зупиняє Стоп, а не клік: учень читає
                // фактичний час, а межа лише каже, докуди він дочитав (2.2).
                Assert.IsTrue(viewModel.IsMeasuring);
                Assert.AreEqual(3, viewModel.WordsRead);
            });
        }

        [TestMethod]
        public void TimerMode_BoundarySetAfterStopRecalculatesResult()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel
                {
                    MeasurementMode = MeasurementMode.Timer,
                    Text = Text
                };

                viewModel.ToggleMeasurementCommand.Execute(null);
                viewModel.ToggleMeasurementCommand.Execute(null);
                Assert.IsTrue(viewModel.HasResult);

                var wholeText = viewModel.WordsPerMinute;

                viewModel.SelectWordCommand.Execute(2);

                Assert.AreEqual(2, viewModel.WordsRead);
                Assert.IsTrue(
                    viewModel.WordsPerMinute < wholeText,
                    "Менше прочитаних слів за той самий час — менша швидкість.");
            });
        }

        // ── Коли межа зникає ──────────────────────────────────────────────

        [TestMethod]
        public void ChangingText_ClearsBoundary()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.SelectWordCommand.Execute(3);

                viewModel.Text = "Зовсім інший текст";

                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
                Assert.IsFalse(viewModel.HasResult);
            });
        }

        [TestMethod]
        public void Reset_ClearsBoundary()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.SelectWordCommand.Execute(3);

                viewModel.ResetMeasurementCommand.Execute(null);

                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
                Assert.IsFalse(viewModel.HasResult);
            });
        }

        [TestMethod]
        public void StartingNewMeasurement_ClearsBoundary()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.SelectWordCommand.Execute(3);

                viewModel.ToggleMeasurementCommand.Execute(null);

                // Межа від попереднього учня мовчки зрізала б половину тексту.
                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
                Assert.AreEqual(5, viewModel.WordsRead);
            });
        }

        [TestMethod]
        public void SwitchingMeasurementMode_DropsResultButKeepsBoundary()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ClickStopViewModel();
                viewModel.SelectWordCommand.Execute(3);
                Assert.IsTrue(viewModel.HasResult);

                viewModel.MeasurementMode = MeasurementMode.Timer;

                // Режими беруть час із різних джерел, тож старий підсумок не чинний.
                Assert.IsFalse(viewModel.HasResult);

                // А от межа — про те, де учень зупинився, і від способу заміру не залежить.
                Assert.AreEqual(3, viewModel.BoundaryWordNumber);
            });
        }
    }
}

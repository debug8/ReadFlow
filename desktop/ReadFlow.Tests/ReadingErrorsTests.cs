using System.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Відмітка помилок — режим C (Задача 8).
    ///
    /// Замір скрізь у режимі A: там час заданий наперед, тож WPM і «чиста»
    /// швидкість — точні числа, а не «приблизно стільки». Інакше кожен тест
    /// залежав би від реального годинника.
    /// </summary>
    [TestClass]
    public class ReadingErrorsTests
    {
        private const string Text = "Раз два три чотири пʼять шість сім вісім девʼять десять";

        private static MainViewModel ErrorModeViewModel()
        {
            var viewModel = new MainViewModel
            {
                MeasurementMode = MeasurementMode.ClickStop,
                TimerSeconds = 60,
                MarkErrors = true,
                Text = Text
            };

            viewModel.RecalculateNow();
            return viewModel;
        }

        // ── Позначення ────────────────────────────────────────────────────

        [TestMethod]
        public void LeftClick_MarksErrorWhenModeIsOn()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(2);

                Assert.IsTrue(viewModel.HasErrors);
                Assert.AreEqual(1, viewModel.ErrorCount);
                CollectionAssert.AreEqual(new[] { 2 }, viewModel.ErrorWords.ToArray());

                // Межу лівий клік у цьому режимі не чіпає.
                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
            });
        }

        [TestMethod]
        public void LeftClickAgain_RemovesError()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(2);
                viewModel.SelectWordCommand.Execute(2);

                Assert.IsFalse(viewModel.HasErrors);
                Assert.AreEqual(0, viewModel.ErrorCount);
                Assert.AreEqual(0, viewModel.ErrorWords.Count);
            });
        }

        [TestMethod]
        public void RightClick_SetsBoundaryEvenInErrorMode()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SetBoundaryCommand.Execute(6);

                Assert.AreEqual(6, viewModel.BoundaryWordNumber);
                Assert.AreEqual(6, viewModel.WordsRead);
                Assert.AreEqual(0, viewModel.ErrorCount);
            });
        }

        [TestMethod]
        public void LeftClick_SetsBoundaryWhenErrorModeIsOff()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();
                viewModel.MarkErrors = false;

                viewModel.SelectWordCommand.Execute(4);

                Assert.AreEqual(4, viewModel.BoundaryWordNumber);
                Assert.AreEqual(0, viewModel.ErrorCount);
            });
        }

        // ── Помилки в межах прочитаного ───────────────────────────────────

        [TestMethod]
        public void ErrorsBeyondBoundary_AreNotCounted()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(2);
                viewModel.SelectWordCommand.Execute(9);   // за майбутньою межею
                viewModel.SetBoundaryCommand.Execute(5);

                // Учень до девʼятого слова не дочитав — та помилка ні про що не свідчить.
                Assert.AreEqual(1, viewModel.ErrorCount);

                // Але позначка лишилася: перенесли межу — вона знову в грі.
                Assert.AreEqual(2, viewModel.ErrorWords.Count);
            });
        }

        [TestMethod]
        public void MovingBoundary_BringsErrorBackIntoCount()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(9);
                viewModel.SetBoundaryCommand.Execute(5);
                Assert.AreEqual(0, viewModel.ErrorCount);

                viewModel.SetBoundaryCommand.Execute(10);

                Assert.AreEqual(1, viewModel.ErrorCount);
            });
        }

        // ── Показники ─────────────────────────────────────────────────────

        [TestMethod]
        public void ErrorPercent_IsShareOfWordsRead()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(1);
                viewModel.SelectWordCommand.Execute(2);

                // Дві помилки з десяти прочитаних слів.
                Assert.AreEqual(20d, viewModel.ErrorPercent, 0.001);

                viewModel.SetBoundaryCommand.Execute(5);

                // Ті самі дві з пʼяти.
                Assert.AreEqual(40d, viewModel.ErrorPercent, 0.001);
            });
        }

        [TestMethod]
        public void ErrorPercent_RoundsHalfAwayFromZeroOnExactFraction()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                // 1 помилка з 3 прочитаних — 33.333…, після округлення 33.3.
                viewModel.SelectWordCommand.Execute(1);
                viewModel.SetBoundaryCommand.Execute(3);

                Assert.AreEqual(33.3d, viewModel.ErrorPercent, 0.001);

                // 1 із 8 — це рівно 12.5. Число представне точно лише тому,
                // що ділення йде в decimal: на double тут уже була б похибка.
                viewModel.SetBoundaryCommand.Execute(8);
                Assert.AreEqual(12.5d, viewModel.ErrorPercent, 0.001);
            });
        }

        [TestMethod]
        public void CleanSpeed_ExcludesWordsReadWithErrors()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(1);
                viewModel.SelectWordCommand.Execute(2);
                viewModel.SetBoundaryCommand.Execute(10);

                Assert.IsTrue(viewModel.HasResult);

                // 10 слів за 60 с — 10 WPM; дві помилки лишають 8.
                Assert.AreEqual(10, viewModel.WordsPerMinute);
                Assert.AreEqual(8, viewModel.CleanWordsPerMinute);
            });
        }

        [TestMethod]
        public void MarkingErrorAfterResult_UpdatesCleanSpeed()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();
                viewModel.SetBoundaryCommand.Execute(10);
                Assert.AreEqual(10, viewModel.CleanWordsPerMinute);

                viewModel.SelectWordCommand.Execute(4);

                // Підсумок уже є — позначка мусить одразу відбитися на числі.
                Assert.AreEqual(9, viewModel.CleanWordsPerMinute);
                Assert.AreEqual(10, viewModel.WordsPerMinute);
            });
        }

        // ── Коли позначки зникають ────────────────────────────────────────

        [TestMethod]
        public void TurningModeOff_ClearsErrors()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();
                viewModel.SelectWordCommand.Execute(2);

                viewModel.MarkErrors = false;

                // Позначки саме зникають, а не ховаються: прихований стан,
                // який мовчки повертається, — джерело чисел, що не сходяться.
                Assert.AreEqual(0, viewModel.ErrorCount);
                Assert.AreEqual(0, viewModel.ErrorWords.Count);
            });
        }

        [TestMethod]
        public void ChangingTextAndReset_ClearErrors()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();
                viewModel.SelectWordCommand.Execute(2);

                viewModel.Text = "Зовсім інший текст";
                Assert.AreEqual(0, viewModel.ErrorWords.Count);

                viewModel.Text = Text;
                viewModel.RecalculateNow();
                viewModel.SelectWordCommand.Execute(2);
                Assert.AreEqual(1, viewModel.ErrorCount);

                viewModel.ResetMeasurementCommand.Execute(null);
                Assert.AreEqual(0, viewModel.ErrorWords.Count);
            });
        }

        [TestMethod]
        public void StartingMeasurement_ClearsErrors()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();
                viewModel.SelectWordCommand.Execute(2);

                viewModel.ToggleMeasurementCommand.Execute(null);

                // Помилки попереднього учня зіпсували б «чисту» швидкість наступного.
                Assert.AreEqual(0, viewModel.ErrorWords.Count);
            });
        }

        [TestMethod]
        public void ClickOutsideText_IsIgnored()
        {
            StaRunner.Run(() =>
            {
                var viewModel = ErrorModeViewModel();

                viewModel.SelectWordCommand.Execute(99);
                viewModel.SelectWordCommand.Execute(null);
                viewModel.SetBoundaryCommand.Execute(99);

                Assert.AreEqual(0, viewModel.ErrorWords.Count);
                Assert.AreEqual(0, viewModel.BoundaryWordNumber);
            });
        }
    }
}

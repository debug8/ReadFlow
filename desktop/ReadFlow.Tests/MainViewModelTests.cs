using System.Collections.Generic;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тести ViewModel головного вікна.
    ///
    /// Дебаунс тут навмисно не «прокручується» очікуванням: у тестовому потоці
    /// немає циклу диспетчера, тож <c>DispatcherTimer</c> ніколи не тікає. Замість
    /// боротьби з таймером перевіряємо два боки контракту окремо: що набір тексту
    /// сам собою статистику НЕ оновлює (тобто дебаунс справді є) і що
    /// <c>RecalculateNow</c> дає правильні числа.
    /// </summary>
    [TestClass]
    public class MainViewModelTests
    {
        [TestMethod]
        public void NewViewModel_HasEmptyTextAndZeroStats()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                Assert.AreEqual(string.Empty, viewModel.Text);
                Assert.AreEqual(0, viewModel.Stats.WordCount);
                Assert.AreEqual(0, viewModel.Stats.CharCount);
            });
        }

        [TestMethod]
        public void SettingText_DoesNotRecalculateImmediately()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Text = "Мама мила раму" };

                Assert.AreEqual(
                    0,
                    viewModel.Stats.WordCount,
                    "Статистика має чекати дебаунсу, інакше вона перераховується на кожну літеру.");
            });
        }

        [TestMethod]
        public void RecalculateNow_UpdatesAllStats()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Text = "Ліс прокинувся. Пташки співали!" };

                viewModel.RecalculateNow();

                Assert.AreEqual(4, viewModel.Stats.WordCount);
                Assert.AreEqual(31, viewModel.Stats.CharCount);
                Assert.AreEqual(2, viewModel.Stats.SentenceCount);
                Assert.AreEqual(1, viewModel.Stats.ParagraphCount);
            });
        }

        [TestMethod]
        public void RecalculateNow_RaisesPropertyChangedForStats()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Text = "слово" };
                var changed = new List<string>();
                viewModel.PropertyChanged += (s, e) => changed.Add(e.PropertyName);

                viewModel.RecalculateNow();

                CollectionAssert.Contains(changed, "Stats", "Без події панель статистики не оновиться.");
            });
        }

        [TestMethod]
        public void SettingText_RaisesPropertyChangedForText()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();
                var changed = new List<string>();
                viewModel.PropertyChanged += (s, e) => changed.Add(e.PropertyName);

                viewModel.Text = "нове";

                CollectionAssert.Contains(changed, "Text");
            });
        }

        [TestMethod]
        public void NullText_BecomesEmptyString()
        {
            StaRunner.Run(() =>
            {
                // Підказка «Вставте текст сюди» показується через DataTrigger зі Value="",
                // а null із рядком не збігається — тому null тут неприпустимий.
                var viewModel = new MainViewModel { Text = "щось" };

                viewModel.Text = null;

                Assert.AreEqual(string.Empty, viewModel.Text);
            });
        }

        [TestMethod]
        public void ClearingText_ResetsStatsToZero()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Text = "два слова" };
                viewModel.RecalculateNow();
                Assert.AreEqual(2, viewModel.Stats.WordCount);

                viewModel.Text = string.Empty;
                viewModel.RecalculateNow();

                Assert.AreEqual(0, viewModel.Stats.WordCount);
                Assert.AreEqual(0d, viewModel.Stats.AverageWordLength);
            });
        }
    }
}

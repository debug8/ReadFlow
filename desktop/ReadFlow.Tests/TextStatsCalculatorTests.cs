using System;
using System.Diagnostics;
using System.Linq;
using System.Text;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Кожен тест тут відповідає правилу з розділу 4 специфікації.
    /// Якщо тест падає — спершу дивимось у специфікацію, а не «підганяємо» код:
    /// ці ж числа має показати Android-версія.
    /// </summary>
    [TestClass]
    public class TextStatsCalculatorTests
    {
        // ── Порожній текст ────────────────────────────────────────────────

        [TestMethod]
        public void EmptyText_AllCountsAreZero()
        {
            foreach (var text in new[] { null, string.Empty })
            {
                var stats = TextStatsCalculator.Calculate(text);

                Assert.AreEqual(0, stats.WordCount);
                Assert.AreEqual(0, stats.CharCount);
                Assert.AreEqual(0, stats.CharCountNoSpaces);
                Assert.AreEqual(0, stats.LetterCount);
                Assert.AreEqual(0d, stats.AverageWordLength);
                Assert.AreEqual(0, stats.SentenceCount);
                Assert.AreEqual(0, stats.ParagraphCount);
            }
        }

        [TestMethod]
        public void WhitespaceOnly_NoWordsButCharsCounted()
        {
            var stats = TextStatsCalculator.Calculate("   \t  \n  ");

            Assert.AreEqual(0, stats.WordCount);
            Assert.AreEqual(9, stats.CharCount);
            Assert.AreEqual(0, stats.CharCountNoSpaces);
            Assert.AreEqual(0, stats.SentenceCount);
            Assert.AreEqual(0, stats.ParagraphCount);
            Assert.AreEqual(0d, stats.AverageWordLength, "Ділення на нуль не має ставатися.");
        }

        [TestMethod]
        public void EmptyText_ReturnsNoWordTokens()
        {
            Assert.AreEqual(0, TextStatsCalculator.GetWords(null).Count);
            Assert.AreEqual(0, TextStatsCalculator.GetWords(string.Empty).Count);
        }

        // ── Апостроф (4.2) ────────────────────────────────────────────────

        [TestMethod]
        public void Apostrophe_DoesNotSplitWord()
        {
            var words = TextStatsCalculator.GetWords("комп'ютер");

            Assert.AreEqual(1, words.Count);
            Assert.AreEqual("комп'ютер", words[0].Text);
            Assert.AreEqual(8, words[0].LetterCount, "Апостроф не є буквою.");
        }

        [TestMethod]
        public void AllApostropheVariants_AreTreatedIdentically()
        {
            // Word підміняє ' на ’, українські розкладки дають ʼ.
            foreach (var apostrophe in new[] { '\'', '’', 'ʼ', '‘', '´', '`' })
            {
                var text = "мавп" + apostrophe + "ячий";
                var words = TextStatsCalculator.GetWords(text);

                Assert.AreEqual(1, words.Count, "Апостроф U+" + ((int)apostrophe).ToString("X4") + " розірвав слово.");
                Assert.AreEqual(8, words[0].LetterCount);
            }
        }

        [TestMethod]
        public void TrailingApostrophe_IsNotPartOfWord()
        {
            var words = TextStatsCalculator.GetWords("слово' ще");

            Assert.AreEqual(2, words.Count);
            Assert.AreEqual("слово", words[0].Text);
        }

        // ── Дефіс і тире (4.2) ────────────────────────────────────────────

        [TestMethod]
        public void Hyphen_DoesNotSplitWord()
        {
            var words = TextStatsCalculator.GetWords("синьо-жовтий");

            Assert.AreEqual(1, words.Count);
            Assert.AreEqual("синьо-жовтий", words[0].Text);
            Assert.AreEqual(11, words[0].LetterCount);
        }

        [TestMethod]
        public void MultipleHyphens_StayOneWord()
        {
            var words = TextStatsCalculator.GetWords("будь-як-небудь");

            Assert.AreEqual(1, words.Count);
        }

        [TestMethod]
        public void EmDashAndEnDash_SplitWords()
        {
            // Тире — роздільник, на відміну від дефіса.
            Assert.AreEqual(2, TextStatsCalculator.GetWords("слово — друге").Count);
            Assert.AreEqual(2, TextStatsCalculator.GetWords("слово–друге").Count);
            Assert.AreEqual(2, TextStatsCalculator.GetWords("слово−друге").Count);
        }

        [TestMethod]
        public void DoubleHyphen_SplitsWord()
        {
            // Другий дефіс не має букви ліворуч, тому слово розривається.
            var words = TextStatsCalculator.GetWords("слово--друге");

            Assert.AreEqual(2, words.Count);
            Assert.AreEqual("слово", words[0].Text);
            Assert.AreEqual("друге", words[1].Text);
        }

        [TestMethod]
        public void StandaloneHyphen_IsNotAWord()
        {
            var words = TextStatsCalculator.GetWords("а - б");

            Assert.AreEqual(2, words.Count);
        }

        // ── Пробіли й розділові знаки ─────────────────────────────────────

        [TestMethod]
        public void ManySeparators_DoNotProduceEmptyWords()
        {
            var words = TextStatsCalculator.GetWords("  раз,,,   два!!!  \t\n  три  ");

            Assert.AreEqual(3, words.Count);
            CollectionAssert.AreEqual(
                new[] { "раз", "два", "три" },
                words.Select(w => w.Text).ToArray());
        }

        [TestMethod]
        public void PunctuationOnly_HasNoWordsAndNoSentences()
        {
            var stats = TextStatsCalculator.Calculate("... !? — «»");

            Assert.AreEqual(0, stats.WordCount);
            Assert.AreEqual(0, stats.LetterCount);
            Assert.AreEqual(0, stats.SentenceCount, "Фрагмент без букв реченням не є.");
        }

        // ── Цифри (4.1) ───────────────────────────────────────────────────

        [TestMethod]
        public void Digits_CountAsWordAndAsLetters()
        {
            var stats = TextStatsCalculator.Calculate("У 2024 році");

            Assert.AreEqual(3, stats.WordCount, "«2024» — окреме слово.");
            Assert.AreEqual(1 + 4 + 4, stats.LetterCount, "Цифри рахуються як букви.");
        }

        [TestMethod]
        public void DigitsAndLettersMixed_StayOneWord()
        {
            var words = TextStatsCalculator.GetWords("A4 та 3D-модель");

            Assert.AreEqual(3, words.Count);
            Assert.AreEqual("A4", words[0].Text);
            Assert.AreEqual("3D-модель", words[2].Text);
        }

        // ── Знаки (4.3) ───────────────────────────────────────────────────

        [TestMethod]
        public void Chars_CountWithAndWithoutSpaces()
        {
            var stats = TextStatsCalculator.Calculate("а б в");

            Assert.AreEqual(5, stats.CharCount);
            Assert.AreEqual(3, stats.CharCountNoSpaces);
        }

        [TestMethod]
        public void CrLf_CountsAsSingleChar()
        {
            // Інакше той самий текст дав би різні числа у Windows і на Android.
            var windows = TextStatsCalculator.Calculate("а\r\nб");
            var unix = TextStatsCalculator.Calculate("а\nб");
            var mac = TextStatsCalculator.Calculate("а\rб");

            Assert.AreEqual(3, windows.CharCount);
            Assert.AreEqual(unix.CharCount, windows.CharCount);
            Assert.AreEqual(unix.CharCount, mac.CharCount);
            Assert.AreEqual(unix.ParagraphCount, windows.ParagraphCount);
        }

        // ── Середня довжина слова (4.4) ───────────────────────────────────

        [TestMethod]
        public void AverageWordLength_RoundedToOneDecimal()
        {
            // 2 + 4 + 5 = 11 букв на 3 слова = 3.666… → 3.7
            var stats = TextStatsCalculator.Calculate("ця мала книга");

            Assert.AreEqual(11, stats.LetterCount);
            Assert.AreEqual(3.7d, stats.AverageWordLength, 0.0001);
        }

        [TestMethod]
        public void AverageWordLength_RoundsHalfAwayFromZero()
        {
            // 17 букв на 4 слова = 4.25. Банківське округлення .NET дало б 4.2,
            // Java/Kotlin дає 4.3 — платформи мусять збігатися.
            var stats = TextStatsCalculator.Calculate("аб абв абвг абвгдежз");

            Assert.AreEqual(17, stats.LetterCount);
            Assert.AreEqual(4, stats.WordCount);
            Assert.AreEqual(4.3d, stats.AverageWordLength, 0.0001);
        }

        // ── Речення (4.5) ─────────────────────────────────────────────────

        [TestMethod]
        public void Sentences_CountedByTerminators()
        {
            Assert.AreEqual(3, TextStatsCalculator.Calculate("Раз. Два! Три?").SentenceCount);
        }

        [TestMethod]
        public void Sentences_RepeatedTerminatorsCountOnce()
        {
            Assert.AreEqual(1, TextStatsCalculator.Calculate("Ого!!!").SentenceCount);
            Assert.AreEqual(1, TextStatsCalculator.Calculate("Справді?!").SentenceCount);
            Assert.AreEqual(2, TextStatsCalculator.Calculate("Так… Ні…").SentenceCount);
        }

        [TestMethod]
        public void Sentences_TailWithoutTerminatorCounts()
        {
            Assert.AreEqual(2, TextStatsCalculator.Calculate("Перше. Друге без крапки").SentenceCount);
        }

        // ── Абзаци (4.6) ──────────────────────────────────────────────────

        [TestMethod]
        public void Paragraphs_NonEmptyLinesMode()
        {
            var options = new CountingOptions { Paragraphs = ParagraphMode.NonEmptyLines };

            Assert.AreEqual(3, TextStatsCalculator.Calculate("А\nБ\n\nВ", options).ParagraphCount);
            Assert.AreEqual(2, TextStatsCalculator.Calculate("А\nБ", options).ParagraphCount);
            Assert.AreEqual(1, TextStatsCalculator.Calculate("А\n   \n", options).ParagraphCount);
        }

        [TestMethod]
        public void Paragraphs_BlankLineSeparatedMode()
        {
            var options = new CountingOptions { Paragraphs = ParagraphMode.BlankLineSeparated };

            Assert.AreEqual(2, TextStatsCalculator.Calculate("А\nБ\n\nВ", options).ParagraphCount);
            Assert.AreEqual(1, TextStatsCalculator.Calculate("А\nБ", options).ParagraphCount);
            Assert.AreEqual(2, TextStatsCalculator.Calculate("А\n \t \nВ", options).ParagraphCount);
        }

        [TestMethod]
        public void Paragraphs_DefaultModeIsNonEmptyLines()
        {
            Assert.AreEqual(ParagraphMode.NonEmptyLines, CountingOptions.Default.Paragraphs);
            Assert.AreEqual(3, TextStatsCalculator.Calculate("А\nБ\n\nВ").ParagraphCount);
        }

        // ── Межі слів (для Задач 4 і 7) ───────────────────────────────────

        [TestMethod]
        public void WordTokens_HaveCorrectBoundariesAndNumbers()
        {
            const string text = "  Ліс прокинувся, пташки співали.";
            var words = TextStatsCalculator.GetWords(text);

            Assert.AreEqual(4, words.Count);

            for (var i = 0; i < words.Count; i++)
            {
                var word = words[i];

                Assert.AreEqual(i + 1, word.Number, "Нумерація має починатися з 1 і не мати пропусків.");
                Assert.AreEqual(word.Text, text.Substring(word.Start, word.Length),
                    "Межі слова мають вказувати рівно на нього у вихідному тексті.");
                Assert.AreEqual(word.Start + word.Length, word.End);
            }

            Assert.AreEqual(2, words[0].Start);
            Assert.AreEqual("співали", words[3].Text);
        }

        [TestMethod]
        public void WordTokens_BoundariesRefarToOriginalTextEvenWithCrLf()
        {
            // Нормалізація переносів не має зсувати індекси слів:
            // саме за ними Задача 4 рендеритиме вихідний текст.
            const string text = "перше\r\nдруге";
            var words = TextStatsCalculator.GetWords(text);

            Assert.AreEqual(2, words.Count);
            Assert.AreEqual("друге", text.Substring(words[1].Start, words[1].Length));
        }

        [TestMethod]
        public void LetterCount_EqualsSumOfWordLetters()
        {
            const string text = "Мама мила раму, а тато — 2 вікна: синьо-жовті й комп'ютерні!";
            var words = TextStatsCalculator.GetWords(text);
            var stats = TextStatsCalculator.Calculate(text, words, null);

            Assert.AreEqual(words.Sum(w => w.LetterCount), stats.LetterCount,
                "Букви поза словами існувати не можуть.");
            Assert.AreEqual(words.Count, stats.WordCount);
        }

        // ── Продуктивність (критерій приймання 7) ─────────────────────────

        [TestMethod]
        public void LargeText_IsProcessedQuickly()
        {
            var builder = new StringBuilder();
            for (var i = 0; i < 3000; i++)
            {
                builder.Append("синьо-жовтий ");
                if (i % 12 == 11)
                {
                    builder.Append(".\n");
                }
            }

            var text = builder.ToString();
            var stopwatch = Stopwatch.StartNew();
            var stats = TextStatsCalculator.Calculate(text);
            stopwatch.Stop();

            Assert.AreEqual(3000, stats.WordCount);
            Assert.IsTrue(
                stopwatch.ElapsedMilliseconds < 500,
                "Підрахунок на 3000 слів зайняв " + stopwatch.ElapsedMilliseconds + " мс.");
        }
    }
}

using System.Linq;
using System.Text;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.Models;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тести розбиття тексту на слова й роздільники для режиму читання.
    ///
    /// Головний інваріант — склеївши всі сегменти по порядку, маємо отримати рівно
    /// вихідний текст. Помилка на одиницю в межах слова інакше проявилася б як
    /// зникла кома або зʼїдений пробіл, і помітити це на екрані дуже важко.
    /// </summary>
    [TestClass]
    public class ReadingDocumentTests
    {
        private static ReadingDocument Build(string text)
        {
            return new ReadingDocument(text, TextStatsCalculator.GetWords(text));
        }

        private static string Join(ReadingDocument document)
        {
            var builder = new StringBuilder();

            foreach (var segment in document.GetSegments())
            {
                builder.Append(segment.Text);
            }

            return builder.ToString();
        }

        [TestMethod]
        public void Segments_ReassembleIntoOriginalText()
        {
            var texts = new[]
            {
                "Мама мила раму.",
                "  Ліс прокинувся, пташки співали!  ",
                "Перший рядок\nДругий рядок\n\nТретій",
                "Windows\r\nстиль\r\nпереносів",
                "комп'ютер, синьо-жовтий і 2024 — разом",
                "!!! ??? ...",
                "одне",
                " ",
            };

            foreach (var text in texts)
            {
                Assert.AreEqual(text, Join(Build(text)), "Текст не склався назад: «" + text + "»");
            }
        }

        [TestMethod]
        public void Segments_WordNumbersAreSequentialFromOne()
        {
            var document = Build("Ліс прокинувся, пташки співали.");

            var numbers = document.GetSegments()
                .Where(s => s.IsWord)
                .Select(s => s.WordNumber)
                .ToArray();

            CollectionAssert.AreEqual(new[] { 1, 2, 3, 4 }, numbers);
        }

        [TestMethod]
        public void Segments_WordTextMatchesToken()
        {
            var document = Build("Мама мила раму");

            var words = document.GetSegments().Where(s => s.IsWord).ToList();

            CollectionAssert.AreEqual(
                new[] { "Мама", "мила", "раму" },
                words.Select(s => s.Text).ToArray());
        }

        [TestMethod]
        public void Segments_SeparatorsHaveZeroNumberAndAreNeverEmpty()
        {
            var document = Build("  Раз, два!  ");

            foreach (var segment in document.GetSegments())
            {
                Assert.AreNotEqual(0, segment.Text.Length, "Порожній сегмент — зайвий Run у рендері.");

                if (!segment.IsWord)
                {
                    Assert.AreEqual(0, segment.WordNumber);
                }
            }
        }

        [TestMethod]
        public void EmptyText_ProducesNoSegments()
        {
            Assert.AreEqual(0, Build(string.Empty).GetSegments().Count);
            Assert.AreEqual(0, ReadingDocument.Empty.GetSegments().Count);
        }

        [TestMethod]
        public void NullArguments_AreTreatedAsEmpty()
        {
            var document = new ReadingDocument(null, null);

            Assert.AreEqual(string.Empty, document.Text);
            Assert.AreEqual(0, document.Words.Count);
            Assert.AreEqual(0, document.GetSegments().Count);
        }

        [TestMethod]
        public void MismatchedWords_FallBackToPlainTextInsteadOfThrowing()
        {
            // Межі слів із чужого тексту. Краще показати текст без нумерації,
            // ніж кинути виняток посеред рендеру.
            var document = new ReadingDocument("коротко", TextStatsCalculator.GetWords("зовсім інший, значно довший текст"));

            var segments = document.GetSegments();

            Assert.AreEqual(1, segments.Count);
            Assert.AreEqual("коротко", segments[0].Text);
            Assert.IsFalse(segments[0].IsWord);
        }

        [TestMethod]
        public void LargeText_SegmentsQuicklyAndCompletely()
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
            var document = Build(text);
            var stopwatch = System.Diagnostics.Stopwatch.StartNew();
            var segments = document.GetSegments();
            stopwatch.Stop();

            Assert.AreEqual(3000, segments.Count(s => s.IsWord));
            Assert.AreEqual(text, Join(document));
            Assert.IsTrue(
                stopwatch.ElapsedMilliseconds < 500,
                "Розбиття 3000 слів зайняло " + stopwatch.ElapsedMilliseconds + " мс.");
        }
    }
}

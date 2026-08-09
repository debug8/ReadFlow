using System;
using System.IO;
using System.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Тексти-зразки (Задача 11).
    ///
    /// Головний тест тут — <see cref="EverySample_WordCountInIndexMatchesText"/>:
    /// поле <c>words</c> у реєстрі заповнюється руками, а від нього рахується
    /// приблизний час читання. Розійшлося з текстом — застосунок бреше вчителю,
    /// і жодним іншим способом це не помітно.
    /// </summary>
    [TestClass]
    public class TextSamplesTests
    {
        // ── Реєстр ────────────────────────────────────────────────────────

        [TestMethod]
        public void SharedIndex_ParsesWithThreeLevels()
        {
            var catalog = LoadShared();

            Assert.IsFalse(catalog.IsEmpty, "Реєстр зразків не прочитався.");
            CollectionAssert.AreEqual(
                new[] { "easy", "medium", "hard" },
                catalog.Levels.Select(l => l.Id).ToArray(),
                "Очікуються три рівні складності в порядку зростання.");
        }

        [TestMethod]
        public void SharedIndex_HasNoGradeField()
        {
            // Свідоме рішення: клас у застосунку вже визначає норму, і фільтр
            // зразків за класом змушував би вчителя міняти норму заради тексту.
            // Див. shared/samples/README.md.
            var json = File.ReadAllText(FindSharedFile("index.json"));

            StringAssert.Contains(json, "\"levels\"", "У реєстрі має бути таблиця рівнів.");
            Assert.IsFalse(json.Contains("\"grade\""),
                "Поле grade прибране навмисно — зразки поділені лише за складністю.");
        }

        [TestMethod]
        public void EverySample_FileExistsAndIsRegisteredOnce()
        {
            var catalog = LoadShared();
            var folder = Path.GetDirectoryName(FindSharedFile("index.json"));

            foreach (var sample in catalog.Samples)
            {
                Assert.IsTrue(File.Exists(Path.Combine(folder, sample.File)),
                    "Реєстр згадує файл, якого немає: " + sample.File);
            }

            var duplicates = catalog.Samples
                .GroupBy(s => s.File, StringComparer.OrdinalIgnoreCase)
                .Where(g => g.Count() > 1)
                .Select(g => g.Key)
                .ToList();

            Assert.AreEqual(0, duplicates.Count,
                "Файл зареєстрований двічі: " + string.Join(", ", duplicates));
        }

        [TestMethod]
        public void EveryTextFile_IsRegisteredInIndex()
        {
            // Незареєстрований файл потрапить у збірку через маску в csproj,
            // але в меню його не буде — тобто він мовчки роздує .exe без користі.
            var folder = Path.GetDirectoryName(FindSharedFile("index.json"));
            var registered = LoadShared().Samples.Select(s => s.File).ToList();

            foreach (var file in Directory.GetFiles(folder, "*.txt"))
            {
                var name = Path.GetFileName(file);

                CollectionAssert.Contains(registered, name,
                    "Файл " + name + " лежить у samples/, але не згаданий в index.json.");
            }
        }

        /// <summary>
        /// Кількість слів у реєстрі має збігатися з тим, що порахує сам застосунок
        /// за правилами розділу 4.
        /// </summary>
        [TestMethod]
        public void EverySample_WordCountInIndexMatchesText()
        {
            var folder = Path.GetDirectoryName(FindSharedFile("index.json"));

            foreach (var sample in LoadShared().Samples)
            {
                var text = File.ReadAllText(Path.Combine(folder, sample.File));
                var actual = TextStatsCalculator.GetWords(text).Count;

                Assert.AreEqual(sample.Words, actual,
                    "У " + sample.File + " фактично " + actual + " слів, а в index.json — " +
                    sample.Words + ". Перерахуйте поле words.");
            }
        }

        /// <summary>
        /// Зразки мають бути помітно довшими за хвилинну норму, інакше учень
        /// дочитає текст раніше, ніж мине хвилина, і замір втратить сенс.
        /// </summary>
        [TestMethod]
        public void EverySample_IsLongEnoughForAMinuteOfReading()
        {
            // Планку беремо з довідника норм, а не з константи: піднімуть норми —
            // і вимога до зразків має піднятися разом із ними.
            var fastest = LoadNorms().Grades
                .SelectMany(g => g.Semesters)
                .Max(n => n.Max);

            foreach (var sample in LoadShared().Samples)
            {
                Assert.IsTrue(sample.Words * 60 / fastest >= 45,
                    sample.File + ": " + sample.Words + " слів — найшвидший учень (" + fastest +
                    " сл./хв) прочитає його менш ніж за 45 секунд, і замір втратить сенс.");
            }
        }

        [TestMethod]
        public void SampleTexts_CoverApostrophesAndHyphens()
        {
            // Зразки заодно перевіряють правила 4.1–4.2 на живому тексті:
            // хоча б один із них має містити слово зі сполучником.
            var folder = Path.GetDirectoryName(FindSharedFile("index.json"));
            var withJoiner = 0;

            foreach (var sample in LoadShared().Samples)
            {
                var text = File.ReadAllText(Path.Combine(folder, sample.File));

                if (TextStatsCalculator.GetWords(text).Any(w => w.Text.Any(TextStatsCalculator.IsJoiner)))
                {
                    withJoiner++;
                }
            }

            Assert.IsTrue(withJoiner > 0,
                "Жоден зразок не містить слова з апострофом чи дефісом — правила 4.2 не перевіряються на живому тексті.");
        }

        [TestMethod]
        public void EmbeddedIndex_MatchesSharedFile()
        {
            SamplesCatalog embedded = null;
            StaRunner.Run(() => embedded = SamplesLoader.Load());

            Assert.IsNotNull(embedded);
            Assert.IsFalse(embedded.IsEmpty,
                "Вбудований реєстр зразків порожній — перевірте Resource-маску в ReadFlow.csproj.");

            var shared = LoadShared();

            CollectionAssert.AreEqual(
                shared.Samples.Select(s => s.File).ToArray(),
                embedded.Samples.Select(s => s.File).ToArray(),
                "Список зразків у збірці розійшовся з shared/samples/index.json.");
        }

        [TestMethod]
        public void EmbeddedBodies_AreReadable()
        {
            SamplesCatalog embedded = null;
            StaRunner.Run(() => embedded = SamplesLoader.Load());

            foreach (var sample in embedded.Samples)
            {
                var current = sample;
                string body = null;
                StaRunner.Run(() => body = SamplesLoader.LoadBody(current));

                Assert.IsFalse(string.IsNullOrWhiteSpace(body),
                    "Текст зразка " + current.File + " не прочитався зі збірки.");
                Assert.AreEqual(current.Words, TextStatsCalculator.GetWords(body).Count,
                    "У вбудованому " + current.File + " інша кількість слів.");
            }
        }

        // ── Стійкість до поганих даних ────────────────────────────────────

        [TestMethod]
        public void Parse_BrokenJson_ReturnsNull()
        {
            Assert.IsNull(SamplesLoader.Parse("{ не json"));
            Assert.IsNull(SamplesLoader.Parse(string.Empty));
            Assert.IsNull(SamplesLoader.Parse(null));
        }

        [TestMethod]
        public void Parse_SampleWithUnknownLevel_IsSkipped()
        {
            var json = "{ \"version\": 1, \"levels\": [ { \"id\": \"easy\", \"label\": \"Простий\" } ], " +
                       "\"samples\": [" +
                       "  { \"id\": \"a\", \"title\": \"Є\", \"file\": \"a.txt\", \"level\": \"easy\", \"words\": 100 }," +
                       "  { \"id\": \"b\", \"title\": \"Немає\", \"file\": \"b.txt\", \"level\": \"???\", \"words\": 100 } ] }";

            var catalog = SamplesLoader.Parse(json);

            Assert.IsNotNull(catalog);
            Assert.AreEqual(1, catalog.Samples.Count, "Зразок із невідомим рівнем має бути пропущений.");
            Assert.AreEqual("a.txt", catalog.Samples[0].File);
        }

        [TestMethod]
        public void Parse_IncompleteSample_IsSkipped()
        {
            var json = "{ \"version\": 1, \"levels\": [ { \"id\": \"easy\", \"label\": \"Простий\" } ], " +
                       "\"samples\": [ { \"id\": \"a\", \"title\": \"Без файлу\", \"level\": \"easy\", \"words\": 100 } ] }";

            Assert.IsNull(SamplesLoader.Parse(json),
                "Реєстр без жодного придатного зразка не є реєстром.");
        }

        // ── Приблизний час читання ────────────────────────────────────────

        [TestMethod]
        public void MinutesToRead_RoundsAwayFromZero()
        {
            Assert.AreEqual(2, SpeedCalculator.MinutesToRead(110, 55));
            Assert.AreEqual(2, SpeedCalculator.MinutesToRead(83, 55), "1.51 округлюється вгору.");
            Assert.AreEqual(1, SpeedCalculator.MinutesToRead(81, 55), "1.47 округлюється вниз.");

            // Рівно половина йде вгору — те саме правило, що в 4.4 і 4.7.
            Assert.AreEqual(2, SpeedCalculator.MinutesToRead(150, 100));
        }

        [TestMethod]
        public void MinutesToRead_NeverShowsZeroMinutes()
        {
            // 10 слів на 95 сл./хв — це 6 секунд, але «≈ 0 хв» нічого не каже.
            Assert.AreEqual(1, SpeedCalculator.MinutesToRead(10, 95));
        }

        [TestMethod]
        public void MinutesToRead_GuardsAgainstNonsense()
        {
            Assert.AreEqual(0, SpeedCalculator.MinutesToRead(0, 55));
            Assert.AreEqual(0, SpeedCalculator.MinutesToRead(100, 0));
            Assert.AreEqual(0, SpeedCalculator.MinutesToRead(-5, 55));
        }

        // ── Поділ на абзаци для друку ─────────────────────────────────────

        [TestMethod]
        public void GetParagraphs_FollowsTheSelectedMode()
        {
            const string text = "А\nБ\n\nВ";

            CollectionAssert.AreEqual(
                new[] { "А", "Б", "В" },
                TextStatsCalculator.GetParagraphs(text, ParagraphMode.NonEmptyLines).ToArray());

            // У режимі блоків сусідні рядки — жорсткі переноси всередині абзацу,
            // тож на аркуші вони мають злитися в один рядок тексту.
            CollectionAssert.AreEqual(
                new[] { "А Б", "В" },
                TextStatsCalculator.GetParagraphs(text, ParagraphMode.BlankLineSeparated).ToArray());
        }

        /// <summary>
        /// Поділ і підрахунок абзаців мають лишатися одним правилом: аркуш,
        /// розданий учням, поділений так само, як показує нижня панель.
        /// </summary>
        [TestMethod]
        public void GetParagraphs_CountMatchesStatistics()
        {
            var texts = new[]
            {
                string.Empty,
                "Одне речення.",
                "А\nБ\n\nВ",
                "\n\n\n",
                "  \nТекст\r\n\r\nДругий блок\nз двох рядків\n",
                "Крайній рядок без переносу"
            };

            foreach (var text in texts)
            {
                foreach (ParagraphMode mode in Enum.GetValues(typeof(ParagraphMode)))
                {
                    var options = new CountingOptions { Paragraphs = mode };
                    var expected = TextStatsCalculator.Calculate(text, options).ParagraphCount;
                    var actual = TextStatsCalculator.GetParagraphs(text, mode).Count;

                    Assert.AreEqual(expected, actual,
                        "Режим " + mode + ", текст «" + text.Replace("\n", "\\n").Replace("\r", "\\r") + "».");
                }
            }
        }

        [TestMethod]
        public void GetParagraphs_TrimsAndDropsEmptyLines()
        {
            CollectionAssert.AreEqual(
                new[] { "Текст" },
                TextStatsCalculator.GetParagraphs("   \n  Текст  \n   \n", ParagraphMode.NonEmptyLines).ToArray());
        }

        // ── ViewModel ─────────────────────────────────────────────────────

        [TestMethod]
        public void ViewModel_ExposesSamplesFromCatalog()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                Assert.IsTrue(viewModel.HasSamples);
                Assert.AreEqual(LoadShared().Samples.Count, viewModel.Samples.Count);
                Assert.IsTrue(viewModel.Samples.All(s => s.InsertCommand != null),
                    "Кожен пункт меню має власну команду вставки.");
                Assert.IsTrue(viewModel.Samples.All(s => !string.IsNullOrWhiteSpace(s.LevelLabel)),
                    "Рівень має бути підписаний у кожному пункті.");
            });
        }

        [TestMethod]
        public void ViewModel_InsertSample_FillsTextAndStatistics()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();
                var option = viewModel.Samples.First();

                option.InsertCommand.Execute(null);

                Assert.IsTrue(viewModel.HasText);
                Assert.AreEqual(option.Sample.Words, viewModel.Stats.WordCount,
                    "Статистика має оновитися одразу, не чекаючи дебаунсу.");
            });
        }

        [TestMethod]
        public void ViewModel_InsertSample_ResetsPreviousMeasurement()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel
                {
                    Text = "один два три",
                    IsClickStopMode = true
                };

                viewModel.RecalculateNow();
                viewModel.SetBoundaryCommand.Execute(2);
                Assert.IsTrue(viewModel.HasResult);

                viewModel.Samples.First().InsertCommand.Execute(null);

                Assert.IsFalse(viewModel.HasResult, "Новий текст — новий замір.");
                Assert.IsFalse(viewModel.HasBoundary, "Межа від попереднього тексту не має лишатися.");
            });
        }

        [TestMethod]
        public void ViewModel_TimeHint_FollowsSelectedGrade()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Grade = 0 };

                // Без класу порівнювати нема з чим — показуємо кількість слів.
                var withoutGrade = viewModel.Samples.First().TimeText;
                StringAssert.Contains(withoutGrade, viewModel.Samples.First().Sample.Words.ToString(),
                    "Без обраного класу підпис має показувати кількість слів.");

                viewModel.Semester = 2;
                viewModel.Grade = 2;

                var withGrade = viewModel.Samples.First().TimeText;

                Assert.AreNotEqual(withoutGrade, withGrade,
                    "З обраним класом підпис має стати приблизним часом читання.");
                StringAssert.Contains(withGrade, "хв");
            });
        }

        [TestMethod]
        public void ViewModel_ClearText_EmptiesFieldAndDisablesItself()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel { Text = "Якийсь текст" };

                Assert.IsTrue(viewModel.ClearTextCommand.CanExecute(null));

                viewModel.ClearTextCommand.Execute(null);

                Assert.AreEqual(string.Empty, viewModel.Text);
                Assert.IsFalse(viewModel.HasText);
                Assert.IsFalse(viewModel.ClearTextCommand.CanExecute(null),
                    "На порожньому тексті кнопка «Очистити» має бути неактивною.");
            });
        }

        [TestMethod]
        public void ViewModel_HasText_IgnoresWhitespaceOnly()
        {
            StaRunner.Run(() =>
            {
                // Друкувати аркуш із самих пробілів немає сенсу.
                var viewModel = new MainViewModel { Text = "   \n\t  " };

                Assert.IsFalse(viewModel.HasText);
            });
        }

        // ── Допоміжне ─────────────────────────────────────────────────────

        private static SamplesCatalog LoadShared()
        {
            var path = FindSharedFile("index.json");
            var catalog = SamplesLoader.Parse(File.ReadAllText(path));

            Assert.IsNotNull(catalog, "Не вдалося розібрати " + path);
            return catalog;
        }

        /// <summary>Довідник норм із дерева вихідників — потрібен для перевірки довжини зразків.</summary>
        private static NormsCatalog LoadNorms()
        {
            var samples = new DirectoryInfo(Path.GetDirectoryName(FindSharedFile("index.json")));
            var path = Path.Combine(samples.Parent.FullName, NormsLoader.FileName);
            var catalog = NormsLoader.LoadFromFile(path);

            Assert.IsNotNull(catalog, "Не вдалося прочитати " + path);
            return catalog;
        }

        private static string FindSharedFile(string name)
        {
            var directory = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory);

            while (directory != null)
            {
                var candidate = Path.Combine(directory.FullName, "shared", "samples", name);

                if (File.Exists(candidate))
                {
                    return candidate;
                }

                directory = directory.Parent;
            }

            Assert.Fail("Не вдалося знайти shared\\samples\\" + name +
                        " від " + AppDomain.CurrentDomain.BaseDirectory);
            return null;
        }
    }
}

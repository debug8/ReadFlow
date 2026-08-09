using System;
using System.IO;
using System.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Норми техніки читання (Задача 10, специфікація 4.9).
    ///
    /// Тести читають <c>shared/norms.json</c> із дерева вихідників — так вони
    /// бачать правку норм, зроблену руками, а не копію, що лишилася в bin.
    /// Окремий тест порівнює вбудований у збірку ресурс із цим файлом: якщо
    /// лінк у csproj відпаде, застосунок працюватиме зі старими нормами
    /// тихо, і спіймати це можна лише так.
    /// </summary>
    [TestClass]
    public class ReadingNormsTests
    {
        // ── Довідник ──────────────────────────────────────────────────────

        [TestMethod]
        public void SharedNorms_ParseIntoFourGrades()
        {
            var catalog = LoadShared();

            Assert.IsFalse(catalog.IsEmpty, "Довідник норм не прочитався.");
            CollectionAssert.AreEqual(
                new[] { 1, 2, 3, 4 },
                catalog.Grades.Select(g => g.Grade).ToArray(),
                "У довіднику мають бути класи 1–4 у порядку зростання.");
        }

        [TestMethod]
        public void SharedNorms_LabelsComeFromFile()
        {
            var catalog = LoadShared();

            // Підпис класу не збирається в коді: він у norms.json, той самий
            // для десктопа й Android.
            Assert.AreEqual("2 клас", catalog.Grades.First(g => g.Grade == 2).Label);

            Assert.AreEqual("нижче норми", catalog.Labels.Below);
            Assert.AreEqual("у межах норми", catalog.Labels.Within);
            Assert.AreEqual("вище норми", catalog.Labels.Above);
        }

        /// <summary>
        /// Джерело й застереження показуються вчителю у вікні «Норми читання».
        /// Порожні — і вікно мовчки подасть норми як безумовну істину, хоча
        /// в НУШ вони рекомендаційні.
        /// </summary>
        [TestMethod]
        public void SharedNorms_HaveSourceAndNote()
        {
            var catalog = LoadShared();

            Assert.IsFalse(string.IsNullOrWhiteSpace(catalog.Source),
                "У norms.json має бути поле source — звідки взяті норми.");
            Assert.IsFalse(string.IsNullOrWhiteSpace(catalog.Note),
                "У norms.json має бути поле note — застереження до норм.");
            StringAssert.Contains(catalog.Source, "МОН",
                "Джерело норм має посилатися на наказ МОН.");
        }

        [TestMethod]
        public void Parse_WithoutSourceAndNote_LeavesThemEmpty()
        {
            // Обидва поля необовʼязкові: старий довідник без них має читатися,
            // а вікно норм — просто не показувати відповідні рядки.
            var json = "{ \"version\": 1, \"grades\": [ { \"grade\": 1, \"label\": \"1 клас\", " +
                       "\"semesters\": [ { \"semester\": 1, \"min\": 10, \"max\": 20 } ] } ] }";

            var catalog = NormsLoader.Parse(json);

            Assert.IsNotNull(catalog);
            Assert.AreEqual(string.Empty, catalog.Source);
            Assert.AreEqual(string.Empty, catalog.Note);
        }

        /// <summary>
        /// Інтерфейс має рівно дві радіокнопки семестрів. Якщо в довіднику
        /// колись зʼявиться третій, цей тест почервоніє — і це правильно:
        /// правити треба розмітку, а не мовчки втрачати норму.
        /// </summary>
        [TestMethod]
        public void SharedNorms_EveryGradeHasExactlyTwoSemesters()
        {
            foreach (var grade in LoadShared().Grades)
            {
                CollectionAssert.AreEquivalent(
                    new[] { 1, 2 },
                    grade.Semesters.Select(s => s.Semester).ToArray(),
                    "Клас " + grade.Grade + ": очікуються семестри 1 і 2.");
            }
        }

        [TestMethod]
        public void SharedNorms_KnownValues()
        {
            var catalog = LoadShared();

            AssertNorm(catalog, 1, 1, 10, 20);
            AssertNorm(catalog, 1, 2, 20, 30);
            AssertNorm(catalog, 4, 2, 90, 95);
        }

        [TestMethod]
        public void SharedNorms_RangesAreAscendingWithinGrade()
        {
            // Норма другого семестру не може бути нижчою за перший — учень
            // за півроку не починає читати повільніше.
            foreach (var grade in LoadShared().Grades)
            {
                var first = grade.Semesters.Single(s => s.Semester == 1);
                var second = grade.Semesters.Single(s => s.Semester == 2);

                Assert.IsTrue(second.Min >= first.Min && second.Max >= first.Max,
                    "Клас " + grade.Grade + ": норма II семестру нижча за I.");
                Assert.IsTrue(first.Min <= first.Max && second.Min <= second.Max,
                    "Клас " + grade.Grade + ": межі норми перевернуті.");
            }
        }

        [TestMethod]
        public void EmbeddedNorms_MatchSharedFile()
        {
            NormsCatalog embedded = null;
            StaRunner.Run(() => embedded = NormsLoader.LoadFromResource());

            Assert.IsNotNull(embedded,
                "Вбудований norms.json не прочитався — перевірте Resource-лінк у ReadFlow.csproj.");

            var shared = LoadShared();

            Assert.AreEqual(shared.Grades.Count, embedded.Grades.Count,
                "Кількість класів у вбудованому довіднику розійшлася з shared/norms.json.");

            Assert.AreEqual(shared.Source, embedded.Source,
                "Джерело норм у вбудованому довіднику розійшлося з shared/norms.json.");

            foreach (var grade in shared.Grades)
            {
                foreach (var norm in grade.Semesters)
                {
                    var actual = embedded.Find(norm.Grade, norm.Semester);

                    Assert.IsNotNull(actual,
                        "У вбудованому довіднику немає норми класу " + norm.Grade +
                        ", семестр " + norm.Semester + ".");
                    Assert.AreEqual(norm.Min, actual.Min, "Класу " + norm.Grade + " не збігається Min.");
                    Assert.AreEqual(norm.Max, actual.Max, "Класу " + norm.Grade + " не збігається Max.");
                }
            }
        }

        // ── Правило оцінки ────────────────────────────────────────────────

        [TestMethod]
        public void Evaluate_BelowWithinAbove()
        {
            var catalog = LoadShared();

            // 2 клас, II семестр — 50–60.
            Assert.AreEqual(NormEvaluation.Below, catalog.Evaluate(49, 2, 2));
            Assert.AreEqual(NormEvaluation.Within, catalog.Evaluate(55, 2, 2));
            Assert.AreEqual(NormEvaluation.Above, catalog.Evaluate(61, 2, 2));
        }

        /// <summary>
        /// Межі входять у норму. Учень, який прочитав рівно 50 слів за норми
        /// 50–60, читає в нормі, а не нижче — інакше нижня межа означала б
        /// «строго більше», чого в наказі МОН немає.
        /// </summary>
        [TestMethod]
        public void Evaluate_BoundariesAreInclusive()
        {
            var catalog = LoadShared();

            Assert.AreEqual(NormEvaluation.Within, catalog.Evaluate(50, 2, 2), "Min має входити в норму.");
            Assert.AreEqual(NormEvaluation.Within, catalog.Evaluate(60, 2, 2), "Max має входити в норму.");
        }

        [TestMethod]
        public void Evaluate_UnknownGradeOrSemester_IsUnknown()
        {
            var catalog = LoadShared();

            Assert.AreEqual(NormEvaluation.Unknown, catalog.Evaluate(55, 0, 2), "Клас не обраний.");
            Assert.AreEqual(NormEvaluation.Unknown, catalog.Evaluate(55, 9, 2), "Такого класу немає.");
            Assert.AreEqual(NormEvaluation.Unknown, catalog.Evaluate(55, 2, 3), "Такого семестру немає.");
        }

        [TestMethod]
        public void Evaluate_ZeroSpeed_IsBelow()
        {
            // Замір є, слів прочитано 0 — це нижче норми, а не «невідомо».
            Assert.AreEqual(NormEvaluation.Below, LoadShared().Evaluate(0, 1, 1));
        }

        [TestMethod]
        public void Describe_UnknownHasNoLabel()
        {
            var catalog = LoadShared();

            Assert.AreEqual(string.Empty, catalog.Describe(NormEvaluation.Unknown));
            Assert.AreEqual(catalog.Labels.Within, catalog.Describe(NormEvaluation.Within));
        }

        [TestMethod]
        public void EmptyCatalog_EvaluatesToUnknown_AndDoesNotThrow()
        {
            Assert.AreEqual(NormEvaluation.Unknown, NormsCatalog.Empty.Evaluate(55, 2, 2));
            Assert.IsTrue(NormsCatalog.Empty.IsEmpty);
            Assert.IsFalse(NormsCatalog.Empty.HasGrade(2));
        }

        // ── Стійкість до поганих даних ────────────────────────────────────

        [TestMethod]
        public void Parse_BrokenJson_ReturnsNullInsteadOfThrowing()
        {
            Assert.IsNull(NormsLoader.Parse("{ це не json"));
            Assert.IsNull(NormsLoader.Parse(string.Empty));
            Assert.IsNull(NormsLoader.Parse(null));
        }

        [TestMethod]
        public void Parse_NewerVersion_IsRefused()
        {
            // Довідник новішого формату читати навмання не можна: краще без
            // оцінки, ніж з числом, зрозумілим не так, як задумано.
            var json = "{ \"version\": " + (NormsLoader.SupportedVersion + 1) +
                       ", \"grades\": [ { \"grade\": 1, \"label\": \"1 клас\", " +
                       "\"semesters\": [ { \"semester\": 1, \"min\": 10, \"max\": 20 } ] } ] }";

            Assert.IsNull(NormsLoader.Parse(json));
        }

        [TestMethod]
        public void Parse_NoGrades_ReturnsNull()
        {
            Assert.IsNull(NormsLoader.Parse("{ \"version\": 1, \"grades\": [] }"));
        }

        [TestMethod]
        public void Parse_InvertedRange_IsSkipped_ButRestSurvives()
        {
            var json = "{ \"version\": 1, \"grades\": [" +
                       "{ \"grade\": 1, \"label\": \"1 клас\", \"semesters\": [" +
                       "  { \"semester\": 1, \"min\": 30, \"max\": 10 }," +
                       "  { \"semester\": 2, \"min\": 20, \"max\": 30 } ] } ] }";

            var catalog = NormsLoader.Parse(json);

            Assert.IsNotNull(catalog);
            Assert.IsNull(catalog.Find(1, 1), "Норму з переверненими межами треба пропустити.");
            Assert.IsNotNull(catalog.Find(1, 2), "Решта норм класу має лишитися.");
        }

        [TestMethod]
        public void Parse_MissingEvaluationLabels_FallsBackToDefaults()
        {
            var json = "{ \"version\": 1, \"grades\": [" +
                       "{ \"grade\": 1, \"label\": \"1 клас\", \"semesters\": [" +
                       "  { \"semester\": 1, \"min\": 10, \"max\": 20 } ] } ] }";

            var catalog = NormsLoader.Parse(json);

            Assert.IsNotNull(catalog);
            Assert.AreEqual(NormLabels.DefaultWithin, catalog.Labels.Within,
                "Без блоку evaluation підпис має бути запасний, а не порожній.");
        }

        [TestMethod]
        public void Parse_IgnoresUnknownFields()
        {
            // У справжньому norms.json є "$comment" і "unit" — парсер не має
            // на них спотикатися.
            var json = "{ \"$comment\": \"текст\", \"version\": 1, \"unit\": \"wordsPerMinute\", " +
                       "\"grades\": [ { \"grade\": 1, \"label\": \"1 клас\", \"note\": \"зайве\", " +
                       "\"semesters\": [ { \"semester\": 1, \"min\": 10, \"max\": 20 } ] } ] }";

            var catalog = NormsLoader.Parse(json);

            Assert.IsNotNull(catalog);
            Assert.IsNotNull(catalog.Find(1, 1));
        }

        [TestMethod]
        public void Parse_HandlesByteOrderMark()
        {
            // Файл, збережений Блокнотом, має BOM. Учитель правитиме норми
            // саме так, і застосунок не має від цього осліпнути.
            var path = Path.Combine(Path.GetTempPath(), "readflow-norms-bom-" + Guid.NewGuid().ToString("N") + ".json");

            try
            {
                File.WriteAllText(
                    path,
                    "{ \"version\": 1, \"grades\": [ { \"grade\": 1, \"label\": \"1 клас\", " +
                    "\"semesters\": [ { \"semester\": 1, \"min\": 10, \"max\": 20 } ] } ] }",
                    new System.Text.UTF8Encoding(true));

                var catalog = NormsLoader.LoadFromFile(path);

                Assert.IsNotNull(catalog, "Файл із BOM має читатися.");
                Assert.IsNotNull(catalog.Find(1, 1));
            }
            finally
            {
                try
                {
                    File.Delete(path);
                }
                catch (IOException)
                {
                    // Не змогли прибрати за собою — це не привід валити тест.
                }
            }
        }

        [TestMethod]
        public void LoadFromFile_MissingFile_ReturnsNull()
        {
            Assert.IsNull(NormsLoader.LoadFromFile(Path.Combine(Path.GetTempPath(), "readflow-no-such-file.json")));
            Assert.IsNull(NormsLoader.LoadFromFile(null));
        }

        [TestMethod]
        public void Load_WithoutExternalFile_FallsBackToEmbedded()
        {
            // Поруч із testhost.exe жодного norms.json немає, тож Load мусить
            // дійти до вбудованого ресурсу.
            NormsCatalog catalog = null;
            StaRunner.Run(() => catalog = NormsLoader.Load());

            Assert.IsNotNull(catalog);
            Assert.IsFalse(catalog.IsEmpty, "Load має повернути вбудований довідник.");
        }

        // ── ViewModel ─────────────────────────────────────────────────────

        [TestMethod]
        public void ViewModel_ShowsGradesFromCatalog()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                Assert.IsTrue(viewModel.HasNormsCatalog);
                Assert.AreEqual(4, viewModel.Grades.Count);
            });
        }

        [TestMethod]
        public void ViewModel_WithoutGrade_HasNoNormStatus()
        {
            StaRunner.Run(() =>
            {
                var viewModel = Measured(0, 2);

                Assert.IsFalse(viewModel.IsGradeSelected);
                Assert.IsFalse(viewModel.HasNormStatus, "Без обраного класу оцінки бути не має.");
                Assert.AreEqual(string.Empty, viewModel.NormStatusText);
                Assert.AreEqual(string.Empty, viewModel.NormRangeText);
            });
        }

        [TestMethod]
        public void ViewModel_EvaluatesAgainstSelectedGradeAndSemester()
        {
            StaRunner.Run(() =>
            {
                // 10 слів за 30 с = 20 WPM. Оцінка має рахуватися саме за парою
                // «клас + семестр», тому міняємо обидва по черзі.
                var viewModel = Measured(1, 1);

                Assert.AreEqual(20, viewModel.WordsPerMinute);

                // 1 клас, I семестр — 10–20: 20 це верхня межа, а вона входить у норму.
                Assert.AreEqual(NormEvaluation.Within, viewModel.NormStatus);
                Assert.AreEqual("10–20", viewModel.NormRangeText);

                // 2 клас, I семестр — 35–45. Семестр лишається першим: змінили
                // тільки клас, тож і норма має змінитися тільки за класом.
                viewModel.Grade = 2;

                Assert.AreEqual(NormEvaluation.Below, viewModel.NormStatus);
                Assert.AreEqual("35–45", viewModel.NormRangeText);

                // 2 клас, II семестр — 50–60.
                viewModel.Semester = 2;

                Assert.AreEqual(NormEvaluation.Below, viewModel.NormStatus);
                Assert.AreEqual("50–60", viewModel.NormRangeText);
            });
        }

        [TestMethod]
        public void ViewModel_ChangingSemester_ChangesNormWithoutNewMeasurement()
        {
            StaRunner.Run(() =>
            {
                // 3 клас: I семестр 65–70, II — 75–80. 20 WPM нижче обох,
                // але діапазон на екрані мусить змінитися одразу.
                var viewModel = Measured(3, 1);
                Assert.AreEqual("65–70", viewModel.NormRangeText);

                viewModel.Semester = 2;

                Assert.IsTrue(viewModel.IsSecondSemester);
                Assert.IsFalse(viewModel.IsFirstSemester);
                Assert.AreEqual("75–80", viewModel.NormRangeText);
            });
        }

        [TestMethod]
        public void ViewModel_ChangingTimerSeconds_UpdatesNormStatus()
        {
            StaRunner.Run(() =>
            {
                // Режим A бере в формулу задану тривалість (4.8), тож зміна
                // тривалості міняє WPM — а за ним і оцінку, без нового заміру.
                var viewModel = Measured(1, 1);
                Assert.AreEqual(NormEvaluation.Within, viewModel.NormStatus);

                // 10 слів за 20 с = 30 WPM, а норма 1 класу в I семестрі — 10–20.
                viewModel.TimerSeconds = 20;

                Assert.AreEqual(30, viewModel.WordsPerMinute);
                Assert.AreEqual(NormEvaluation.Above, viewModel.NormStatus);
            });
        }

        [TestMethod]
        public void ViewModel_UnknownGrade_IsTreatedAsNotSelected()
        {
            StaRunner.Run(() =>
            {
                var viewModel = new MainViewModel();

                viewModel.Grade = 9;

                Assert.AreEqual(0, viewModel.Grade);
                Assert.IsFalse(viewModel.IsGradeSelected);
            });
        }

        [TestMethod]
        public void ViewModel_NormStatus_DisappearsWithResult()
        {
            StaRunner.Run(() =>
            {
                var viewModel = Measured(1, 1);
                Assert.IsTrue(viewModel.HasNormStatus);

                viewModel.ResetMeasurementCommand.Execute(null);

                Assert.IsFalse(viewModel.HasNormStatus, "Скинули замір — оцінка теж зникає.");
            });
        }

        [TestMethod]
        public void Settings_GradeAndSemesterDefaults()
        {
            // Читаємо .settings із дерева вихідників: файл правиться руками,
            // дизайнер VS у сесіях ШІ недоступний, і розʼїхатися тут легко.
            var settings = System.Xml.Linq.XDocument.Load(FindSourceFile("ReadFlow", "Properties", "Settings.settings"));

            Assert.AreEqual("0", ReadDefault(settings, "Grade"),
                "За замовчуванням клас не обраний.");
            Assert.AreEqual(
                MainViewModel.DefaultSemester.ToString(System.Globalization.CultureInfo.InvariantCulture),
                ReadDefault(settings, "Semester"),
                "Семестр за замовчуванням має збігатися з MainViewModel.DefaultSemester.");
        }

        // ── Допоміжне ─────────────────────────────────────────────────────

        /// <summary>Замір із відомим результатом: 10 слів за 30 с = 20 WPM.</summary>
        private static MainViewModel Measured(int grade, int semester)
        {
            var viewModel = new MainViewModel
            {
                Text = "один два три чотири пʼять шість сім вісім девʼять десять",
                TimerSeconds = 30,
                IsClickStopMode = true,
                Semester = semester,
                Grade = grade
            };

            viewModel.RecalculateNow();

            // Режим A: межа на останньому слові й дає завершений підсумок,
            // час у формулу йде заданий (специфікація, 4.8).
            viewModel.SetBoundaryCommand.Execute(10);

            Assert.IsTrue(viewModel.HasResult, "Підсумок заміру не зʼявився.");
            return viewModel;
        }

        private static void AssertNorm(NormsCatalog catalog, int grade, int semester, int min, int max)
        {
            var norm = catalog.Find(grade, semester);

            Assert.IsNotNull(norm, "Немає норми класу " + grade + ", семестр " + semester + ".");
            Assert.AreEqual(min, norm.Min, "Клас " + grade + ", семестр " + semester + ": Min.");
            Assert.AreEqual(max, norm.Max, "Клас " + grade + ", семестр " + semester + ": Max.");
        }

        private static NormsCatalog LoadShared()
        {
            var path = FindSharedNorms();
            var catalog = NormsLoader.LoadFromFile(path);

            Assert.IsNotNull(catalog, "Не вдалося прочитати " + path);
            return catalog;
        }

        private static string ReadDefault(System.Xml.Linq.XDocument document, string name)
        {
            var setting = document.Descendants()
                .FirstOrDefault(e => e.Name.LocalName == "Setting" &&
                                     (string)e.Attribute("Name") == name);

            Assert.IsNotNull(setting, "У Settings.settings немає налаштування " + name + ".");

            var value = setting.Elements().FirstOrDefault(e => e.Name.LocalName == "Value");

            Assert.IsNotNull(value, "У налаштування " + name + " немає значення за замовчуванням.");
            return value.Value.Trim();
        }

        /// <summary>
        /// Шукає <c>shared/norms.json</c>, піднімаючись від папки збірки:
        /// у тестах немає ані робочої папки проєкту, ані самого проєкту.
        /// </summary>
        private static string FindSharedNorms()
        {
            var directory = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory);

            while (directory != null)
            {
                var candidate = Path.Combine(directory.FullName, "shared", NormsLoader.FileName);
                if (File.Exists(candidate))
                {
                    return candidate;
                }

                directory = directory.Parent;
            }

            Assert.Fail("Не вдалося знайти shared\\" + NormsLoader.FileName +
                        " від " + AppDomain.CurrentDomain.BaseDirectory);
            return null;
        }

        /// <summary>Файл у дереві вихідників десктопа, від папки <c>desktop/</c>.</summary>
        private static string FindSourceFile(params string[] parts)
        {
            var directory = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory);

            while (directory != null)
            {
                var candidate = Path.Combine(directory.FullName, Path.Combine(parts));
                if (File.Exists(candidate))
                {
                    return candidate;
                }

                directory = directory.Parent;
            }

            Assert.Fail("Не вдалося знайти " + Path.Combine(parts) +
                        " від " + AppDomain.CurrentDomain.BaseDirectory);
            return null;
        }
    }
}

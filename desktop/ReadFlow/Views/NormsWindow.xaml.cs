using System.Collections.Generic;
using System.Globalization;
using System.Windows;
using ReadFlow.Core;

namespace ReadFlow.Views
{
    /// <summary>
    /// Довідка → Норми читання: усі норми з <c>norms.json</c> одразу, за класами
    /// й семестрами.
    ///
    /// Навіщо окреме вікно: у панелі налаштувань учитель бачить лише свій клас,
    /// а норму сусіднього — ні. Питання «а скільки має читати третій?» виникає
    /// щоразу, коли вчитель веде більш ніж один клас.
    ///
    /// Жодного числа й жодного підпису класу тут не зашито: усе приходить із
    /// довідника. Учитель, який поправив <c>norms.json</c>, побачить у цьому
    /// вікні саме свої значення — інакше воно брехало б.
    /// </summary>
    public partial class NormsWindow : Window
    {
        private const string RangeSeparator = "–";

        /// <summary>Прочерк у клітинці, для якої норми в довіднику немає.</summary>
        private const string NoValue = "—";

        private const int FirstSemester = 1;
        private const int SecondSemester = 2;

        /// <param name="catalog">Довідник норм. Порожній — вікно чесно про це скаже.</param>
        /// <param name="grade">Обраний клас або 0. Його норма підсвічується.</param>
        /// <param name="semester">Обраний семестр.</param>
        public NormsWindow(NormsCatalog catalog, int grade, int semester)
        {
            InitializeComponent();

            var norms = catalog ?? NormsCatalog.Empty;

            PathText.Text = NormsLoader.ExternalPath ?? string.Empty;

            if (norms.IsEmpty)
            {
                TableSection.Visibility = Visibility.Collapsed;
                EmptyMessage.Visibility = Visibility.Visible;
                return;
            }

            Rows.ItemsSource = BuildRows(norms, grade, semester);

            // Підказка про підсвітку має сенс лише коли є що підсвічувати.
            ActiveHint.Visibility = norms.Find(grade, semester) == null
                ? Visibility.Collapsed
                : Visibility.Visible;

            ShowIfPresent(SourceText, norms.Source);
            ShowIfPresent(NoteText, norms.Note);
        }

        private static List<NormRow> BuildRows(NormsCatalog catalog, int grade, int semester)
        {
            var rows = new List<NormRow>();

            foreach (var item in catalog.Grades)
            {
                var isActiveGrade = item.Grade == grade;

                rows.Add(new NormRow(
                    item.Label,
                    Describe(item, FirstSemester),
                    Describe(item, SecondSemester),
                    isActiveGrade && semester == FirstSemester,
                    isActiveGrade && semester == SecondSemester));
            }

            return rows;
        }

        /// <summary>Діапазон норми як «50–60» або прочерк, якщо її немає.</summary>
        private static string Describe(GradeNorms grade, int semester)
        {
            foreach (var norm in grade.Semesters)
            {
                if (norm.Semester != semester)
                {
                    continue;
                }

                return norm.Min.ToString(CultureInfo.CurrentCulture) + RangeSeparator +
                       norm.Max.ToString(CultureInfo.CurrentCulture);
            }

            return NoValue;
        }

        /// <summary>
        /// Порожнє поле довідника не лишає порожнього рядка у вікні:
        /// відступ без тексту виглядає як недомальований інтерфейс.
        /// </summary>
        private static void ShowIfPresent(System.Windows.Controls.TextBlock target, string text)
        {
            if (string.IsNullOrWhiteSpace(text))
            {
                target.Visibility = Visibility.Collapsed;
                return;
            }

            target.Text = text;
            target.Visibility = Visibility.Visible;
        }

        private void OnCloseClick(object sender, RoutedEventArgs e)
        {
            Close();
        }

        /// <summary>Рядок таблиці: клас і його норми за обидва семестри.</summary>
        public sealed class NormRow
        {
            public NormRow(string gradeLabel, string firstText, string secondText,
                           bool isFirstActive, bool isSecondActive)
            {
                GradeLabel = gradeLabel;
                FirstText = firstText;
                SecondText = secondText;
                IsFirstActive = isFirstActive;
                IsSecondActive = isSecondActive;
            }

            public string GradeLabel { get; private set; }

            public string FirstText { get; private set; }

            public string SecondText { get; private set; }

            /// <summary>Норма I семестру цього класу — та, за якою оцінюється замір.</summary>
            public bool IsFirstActive { get; private set; }

            /// <summary>Те саме для II семестру.</summary>
            public bool IsSecondActive { get; private set; }
        }
    }
}

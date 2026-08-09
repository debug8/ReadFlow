using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using ReadFlow.Core;
using ReadFlow.ViewModels;
using ReadFlow.Views;

namespace ReadFlow
{
    /// <summary>
    /// Головне вікно: ліва панель налаштувань, центральна зона тексту,
    /// нижня панель статистики й керування.
    /// </summary>
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
        }

        /// <summary>
        /// Довідка → Про програму.
        ///
        /// Відкриття вікна лишається у View, а не в ViewModel: інакше ViewModel
        /// мусила б знати про класи вікон, і її не можна було б перевірити тестом
        /// без запуску інтерфейсу.
        /// </summary>
        private void OnAboutClick(object sender, RoutedEventArgs e)
        {
            var about = new AboutWindow { Owner = this };
            about.ShowDialog();
        }

        /// <summary>
        /// Довідка → Норми читання: усі норми одразу, за класами й семестрами.
        ///
        /// Обраний клас і семестр передаються, щоб підсвітити саме ту норму,
        /// за якою зараз оцінюється замір. Без ViewModel вікно теж відкриється —
        /// просто без підсвітки.
        /// </summary>
        private void OnNormsClick(object sender, RoutedEventArgs e)
        {
            var viewModel = DataContext as MainViewModel;

            var norms = new NormsWindow(
                NormsLoader.Current,
                viewModel == null ? 0 : viewModel.Grade,
                viewModel == null ? 0 : viewModel.Semester)
            {
                Owner = this
            };

            norms.ShowDialog();
        }

        /// <summary>
        /// Гарячі клавіші: Пробіл — старт і стоп заміру, Esc — скидання.
        ///
        /// Обробляються тут, а не через <c>InputBindings</c>, бо KeyBinding на вікні
        /// спрацював би й тоді, коли вчитель набирає текст: Пробіл запускав би замір
        /// і водночас вставляв пробіл у поле. Тому спершу перевіряємо, де фокус.
        /// </summary>
        private void OnWindowPreviewKeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key != Key.Space && e.Key != Key.Escape)
            {
                return;
            }

            // Поле вводу й будь-яке інше текстове поле мають пріоритет.
            if (Keyboard.FocusedElement is TextBoxBase)
            {
                return;
            }

            var viewModel = DataContext as MainViewModel;
            if (viewModel == null)
            {
                return;
            }

            if (e.Key == Key.Space)
            {
                viewModel.ToggleMeasurementCommand.Execute(null);
            }
            else
            {
                viewModel.ResetMeasurementCommand.Execute(null);
            }

            e.Handled = true;
        }
    }
}

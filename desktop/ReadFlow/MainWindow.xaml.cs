using System.Windows;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using ReadFlow.ViewModels;

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

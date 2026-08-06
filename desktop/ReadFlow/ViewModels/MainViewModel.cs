using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows.Threading;
using ReadFlow.Core;
using ReadFlow.Models;

namespace ReadFlow.ViewModels
{
    /// <summary>
    /// ViewModel головного вікна: текст, його статистика й вибір теми.
    /// Решта зʼявиться в наступних задачах (налаштування — 5, таймер — 6, режими — 6–8).
    /// </summary>
    public class MainViewModel : ViewModelBase
    {
        /// <summary>
        /// Пауза після останнього натискання клавіші перед перерахунком.
        /// Менше — перерахунок на кожну літеру; більше — вчитель бачить застарілі числа.
        /// </summary>
        private const int DebounceMilliseconds = 300;

        private readonly DispatcherTimer _recalculateTimer;

        // Режим підрахунку абзаців стане налаштуванням у Задачі 5.
        private readonly CountingOptions _countingOptions = new CountingOptions();

        private string _text = string.Empty;
        private TextStats _stats = TextStats.Empty;
        private ReadingDocument _document = ReadingDocument.Empty;
        private ThemeDescriptor _selectedTheme;
        private bool _isReadingMode;

        public MainViewModel()
        {
            // Background: ввід тексту важливіший за перерахунок статистики.
            _recalculateTimer = new DispatcherTimer(DispatcherPriority.Background)
            {
                Interval = TimeSpan.FromMilliseconds(DebounceMilliseconds)
            };
            _recalculateTimer.Tick += OnRecalculateTimerTick;

            Themes = ThemeManager.AvailableThemes;

            // Поле, а не властивість: при старті тема вже застосована в App.OnStartup,
            // повторно застосовувати її не треба.
            _selectedTheme = Themes.FirstOrDefault(
                                 t => string.Equals(t.Name, ThemeManager.CurrentThemeName, StringComparison.OrdinalIgnoreCase))
                             ?? Themes.FirstOrDefault();
        }

        /// <summary>Текст, який вчитель вставив або набрав.</summary>
        public string Text
        {
            get { return _text; }
            set
            {
                if (Set(ref _text, value ?? string.Empty))
                {
                    RestartDebounce();
                }
            }
        }

        /// <summary>Статистика тексту. Оновлюється через ~300 мс після зупинки набору.</summary>
        public TextStats Stats
        {
            get { return _stats; }
            private set { Set(ref _stats, value); }
        }

        /// <summary>
        /// Текст із розібраними словами для режиму читання.
        /// Оновлюється разом зі статистикою — з того самого розбору.
        /// </summary>
        public ReadingDocument Document
        {
            get { return _document; }
            private set { Set(ref _document, value); }
        }

        /// <summary>
        /// Режим читання (послівний перегляд) замість режиму редагування.
        /// </summary>
        public bool IsReadingMode
        {
            get { return _isReadingMode; }
            set
            {
                if (Set(ref _isReadingMode, value) && value)
                {
                    // Перемикання може статися до того, як спрацює дебаунс, — тоді
                    // вчитель побачив би нумерацію попереднього тексту. Тому дорахунок
                    // примусово, не чекаючи таймера.
                    RecalculateNow();
                }
            }
        }

        /// <summary>Список доступних тем. Формується з файлів у <c>Themes/</c>.</summary>
        public IReadOnlyList<ThemeDescriptor> Themes { get; private set; }

        /// <summary>Обрана тема. Зміна застосовується миттєво й запамʼятовується.</summary>
        public ThemeDescriptor SelectedTheme
        {
            get { return _selectedTheme; }
            set
            {
                if (Set(ref _selectedTheme, value) && value != null)
                {
                    ThemeManager.Apply(value.Name);
                }
            }
        }

        /// <summary>
        /// Перерахувати статистику негайно, не чекаючи дебаунсу.
        /// Потрібно, коли змінилося налаштування, а не текст, — і в тестах.
        /// </summary>
        public void RecalculateNow()
        {
            _recalculateTimer.Stop();

            var words = TextStatsCalculator.GetWords(_text);

            Stats = TextStatsCalculator.Calculate(_text, words, _countingOptions);
            Document = new ReadingDocument(_text, words);
        }

        private void RestartDebounce()
        {
            // Stop + Start скидає відлік: перерахунок буде через 300 мс
            // після ОСТАННЬОГО натискання, а не після першого.
            _recalculateTimer.Stop();
            _recalculateTimer.Start();
        }

        private void OnRecalculateTimerTick(object sender, EventArgs e)
        {
            RecalculateNow();
        }
    }
}

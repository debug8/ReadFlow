using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Linq;
using System.Windows.Input;
using System.Windows.Threading;
using ReadFlow.Core;
using ReadFlow.Models;
using ReadFlow.Properties;

namespace ReadFlow.ViewModels
{
    /// <summary>
    /// ViewModel головного вікна: текст, статистика й налаштування.
    /// Таймер і режими вимірювання зʼявляться в Задачах 6–8; тут поки що
    /// зберігається лише вибір режиму.
    /// </summary>
    public class MainViewModel : ViewModelBase
    {
        /// <summary>
        /// Пауза після останнього натискання клавіші перед перерахунком.
        /// Менше — перерахунок на кожну літеру; більше — вчитель бачить застарілі числа.
        /// </summary>
        private const int DebounceMilliseconds = 300;

        public const double MinFontSize = 10d;
        public const double MaxFontSize = 40d;
        public const double MinLineHeight = 12d;
        public const double MaxLineHeight = 60d;

        /// <summary>Менше 5 с замір втрачає сенс, більше години — це вже не урок.</summary>
        public const int MinTimerSeconds = 5;
        public const int MaxTimerSeconds = 3600;

        private readonly DispatcherTimer _recalculateTimer;
        private readonly CountingOptions _countingOptions = new CountingOptions();
        private readonly ReadingTimer _readingTimer = new ReadingTimer();

        private bool _hasResult;
        private int _wordsPerMinute;
        private int _charsPerMinute;
        private TimeSpan _resultElapsed;

        private string _text = string.Empty;
        private TextStats _stats = TextStats.Empty;
        private ReadingDocument _document = ReadingDocument.Empty;
        private ThemeDescriptor _selectedTheme;
        private bool _isReadingMode;

        // 0 означає «вчитель повзунка не чіпав» — діє значення з теми.
        private double _fontSizeOverride;
        private double _lineHeightOverride;

        private int _timerSeconds;
        private MeasurementMode _measurementMode;
        private bool _markErrors;
        private bool _isSettingsExpanded;

        public MainViewModel()
        {
            // Background: ввід тексту важливіший за перерахунок статистики.
            _recalculateTimer = new DispatcherTimer(DispatcherPriority.Background)
            {
                Interval = TimeSpan.FromMilliseconds(DebounceMilliseconds)
            };
            _recalculateTimer.Tick += OnRecalculateTimerTick;

            _readingTimer.Tick += OnReadingTimerTick;
            _readingTimer.DurationReached += OnDurationReached;

            SetTimerSecondsCommand = new RelayCommand<string>(OnSetTimerSeconds);
            UseThemeSizesCommand = new RelayCommand(OnUseThemeSizes, () => !UsesThemeSizes);
            ToggleSettingsCommand = new RelayCommand(() => IsSettingsExpanded = !IsSettingsExpanded);
            ToggleMeasurementCommand = new RelayCommand(ToggleMeasurement);
            ResetMeasurementCommand = new RelayCommand(ResetMeasurement);

            Themes = ThemeManager.AvailableThemes;

            // Поле, а не властивість: при старті тема вже застосована в App.OnStartup,
            // повторно застосовувати її не треба.
            _selectedTheme = Themes.FirstOrDefault(
                                 t => string.Equals(t.Name, ThemeManager.CurrentThemeName, StringComparison.OrdinalIgnoreCase))
                             ?? Themes.FirstOrDefault();

            LoadSettings();
        }

        // ── Текст і статистика ────────────────────────────────────────────

        /// <summary>Текст, який вчитель вставив або набрав.</summary>
        public string Text
        {
            get { return _text; }
            set
            {
                if (!Set(ref _text, value ?? string.Empty))
                {
                    return;
                }

                // Підсумок стосується конкретного тексту. Змінили текст —
                // старі WPM більше ні про що не свідчать.
                HasResult = false;
                RestartDebounce();
            }
        }

        /// <summary>Статистика тексту. Оновлюється через ~300 мс після зупинки набору.</summary>
        public TextStats Stats
        {
            get { return _stats; }
            private set { Set(ref _stats, value); }
        }

        /// <summary>Текст із розібраними словами для режиму читання.</summary>
        public ReadingDocument Document
        {
            get { return _document; }
            private set { Set(ref _document, value); }
        }

        /// <summary>Режим читання (послівний перегляд) замість режиму редагування.</summary>
        public bool IsReadingMode
        {
            get { return _isReadingMode; }
            set
            {
                if (Set(ref _isReadingMode, value) && value)
                {
                    // Перемикання може статися до того, як спрацює дебаунс, — тоді
                    // вчитель побачив би нумерацію попереднього тексту.
                    RecalculateNow();
                }
            }
        }

        // ── Оформлення ────────────────────────────────────────────────────

        public IReadOnlyList<ThemeDescriptor> Themes { get; private set; }

        /// <summary>Обрана тема. Зміна застосовується миттєво й запамʼятовується.</summary>
        public ThemeDescriptor SelectedTheme
        {
            get { return _selectedTheme; }
            set
            {
                if (!Set(ref _selectedTheme, value) || value == null)
                {
                    return;
                }

                ThemeManager.Apply(value.Name);

                // Якщо вчитель повзунків не чіпав, розміри беруться з теми — і після
                // перемикання вони інші, тож повзунки мають переїхати.
                if (_fontSizeOverride <= TextAppearance.UseThemeValue)
                {
                    OnPropertyChanged("FontSize");
                }

                if (_lineHeightOverride <= TextAppearance.UseThemeValue)
                {
                    OnPropertyChanged("LineHeight");
                }
            }
        }

        /// <summary>
        /// Розмір шрифту тексту. Показує значення теми, доки вчитель не зсунув повзунок;
        /// після цього діє його вибір.
        /// </summary>
        public double FontSize
        {
            get
            {
                return _fontSizeOverride > TextAppearance.UseThemeValue
                    ? _fontSizeOverride
                    : TextAppearance.GetEffective(TextAppearance.FontSizeKey);
            }
            set
            {
                var clamped = Clamp(value, MinFontSize, MaxFontSize);
                if (Math.Abs(clamped - FontSize) < 0.01 && _fontSizeOverride > TextAppearance.UseThemeValue)
                {
                    return;
                }

                _fontSizeOverride = clamped;
                TextAppearance.ApplyFontSize(clamped);
                SaveSetting(() => Settings.Default.FontSize = clamped);
                OnPropertyChanged();
                OnPropertyChanged("UsesThemeSizes");
            }
        }

        /// <summary>Міжрядковий інтервал тексту. Логіка та сама, що у <see cref="FontSize"/>.</summary>
        public double LineHeight
        {
            get
            {
                return _lineHeightOverride > TextAppearance.UseThemeValue
                    ? _lineHeightOverride
                    : TextAppearance.GetEffective(TextAppearance.LineHeightKey);
            }
            set
            {
                var clamped = Clamp(value, MinLineHeight, MaxLineHeight);
                if (Math.Abs(clamped - LineHeight) < 0.01 && _lineHeightOverride > TextAppearance.UseThemeValue)
                {
                    return;
                }

                _lineHeightOverride = clamped;
                TextAppearance.ApplyLineHeight(clamped);
                SaveSetting(() => Settings.Default.LineHeight = clamped);
                OnPropertyChanged();
                OnPropertyChanged("UsesThemeSizes");
            }
        }

        /// <summary>Чи діють зараз розміри з теми (тобто повзунки не чіпали).</summary>
        public bool UsesThemeSizes
        {
            get
            {
                return _fontSizeOverride <= TextAppearance.UseThemeValue
                       && _lineHeightOverride <= TextAppearance.UseThemeValue;
            }
        }

        /// <summary>Повернути розміри, які задає тема.</summary>
        public ICommand UseThemeSizesCommand { get; private set; }

        // ── Підрахунок ────────────────────────────────────────────────────

        /// <summary>Режим підрахунку абзаців (специфікація, 4.6).</summary>
        public ParagraphMode ParagraphMode
        {
            get { return _countingOptions.Paragraphs; }
            set
            {
                if (_countingOptions.Paragraphs == value)
                {
                    return;
                }

                _countingOptions.Paragraphs = value;
                SaveSetting(() => Settings.Default.ParagraphMode = (int)value);
                OnPropertyChanged();

                // Змінилося правило, а не текст, — дебаунсу тут чекати немає сенсу.
                RecalculateNow();
            }
        }

        // ── Замір ─────────────────────────────────────────────────────────

        /// <summary>Тривалість заміру в секундах.</summary>
        public int TimerSeconds
        {
            get { return _timerSeconds; }
            set
            {
                var clamped = value < MinTimerSeconds
                    ? MinTimerSeconds
                    : (value > MaxTimerSeconds ? MaxTimerSeconds : value);

                if (Set(ref _timerSeconds, clamped))
                {
                    SaveSetting(() => Settings.Default.TimerSeconds = clamped);
                }
            }
        }

        /// <summary>Швидкий вибір тривалості: 30 / 60 / 120 секунд.</summary>
        public ICommand SetTimerSecondsCommand { get; private set; }

        // ── Таймер і підсумок ─────────────────────────────────────────────

        /// <summary>Чи триває замір.</summary>
        public bool IsMeasuring
        {
            get { return _readingTimer.IsRunning; }
        }

        /// <summary>Час на екрані у форматі мм:сс.</summary>
        public string ElapsedDisplay
        {
            get { return Format(_readingTimer.Elapsed); }
        }

        /// <summary>Чи є завершений замір, який можна показати.</summary>
        public bool HasResult
        {
            get { return _hasResult; }
            private set { Set(ref _hasResult, value); }
        }

        /// <summary>Час завершеного заміру у форматі мм:сс.</summary>
        public string ResultElapsedDisplay
        {
            get { return Format(_resultElapsed); }
        }

        public int WordsPerMinute
        {
            get { return _wordsPerMinute; }
            private set { Set(ref _wordsPerMinute, value); }
        }

        public int CharsPerMinute
        {
            get { return _charsPerMinute; }
            private set { Set(ref _charsPerMinute, value); }
        }

        /// <summary>
        /// Скільки слів вважати прочитаними. Поки що весь текст;
        /// у Задачі 7 сюди прийде слово-межа з режиму A.
        /// </summary>
        public int WordsRead
        {
            get { return Stats.WordCount; }
        }

        /// <summary>
        /// Скільки знаків вважати прочитаними. Без пробілів — вони не
        /// вимовляються (специфікація, 4.7).
        /// </summary>
        public int CharsRead
        {
            get { return Stats.CharCountNoSpaces; }
        }

        /// <summary>Старт або стоп заміру. Гаряча клавіша — Пробіл.</summary>
        public ICommand ToggleMeasurementCommand { get; private set; }

        /// <summary>Скинути замір. Гаряча клавіша — Esc.</summary>
        public ICommand ResetMeasurementCommand { get; private set; }

        /// <summary>Спосіб заміру. Режими A і B взаємовиключні.</summary>
        public MeasurementMode MeasurementMode
        {
            get { return _measurementMode; }
            set
            {
                if (Set(ref _measurementMode, value))
                {
                    SaveSetting(() => Settings.Default.MeasurementMode = (int)value);
                    OnPropertyChanged("IsClickStopMode");
                    OnPropertyChanged("IsTimerMode");
                }
            }
        }

        /// <summary>
        /// Режим A для привʼязки радіокнопки. Окремі булеві властивості потрібні
        /// тому, що <c>RadioButton.IsChecked</c> не вміє порівнювати себе зі значенням
        /// перелічення без конвертера.
        /// </summary>
        public bool IsClickStopMode
        {
            get { return MeasurementMode == MeasurementMode.ClickStop; }
            set
            {
                if (value)
                {
                    MeasurementMode = MeasurementMode.ClickStop;
                }
            }
        }

        /// <summary>Режим B для привʼязки радіокнопки.</summary>
        public bool IsTimerMode
        {
            get { return MeasurementMode == MeasurementMode.Timer; }
            set
            {
                if (value)
                {
                    MeasurementMode = MeasurementMode.Timer;
                }
            }
        }

        /// <summary>
        /// Режим C: відмічати помилки. Незалежний від A і B — накладається на будь-який.
        /// </summary>
        public bool MarkErrors
        {
            get { return _markErrors; }
            set
            {
                if (Set(ref _markErrors, value))
                {
                    SaveSetting(() => Settings.Default.MarkErrors = value);
                }
            }
        }

        // ── Панель ────────────────────────────────────────────────────────

        /// <summary>Чи розгорнута ліва панель налаштувань.</summary>
        public bool IsSettingsExpanded
        {
            get { return _isSettingsExpanded; }
            set
            {
                if (Set(ref _isSettingsExpanded, value))
                {
                    SaveSetting(() => Settings.Default.SettingsExpanded = value);
                }
            }
        }

        public ICommand ToggleSettingsCommand { get; private set; }

        // ── Перерахунок ───────────────────────────────────────────────────

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

            OnPropertyChanged("WordsRead");
            OnPropertyChanged("CharsRead");
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

        // ── Замір ─────────────────────────────────────────────────────────

        private void ToggleMeasurement()
        {
            if (_readingTimer.IsRunning)
            {
                StopMeasurement();
                return;
            }

            // Дебаунс міг ще не спрацювати — інакше замір рахувався б
            // за кількістю слів попереднього тексту.
            RecalculateNow();

            _readingTimer.Duration = TimeSpan.FromSeconds(TimerSeconds);
            HasResult = false;
            _readingTimer.Start();

            OnPropertyChanged("IsMeasuring");
        }

        private void StopMeasurement()
        {
            _readingTimer.Stop();
            _resultElapsed = _readingTimer.Elapsed;

            var seconds = (decimal)_resultElapsed.TotalSeconds;
            WordsPerMinute = SpeedCalculator.WordsPerMinute(WordsRead, seconds);
            CharsPerMinute = SpeedCalculator.CharsPerMinute(CharsRead, seconds);

            HasResult = _resultElapsed > TimeSpan.Zero;

            OnPropertyChanged("IsMeasuring");
            OnPropertyChanged("ResultElapsedDisplay");
        }

        private void ResetMeasurement()
        {
            _readingTimer.Reset();
            HasResult = false;

            OnPropertyChanged("IsMeasuring");
            OnPropertyChanged("ElapsedDisplay");
        }

        private void OnReadingTimerTick(object sender, EventArgs e)
        {
            OnPropertyChanged("ElapsedDisplay");
        }

        private void OnDurationReached(object sender, EventArgs e)
        {
            // Сигнал, але відлік триває: у режимі B час має бути фактичним
            // (специфікація, 4.8).
            TimerSound.Play();
        }

        private static string Format(TimeSpan value)
        {
            var minutes = (int)value.TotalMinutes;
            return minutes.ToString("00", CultureInfo.InvariantCulture)
                   + ":" + value.Seconds.ToString("00", CultureInfo.InvariantCulture);
        }

        // ── Налаштування ──────────────────────────────────────────────────

        private void LoadSettings()
        {
            try
            {
                var settings = Settings.Default;

                _fontSizeOverride = settings.FontSize;
                _lineHeightOverride = settings.LineHeight;
                _timerSeconds = settings.TimerSeconds;
                _markErrors = settings.MarkErrors;
                _isSettingsExpanded = settings.SettingsExpanded;
                _measurementMode = Enum.IsDefined(typeof(MeasurementMode), settings.MeasurementMode)
                    ? (MeasurementMode)settings.MeasurementMode
                    : MeasurementMode.Timer;
                _countingOptions.Paragraphs = Enum.IsDefined(typeof(ParagraphMode), settings.ParagraphMode)
                    ? (ParagraphMode)settings.ParagraphMode
                    : ParagraphMode.NonEmptyLines;
            }
            catch (Exception ex)
            {
                // Пошкоджений user.config не має заважати працювати.
                Debug.WriteLine("ReadFlow: не вдалося прочитати налаштування — " + ex.Message);
                _timerSeconds = 60;
                _isSettingsExpanded = true;
                _measurementMode = MeasurementMode.Timer;
            }

            if (_timerSeconds < MinTimerSeconds || _timerSeconds > MaxTimerSeconds)
            {
                _timerSeconds = 60;
            }

            // Перекриття застосовуємо після читання: якщо їх немає, у ресурсах
            // нічого не змінюється й діють значення теми.
            TextAppearance.ApplyFontSize(_fontSizeOverride);
            TextAppearance.ApplyLineHeight(_lineHeightOverride);
        }

        private void OnUseThemeSizes()
        {
            _fontSizeOverride = TextAppearance.UseThemeValue;
            _lineHeightOverride = TextAppearance.UseThemeValue;

            TextAppearance.ApplyFontSize(TextAppearance.UseThemeValue);
            TextAppearance.ApplyLineHeight(TextAppearance.UseThemeValue);

            SaveSetting(() =>
            {
                Settings.Default.FontSize = TextAppearance.UseThemeValue;
                Settings.Default.LineHeight = TextAppearance.UseThemeValue;
            });

            OnPropertyChanged("FontSize");
            OnPropertyChanged("LineHeight");
            OnPropertyChanged("UsesThemeSizes");
        }

        private void OnSetTimerSeconds(string seconds)
        {
            int parsed;
            if (int.TryParse(seconds, NumberStyles.Integer, CultureInfo.InvariantCulture, out parsed))
            {
                TimerSeconds = parsed;
            }
        }

        private static void SaveSetting(Action assign)
        {
            try
            {
                assign();
                Settings.Default.Save();
            }
            catch (Exception ex)
            {
                // Немає прав на запис user.config — налаштування просто не запамʼятається.
                Debug.WriteLine("ReadFlow: не вдалося зберегти налаштування — " + ex.Message);
            }
        }

        private static double Clamp(double value, double min, double max)
        {
            if (value < min)
            {
                return min;
            }

            return value > max ? max : value;
        }
    }
}

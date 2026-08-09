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
    /// ViewModel головного вікна: текст, статистика, налаштування й замір.
    /// Відмітка помилок (режим C) зʼявиться в Задачі 8.
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

        /// <summary>
        /// Семестр за замовчуванням — другий. Норми першого семестру нижчі,
        /// і показати їх у травні означало б завищити оцінку всьому класу;
        /// зворотна помилка помітніша й швидше виправляється вчителем.
        /// </summary>
        public const int DefaultSemester = 2;

        private readonly DispatcherTimer _recalculateTimer;
        private readonly CountingOptions _countingOptions = new CountingOptions();
        private readonly ReadingTimer _readingTimer = new ReadingTimer();

        // Довідник норм. І числа, і підписи оцінок у ньому — із shared/norms.json;
        // у коді немає ані того, ані іншого (непорушне правило 2).
        private readonly NormsCatalog _norms = NormsLoader.Current;

        // Помилки зберігаються за номерами слів, а не за посиланнями на них:
        // при перерахунку статистики слова створюються заново, і позначки
        // з'їхали б на інші об'єкти.
        private readonly HashSet<int> _errorWords = new HashSet<int>();

        private bool _hasResult;
        private int _wordsPerMinute;
        private int _charsPerMinute;
        private int _cleanWordsPerMinute;
        private int _boundaryWordNumber;
        private int _errorCount;
        private double _errorPercent;
        private IReadOnlyCollection<int> _errorWordsSnapshot = new int[0];

        // Час, за яким порахований підсумок. У режимі B це показ секундоміра,
        // у режимі A — задана тривалість; заповнюється в CaptureResultTime.
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

        // null — клас не обраний. Норма без класу невідома, і краще не показати
        // оцінки взагалі, ніж підсунути вчителеві норму навмання.
        //
        // Саме обʼєкт класу, а не його номер: ComboBox привʼязується до
        // SelectedItem, і «не обрано» стає звичайним null замість числа 0,
        // яке довелося б окремо конвертувати в порожній вибір і назад.
        private GradeNorms _selectedGrade;
        private int _semester;

        private MeasurementMode _measurementMode;
        private bool _markErrors;
        private bool _isSettingsExpanded;
        private bool _showLineHighlight;
        private bool _useReadingFont;

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
            SelectWordCommand = new RelayCommand<object>(OnSelectWord);
            SetBoundaryCommand = new RelayCommand<object>(OnSetBoundary);

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
                // старі WPM більше ні про що не свідчать, а межа й позначки
                // помилок вказують на слова, яких у новому тексті може не бути.
                BoundaryWordNumber = 0;
                ClearErrors();
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

        /// <summary>
        /// Гарнітура, зручніша для читання (Задача 9).
        ///
        /// Окремо від повзунків розміру: розмір і гарнітура — різні речі, і вчитель
        /// може захотіти велику звичайну або дрібну широку. Тому перемикач не чіпає
        /// ані розміру, ані інтервалу.
        /// </summary>
        public bool UseReadingFont
        {
            get { return _useReadingFont; }
            set
            {
                if (!Set(ref _useReadingFont, value))
                {
                    return;
                }

                TextAppearance.ApplyReadingFont(value);
                SaveSetting(() => Settings.Default.ReadingFont = value);
            }
        }

        /// <summary>
        /// Підсвічувати рядок під курсором у режимі читання (Задача 9).
        /// </summary>
        public bool ShowLineHighlight
        {
            get { return _showLineHighlight; }
            set
            {
                if (Set(ref _showLineHighlight, value))
                {
                    SaveSetting(() => Settings.Default.LineHighlight = value);
                }
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

                if (!Set(ref _timerSeconds, clamped))
                {
                    return;
                }

                SaveSetting(() => Settings.Default.TimerSeconds = clamped);

                // У режимі A саме ця тривалість — знаменник у формулі швидкості.
                // Змінили її з готовим підсумком — підсумок мусить наздогнати,
                // інакше на екрані лишиться WPM, порахований за старим часом.
                if (HasResult && MeasurementMode == MeasurementMode.ClickStop)
                {
                    CaptureResultTime();
                    UpdateResult();
                }
            }
        }

        /// <summary>Швидкий вибір тривалості: 30 / 60 / 120 секунд.</summary>
        public ICommand SetTimerSecondsCommand { get; private set; }

        // ── Норми за класами (специфікація, 4.9) ──────────────────────────

        /// <summary>
        /// Класи з довідника норм. Список і підписи — з <c>shared/norms.json</c>:
        /// у розмітці класів немає, інакше правка норм вимагала б правки XAML.
        /// </summary>
        public IList<GradeNorms> Grades
        {
            get { return _norms.Grades; }
        }

        /// <summary>
        /// Чи є взагалі довідник норм. Коли ні — контроли класу й семестру
        /// не показуються: сірий випадний список ні про що не говорить учителю
        /// і суперечить мінімалізму (специфікація, 3).
        /// </summary>
        public bool HasNormsCatalog
        {
            get { return !_norms.IsEmpty; }
        }

        /// <summary>
        /// Обраний клас із довідника або <c>null</c>, якщо вчитель його ще не обрав.
        /// Це те, до чого привʼязаний випадний список.
        /// </summary>
        public GradeNorms SelectedGrade
        {
            get { return _selectedGrade; }
            set
            {
                if (!Set(ref _selectedGrade, value))
                {
                    return;
                }

                var number = Grade;
                SaveSetting(() => Settings.Default.Grade = number);

                OnPropertyChanged("Grade");
                OnPropertyChanged("IsGradeSelected");
                PublishNorm();
            }
        }

        /// <summary>
        /// Номер обраного класу або 0. Зручніше за <see cref="SelectedGrade"/>
        /// і в налаштуваннях, і в тестах; клас, якого немає в довіднику,
        /// означає те саме, що «не обрано».
        /// </summary>
        public int Grade
        {
            get { return _selectedGrade == null ? 0 : _selectedGrade.Grade; }
            set { SelectedGrade = FindGrade(value); }
        }

        /// <summary>Чи обрано клас.</summary>
        public bool IsGradeSelected
        {
            get { return _selectedGrade != null; }
        }

        /// <summary>
        /// Обраний семестр. Норми за перше й друге півріччя різні, і показувати
        /// у вересні норму кінця року означало б записати весь клас у відстаючі.
        /// </summary>
        public int Semester
        {
            get { return _semester; }
            set
            {
                if (!Set(ref _semester, value))
                {
                    return;
                }

                SaveSetting(() => Settings.Default.Semester = value);
                OnPropertyChanged("IsFirstSemester");
                OnPropertyChanged("IsSecondSemester");
                PublishNorm();
            }
        }

        /// <summary>Перший семестр — для привʼязки радіокнопки.</summary>
        public bool IsFirstSemester
        {
            get { return _semester == 1; }
            set
            {
                if (value)
                {
                    Semester = 1;
                }
            }
        }

        /// <summary>Другий семестр — для привʼязки радіокнопки.</summary>
        public bool IsSecondSemester
        {
            get { return _semester == 2; }
            set
            {
                if (value)
                {
                    Semester = 2;
                }
            }
        }

        /// <summary>Норма для обраного класу й семестру або <c>null</c>.</summary>
        public ReadingNorm CurrentNorm
        {
            get { return _norms.Find(Grade, _semester); }
        }

        private GradeNorms FindGrade(int number)
        {
            foreach (var grade in _norms.Grades)
            {
                if (grade.Grade == number)
                {
                    return grade;
                }
            }

            return null;
        }

        /// <summary>Межі норми у вигляді «50–60». Лише цифри й тире — перекладати нічого.</summary>
        public string NormRangeText
        {
            get
            {
                var norm = CurrentNorm;

                return norm == null
                    ? string.Empty
                    : norm.Min.ToString(CultureInfo.CurrentCulture) + "–" +
                      norm.Max.ToString(CultureInfo.CurrentCulture);
            }
        }

        /// <summary>
        /// Оцінка результату відносно норми. Рахується від <see cref="WordsPerMinute"/>,
        /// а не від «чистої» швидкості: норми МОН — про темп читання, а помилки
        /// вчитель бачить окремим показником.
        /// </summary>
        public NormEvaluation NormStatus
        {
            get
            {
                return HasResult
                    ? _norms.Evaluate(WordsPerMinute, CurrentNorm)
                    : Core.NormEvaluation.Unknown;
            }
        }

        /// <summary>Підпис оцінки з довідника: «нижче норми» / «у межах норми» / «вище норми».</summary>
        public string NormStatusText
        {
            get { return _norms.Describe(NormStatus); }
        }

        /// <summary>Чи є що показати в блоці оцінки.</summary>
        public bool HasNormStatus
        {
            get { return NormStatus != Core.NormEvaluation.Unknown; }
        }

        /// <summary>
        /// Оцінка залежить і від норми, і від підсумку заміру, тому обчислюється
        /// на льоту, а не зберігається полем: два джерела правди про одне число
        /// рано чи пізно розійшлися б.
        /// </summary>
        private void PublishNorm()
        {
            OnPropertyChanged("CurrentNorm");
            OnPropertyChanged("NormRangeText");
            OnPropertyChanged("NormStatus");
            OnPropertyChanged("NormStatusText");
            OnPropertyChanged("HasNormStatus");
        }

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
            private set
            {
                if (Set(ref _hasResult, value))
                {
                    // Без підсумку немає й оцінки за нормою.
                    PublishNorm();
                }
            }
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
        /// Номер слова, на якому учень зупинився, або 0, якщо межі немає.
        /// Задається кліком по слову в режимі читання.
        /// </summary>
        public int BoundaryWordNumber
        {
            get { return _boundaryWordNumber; }
            private set
            {
                if (!Set(ref _boundaryWordNumber, value))
                {
                    return;
                }

                OnPropertyChanged("HasBoundary");
                OnPropertyChanged("WordsRead");
                OnPropertyChanged("CharsRead");
            }
        }

        /// <summary>Чи позначено слово-межу.</summary>
        public bool HasBoundary
        {
            get { return _boundaryWordNumber > 0; }
        }

        /// <summary>
        /// Лівий клік по слову. Параметр — номер слова.
        ///
        /// Що саме він робить, вирішує ViewModel, а не View: коли відмітка
        /// помилок увімкнена — позначає помилку, інакше ставить межу читання.
        /// </summary>
        public ICommand SelectWordCommand { get; private set; }

        /// <summary>
        /// Правий клік по слову — завжди межа читання.
        ///
        /// Потрібен саме тому, що в режимі C лівий клік зайнятий помилками:
        /// вчитель тримає мишу й слухає учня, і друга рука на Ctrl була б зайвою.
        /// </summary>
        public ICommand SetBoundaryCommand { get; private set; }

        /// <summary>
        /// Номери слів, позначених як помилки. Незмінний знімок: щоразу новий
        /// набір, бо привʼязка не помітила б зміни всередині того самого обʼєкта.
        /// </summary>
        public IReadOnlyCollection<int> ErrorWords
        {
            get { return _errorWordsSnapshot; }
            private set { Set(ref _errorWordsSnapshot, value); }
        }

        /// <summary>
        /// Кількість помилок у межах прочитаного. Позначки за межею лишаються
        /// на своїх словах, але в показники не входять: учень туди не дочитав
        /// (Задача 8, «помилки в межах межі читання»).
        /// </summary>
        public int ErrorCount
        {
            get { return _errorCount; }
            private set { Set(ref _errorCount, value); }
        }

        /// <summary>Відсоток помилок від прочитаних слів, з точністю 0.1.</summary>
        public double ErrorPercent
        {
            get { return _errorPercent; }
            private set { Set(ref _errorPercent, value); }
        }

        /// <summary>Чи є що показувати в блоці помилок.</summary>
        public bool HasErrors
        {
            get { return _errorCount > 0; }
        }

        /// <summary>«Чиста» швидкість: без слів, прочитаних із помилкою.</summary>
        public int CleanWordsPerMinute
        {
            get { return _cleanWordsPerMinute; }
            private set { Set(ref _cleanWordsPerMinute, value); }
        }

        /// <summary>
        /// Скільки слів вважати прочитаними: до слова-межі включно, а якщо
        /// межі немає — весь текст (специфікація, 4.7).
        ///
        /// Слова нумеруються поспіль від 1, тож номер межі — це і є кількість
        /// слів до неї включно.
        /// </summary>
        public int WordsRead
        {
            get
            {
                return _boundaryWordNumber > 0 && _boundaryWordNumber <= Stats.WordCount
                    ? _boundaryWordNumber
                    : Stats.WordCount;
            }
        }

        /// <summary>
        /// Скільки знаків вважати прочитаними. Без пробілів — вони не
        /// вимовляються (специфікація, 4.7).
        /// </summary>
        public int CharsRead
        {
            get
            {
                var boundary = Document.WordByNumber(_boundaryWordNumber);

                return boundary == null
                    ? Stats.CharCountNoSpaces
                    : TextStatsCalculator.CountCharsNoSpaces(Document.Text, boundary.End);
            }
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
                if (!Set(ref _measurementMode, value))
                {
                    return;
                }

                SaveSetting(() => Settings.Default.MeasurementMode = (int)value);
                OnPropertyChanged("IsClickStopMode");
                OnPropertyChanged("IsTimerMode");

                // Режими беруть час із різних джерел (4.8), тож підсумок,
                // порахований за правилами попереднього режиму, більше не чинний.
                // Межу лишаємо: вона про те, де учень зупинився, а не про час.
                HasResult = false;
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
                if (!Set(ref _markErrors, value))
                {
                    return;
                }

                SaveSetting(() => Settings.Default.MarkErrors = value);

                // Вимкнули відмітку — позначки зникають, а не ховаються.
                // Прихований стан, який мовчки повертається при повторному
                // вмиканні, — саме те, через що потім не сходяться числа.
                ClearErrors();
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

            // Підстраховка: зміна тексту межу скидає, але якщо колись перерахунок
            // почнуть викликати ще звідкись, межа не має пережити текст, у якому
            // такого слова вже немає.
            if (_boundaryWordNumber > words.Count)
            {
                BoundaryWordNumber = 0;
            }

            _errorWords.RemoveWhere(number => number > words.Count);

            OnPropertyChanged("WordsRead");
            OnPropertyChanged("CharsRead");

            // Кількість слів могла змінитися — а від неї залежить і скільки
            // помилок «у грі», і їхній відсоток.
            PublishErrors();
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

            // Новий замір — з чистого аркуша: межа й помилки від попереднього
            // учня мовчки зрізали б половину тексту й зіпсували «чисту» швидкість.
            BoundaryWordNumber = 0;
            ClearErrors();
            HasResult = false;
            _readingTimer.Start();

            OnPropertyChanged("IsMeasuring");
        }

        private void StopMeasurement()
        {
            _readingTimer.Stop();
            CaptureResultTime();

            // У режимі A підсумок має сенс лише з позначеною межею: без неї
            // невідомо, скільки учень прочитав, а час і так заданий наперед.
            HasResult = MeasurementMode == MeasurementMode.ClickStop
                ? HasBoundary
                : _readingTimer.Elapsed > TimeSpan.Zero;

            if (HasResult)
            {
                UpdateResult();
            }

            OnPropertyChanged("IsMeasuring");
        }

        private void ResetMeasurement()
        {
            _readingTimer.Reset();
            BoundaryWordNumber = 0;
            ClearErrors();
            HasResult = false;

            OnPropertyChanged("IsMeasuring");
            OnPropertyChanged("ElapsedDisplay");
        }

        /// <summary>
        /// Лівий клік: помилка в режимі C, інакше межа читання.
        /// </summary>
        private void OnSelectWord(object parameter)
        {
            int number;
            if (!TryResolveWord(parameter, out number))
            {
                return;
            }

            if (MarkErrors)
            {
                ToggleError(number);
                return;
            }

            SetBoundary(number);
        }

        /// <summary>Правий клік: межа читання незалежно від режиму.</summary>
        private void OnSetBoundary(object parameter)
        {
            int number;
            if (TryResolveWord(parameter, out number))
            {
                SetBoundary(number);
            }
        }

        /// <summary>
        /// Позначити слово помилкою або зняти позначку.
        /// </summary>
        private void ToggleError(int number)
        {
            if (!_errorWords.Remove(number))
            {
                _errorWords.Add(number);
            }

            PublishErrors();
        }

        private void ClearErrors()
        {
            if (_errorWords.Count == 0)
            {
                return;
            }

            _errorWords.Clear();
            PublishErrors();
        }

        /// <summary>
        /// Перерахувати показники помилок і віддати View новий набір позначок.
        /// </summary>
        private void PublishErrors()
        {
            var read = WordsRead;

            // Рахуються лише помилки в межах прочитаного. За межею позначка
            // лишається — перенесли межу далі, і вона знову в грі.
            var counted = 0;
            foreach (var number in _errorWords)
            {
                if (number <= read)
                {
                    counted++;
                }
            }

            ErrorCount = counted;

            // Той самий підхід, що й у решті формул (специфікація, 4.4 і 4.7):
            // ділимо два цілих у decimal і округлюємо «від нуля». У double
            // серединні значення на кшталт 5/40 = 12.5% залежали б від платформи.
            ErrorPercent = read == 0 || counted == 0
                ? 0d
                : (double)Math.Round(counted * 100m / read, 1, MidpointRounding.AwayFromZero);

            var snapshot = new int[_errorWords.Count];
            _errorWords.CopyTo(snapshot);
            ErrorWords = snapshot;

            OnPropertyChanged("HasErrors");

            if (HasResult)
            {
                UpdateResult();
            }
        }

        /// <summary>
        /// Номер слова з параметра команди — і перевірка, що таке слово в тексті є.
        /// Клік по слову з номером від попереднього тексту краще проігнорувати,
        /// ніж показати позначку поза текстом.
        /// </summary>
        private bool TryResolveWord(object parameter, out int number)
        {
            return TryGetWordNumber(parameter, out number)
                   && Document.WordByNumber(number) != null;
        }

        /// <summary>
        /// Поставити, перенести або зняти межу читання — режим A «клік = стоп».
        /// </summary>
        private void SetBoundary(int number)
        {
            // Повторний клік по слову-межі знімає її: вчитель може виправити
            // випадковий клік, не шукаючи окремої кнопки.
            BoundaryWordNumber = number == _boundaryWordNumber ? 0 : number;

            // Скільки помилок «у грі», залежить від межі — переставили її,
            // і показники мусять наздогнати.
            PublishErrors();

            if (MeasurementMode == MeasurementMode.ClickStop)
            {
                // Клік = стоп. Відлік зупиняється, але в формулу йде задана
                // тривалість, а не показ секундоміра (специфікація, 4.8):
                // вчитель клікає в момент, коли час минув.
                _readingTimer.Stop();
                CaptureResultTime();

                HasResult = HasBoundary;

                OnPropertyChanged("IsMeasuring");
            }

            // У режимі B клік лише переносить межу — час рахує секундомір,
            // і зупиняє його Стоп. Якщо замір уже завершено, підсумок
            // перераховується під нову кількість прочитаних слів (2.2).
            if (HasResult)
            {
                UpdateResult();
            }
        }

        /// <summary>
        /// Запамʼятати час, за яким рахується підсумок.
        ///
        /// Режим A бере задану тривалість, режим B — показ секундоміра. Обидва
        /// шляхи сходяться тут: інакше поруч могли б опинитися час читання з
        /// секундоміра й WPM, порахований за іншим числом.
        /// </summary>
        private void CaptureResultTime()
        {
            _resultElapsed = MeasurementMode == MeasurementMode.ClickStop
                ? TimeSpan.FromSeconds(TimerSeconds)
                : _readingTimer.Elapsed;

            OnPropertyChanged("ResultElapsedDisplay");
        }

        private void UpdateResult()
        {
            var seconds = (decimal)_resultElapsed.TotalSeconds;

            var read = WordsRead;

            WordsPerMinute = SpeedCalculator.WordsPerMinute(read, seconds);
            CharsPerMinute = SpeedCalculator.CharsPerMinute(CharsRead, seconds);

            // «Чиста» швидкість — та сама формула, але слова з помилками
            // не рахуються прочитаними (специфікація, 4.7).
            CleanWordsPerMinute = SpeedCalculator.WordsPerMinute(read - ErrorCount, seconds);

            // WPM змінився — оцінка за нормою мусить наздогнати.
            PublishNorm();
        }

        /// <summary>
        /// Номер слова з параметра команди. Привʼязка може принести і число,
        /// і рядок, тому обидва варіанти читаються тут, а не в розмітці.
        /// </summary>
        private static bool TryGetWordNumber(object parameter, out int number)
        {
            if (parameter is int)
            {
                number = (int)parameter;
                return number > 0;
            }

            var text = parameter as string;
            if (text != null &&
                int.TryParse(text, NumberStyles.Integer, CultureInfo.InvariantCulture, out number))
            {
                return number > 0;
            }

            number = 0;
            return false;
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

                // Клас із налаштувань міг зникнути з довідника: учитель поправив
                // norms.json і прибрав рядок. Тоді це просто «не обрано».
                _selectedGrade = FindGrade(settings.Grade);
                _semester = settings.Semester;
                _markErrors = settings.MarkErrors;
                _isSettingsExpanded = settings.SettingsExpanded;
                _showLineHighlight = settings.LineHighlight;
                _useReadingFont = settings.ReadingFont;
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
                _semester = DefaultSemester;
            }

            if (_timerSeconds < MinTimerSeconds || _timerSeconds > MaxTimerSeconds)
            {
                _timerSeconds = 60;
            }

            if (_semester != 1 && _semester != 2)
            {
                _semester = DefaultSemester;
            }

            // Перекриття застосовуємо після читання: якщо їх немає, у ресурсах
            // нічого не змінюється й діють значення теми.
            TextAppearance.ApplyFontSize(_fontSizeOverride);
            TextAppearance.ApplyLineHeight(_lineHeightOverride);
            TextAppearance.ApplyReadingFont(_useReadingFont);
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

using System.Collections.Generic;
using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;
using ReadFlow.Core;
using ReadFlow.Models;

namespace ReadFlow.Views
{
    /// <summary>
    /// Режим читання: текст рендериться послівно, кожне слово — окремий <see cref="Run"/>
    /// з підказкою «Слово №N» і кліком, що позначає межу читання (Задача 7).
    ///
    /// Підказка задається рядком прямо на <c>Run</c>, а не окремим обробником миші.
    /// Це виглядає марнотратно, але насправді дешево: <c>ToolTipService</c> створює
    /// вікно підказки лише в момент показу, тож на 3000 слів ми додаємо 3000 коротких
    /// рядків (десятки кілобайт) і жодного зайвого візуального елемента.
    ///
    /// А от обробники миші висять на одному <see cref="TextBlock"/>, а не на кожному
    /// слові: подія однаково приходить від <c>Run</c>, у який влучив курсор
    /// (<c>e.OriginalSource</c>), тож 3000 підписок нічого б не додали, крім памʼяті.
    /// </summary>
    public class WordReaderView : UserControl
    {
        private const string WordNumberFormatKey = "Str_WordNumberFormat";
        private const string DefaultWordNumberFormat = "Слово №{0}";

        private const string BoundaryBrushKey = "WordBoundaryBrush";
        private const string HoverBrushKey = "WordHoverBrush";
        private const string ErrorBrushKey = "WordErrorBrush";
        private const string LineHighlightBrushKey = "LineHighlightBrush";

        /// <summary>Відступ тексту від краю. Від нього рахується позиція першого рядка.</summary>
        private const double TextPadding = 12d;

        public static readonly DependencyProperty DocumentProperty = DependencyProperty.Register(
            "Document",
            typeof(ReadingDocument),
            typeof(WordReaderView),
            new PropertyMetadata(null, OnDocumentChanged));

        public static readonly DependencyProperty BoundaryWordNumberProperty = DependencyProperty.Register(
            "BoundaryWordNumber",
            typeof(int),
            typeof(WordReaderView),
            new PropertyMetadata(0, OnBoundaryWordNumberChanged));

        public static readonly DependencyProperty ErrorWordsProperty = DependencyProperty.Register(
            "ErrorWords",
            typeof(IReadOnlyCollection<int>),
            typeof(WordReaderView),
            new PropertyMetadata(null, OnErrorWordsChanged));

        public static readonly DependencyProperty ShowLineHighlightProperty = DependencyProperty.Register(
            "ShowLineHighlight",
            typeof(bool),
            typeof(WordReaderView),
            new PropertyMetadata(false, OnShowLineHighlightChanged));

        public static readonly DependencyProperty WordCommandProperty = DependencyProperty.Register(
            "WordCommand",
            typeof(ICommand),
            typeof(WordReaderView),
            new PropertyMetadata(null));

        public static readonly DependencyProperty WordAlternateCommandProperty = DependencyProperty.Register(
            "WordAlternateCommand",
            typeof(ICommand),
            typeof(WordReaderView),
            new PropertyMetadata(null));

        private readonly TextBlock _textBlock;
        private readonly Rectangle _lineHighlight;

        // Runʼи слів за номером (номер − 1). Потрібні, щоб змінити тло одного слова,
        // не перебираючи тисячі інлайнів: підсвітка межі має бути миттєвою.
        private Run[] _wordRuns = new Run[0];

        // Копія набору помилок: коли він змінюється, перемальовуються лише ті
        // слова, що з'явилися або зникли, а не всі три тисячі.
        private HashSet<int> _errorWordNumbers = new HashSet<int>();

        private int _hoveredWordNumber;
        private bool _needsRebuild = true;

        public WordReaderView()
        {
            _textBlock = new TextBlock
            {
                TextWrapping = TextWrapping.Wrap,
                LineStackingStrategy = LineStackingStrategy.BlockLineHeight,
                Padding = new Thickness(12),

                // Не колір, а умова влучення: без заданого тла миша «провалюється»
                // крізь порожні місця між словами, і підсвітка не гасне, коли
                // курсор зʼїхав з тексту. Прозоре тло нічого не малює.
                Background = Brushes.Transparent
            };

            // SetResourceReference — це DynamicResource у коді: при зміні теми
            // значення оновиться саме, без перебудови контрола.
            _textBlock.SetResourceReference(TextBlock.ForegroundProperty, "TextForegroundBrush");
            _textBlock.SetResourceReference(TextBlock.FontSizeProperty, "BaseFontSize");
            _textBlock.SetResourceReference(TextBlock.LineHeightProperty, "TextLineHeight");
            _textBlock.SetResourceReference(TextBlock.FontFamilyProperty, TextAppearance.FontFamilyKey);

            _textBlock.MouseLeftButtonDown += OnTextMouseLeftButtonDown;
            _textBlock.MouseRightButtonDown += OnTextMouseRightButtonDown;
            _textBlock.MouseMove += OnTextMouseMove;
            _textBlock.MouseLeave += OnTextMouseLeave;

            // Смуга підсвітки рядка лежить ПІД текстом в одній клітинці Grid.
            // Окремим шаром, а не тлом Runʼів: рядок — поняття розкладки, а не
            // розмітки, і слова в ньому належать різним Runʼам.
            _lineHighlight = new Rectangle
            {
                HorizontalAlignment = HorizontalAlignment.Stretch,
                VerticalAlignment = VerticalAlignment.Top,
                Visibility = Visibility.Collapsed,
                IsHitTestVisible = false
            };
            _lineHighlight.SetResourceReference(Shape.FillProperty, LineHighlightBrushKey);

            var layers = new Grid();
            layers.Children.Add(_lineHighlight);
            layers.Children.Add(_textBlock);

            Content = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Content = layers
            };

            IsVisibleChanged += OnIsVisibleChanged;
        }

        /// <summary>Текст із розібраними словами. Рендер оновлюється при зміні.</summary>
        public ReadingDocument Document
        {
            get { return (ReadingDocument)GetValue(DocumentProperty); }
            set { SetValue(DocumentProperty, value); }
        }

        /// <summary>Номер слова-межі читання, або 0. Підсвічується тлом теми.</summary>
        public int BoundaryWordNumber
        {
            get { return (int)GetValue(BoundaryWordNumberProperty); }
            set { SetValue(BoundaryWordNumberProperty, value); }
        }

        /// <summary>Номери слів, позначених як помилки.</summary>
        public IReadOnlyCollection<int> ErrorWords
        {
            get { return (IReadOnlyCollection<int>)GetValue(ErrorWordsProperty); }
            set { SetValue(ErrorWordsProperty, value); }
        }

        /// <summary>Підсвічувати рядок, над яким курсор.</summary>
        public bool ShowLineHighlight
        {
            get { return (bool)GetValue(ShowLineHighlightProperty); }
            set { SetValue(ShowLineHighlightProperty, value); }
        }

        /// <summary>Команда лівого кліку по слову. Параметр — номер слова.</summary>
        public ICommand WordCommand
        {
            get { return (ICommand)GetValue(WordCommandProperty); }
            set { SetValue(WordCommandProperty, value); }
        }

        /// <summary>Команда правого кліку по слову. Параметр — номер слова.</summary>
        public ICommand WordAlternateCommand
        {
            get { return (ICommand)GetValue(WordAlternateCommandProperty); }
            set { SetValue(WordAlternateCommandProperty, value); }
        }

        private static void OnDocumentChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            ((WordReaderView)d).InvalidateContent();
        }

        private static void OnBoundaryWordNumberChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            var view = (WordReaderView)d;

            // Перемальовуємо рівно два слова: те, що перестало бути межею, і нове.
            view.ApplyWordBrush((int)e.OldValue);
            view.ApplyWordBrush((int)e.NewValue);
        }

        private static void OnShowLineHighlightChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            if (!(bool)e.NewValue)
            {
                ((WordReaderView)d)._lineHighlight.Visibility = Visibility.Collapsed;
            }
        }

        private static void OnErrorWordsChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            var view = (WordReaderView)d;

            var updated = new HashSet<int>((IEnumerable<int>)e.NewValue ?? new int[0]);
            var previous = view._errorWordNumbers;
            view._errorWordNumbers = updated;

            // Симетрична різниця: зазвичай це одне слово, яке щойно клікнули.
            // Перебирати весь текст на кожну позначку було б помітно на 3000 слів.
            foreach (var number in previous)
            {
                if (!updated.Contains(number))
                {
                    view.ApplyWordBrush(number);
                }
            }

            foreach (var number in updated)
            {
                if (!previous.Contains(number))
                {
                    view.ApplyWordBrush(number);
                }
            }
        }

        /// <summary>
        /// Позначити рендер застарілим. Перебудова відкладається, доки контрол
        /// не стане видимим: у режимі редагування текст змінюється на кожну паузу
        /// в наборі, і будувати тисячі Runʼів для схованого контрола — марна робота.
        /// </summary>
        private void InvalidateContent()
        {
            _needsRebuild = true;

            if (IsVisible)
            {
                Rebuild();
            }
        }

        private void OnIsVisibleChanged(object sender, DependencyPropertyChangedEventArgs e)
        {
            if (IsVisible && _needsRebuild)
            {
                Rebuild();
            }
        }

        private void Rebuild()
        {
            _needsRebuild = false;
            _textBlock.Inlines.Clear();
            _wordRuns = new Run[0];
            _hoveredWordNumber = 0;
            _textBlock.Cursor = null;

            // Смуга вказувала на рядок попереднього тексту — після перебудови
            // під нею вже інші слова.
            _lineHighlight.Visibility = Visibility.Collapsed;

            var document = Document;
            if (document == null)
            {
                return;
            }

            var segments = document.GetSegments();
            if (segments.Count == 0)
            {
                return;
            }

            var format = TryFindResource(WordNumberFormatKey) as string ?? DefaultWordNumberFormat;
            var inlines = new List<Inline>(segments.Count);
            var runs = new Run[document.Words.Count];

            foreach (var segment in segments)
            {
                var run = new Run(segment.Text);

                if (segment.IsWord)
                {
                    run.ToolTip = string.Format(CultureInfo.CurrentCulture, format, segment.WordNumber);

                    // Номер живе на самому Runʼі: обробник миші дістає його
                    // з e.OriginalSource без жодного пошуку.
                    run.Tag = segment.WordNumber;

                    if (segment.WordNumber <= runs.Length)
                    {
                        runs[segment.WordNumber - 1] = run;
                    }
                }

                inlines.Add(run);
            }

            _wordRuns = runs;

            // AddRange, а не Add у циклі: інакше кожне додавання окремо інвалідує
            // розмітку, і на великому тексті перемикання в режим читання підвисає.
            _textBlock.Inlines.AddRange(inlines);

            // Межа й помилки могли бути задані до того, як контрол став видимим.
            ApplyWordBrush(BoundaryWordNumber);

            foreach (var number in _errorWordNumbers)
            {
                ApplyWordBrush(number);
            }
        }

        // ── Миша ──────────────────────────────────────────────────────────

        private void OnTextMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            Invoke(WordCommand, e);
        }

        private void OnTextMouseRightButtonDown(object sender, MouseButtonEventArgs e)
        {
            Invoke(WordAlternateCommand, e);
        }

        private static void Invoke(ICommand command, MouseButtonEventArgs e)
        {
            var number = WordNumberAt(e.OriginalSource);
            if (number == 0 || command == null || !command.CanExecute(number))
            {
                return;
            }

            command.Execute(number);
            e.Handled = true;
        }

        private void OnTextMouseMove(object sender, MouseEventArgs e)
        {
            SetHoveredWord(WordNumberAt(e.OriginalSource));
            MoveLineHighlight(e.GetPosition(_textBlock).Y);
        }

        private void OnTextMouseLeave(object sender, MouseEventArgs e)
        {
            SetHoveredWord(0);
            _lineHighlight.Visibility = Visibility.Collapsed;
        }

        /// <summary>
        /// Посунути смугу під рядок, над яким курсор.
        ///
        /// Рядок обчислюється арифметикою, а не пошуком по розкладці, і це надійно
        /// саме тут: <c>TextBlock</c> налаштований на <c>BlockLineHeight</c> із явним
        /// <c>LineHeight</c>, тобто всі рядки рівно однакової висоти. Без цього
        /// довелося б ходити по <c>TextPointer</c> на кожен рух миші.
        /// </summary>
        private void MoveLineHighlight(double y)
        {
            if (!ShowLineHighlight)
            {
                return;
            }

            var lineHeight = _textBlock.LineHeight;
            var offset = y - TextPadding;

            if (double.IsNaN(lineHeight) || lineHeight <= 0 || offset < 0)
            {
                _lineHighlight.Visibility = Visibility.Collapsed;
                return;
            }

            var line = (int)(offset / lineHeight);

            _lineHighlight.Height = lineHeight;
            _lineHighlight.Margin = new Thickness(0, TextPadding + line * lineHeight, 0, 0);
            _lineHighlight.Visibility = Visibility.Visible;
        }

        private void SetHoveredWord(int number)
        {
            if (number == _hoveredWordNumber)
            {
                return;
            }

            var previous = _hoveredWordNumber;
            _hoveredWordNumber = number;

            ApplyWordBrush(previous);
            ApplyWordBrush(number);

            // Курсор — єдина підказка, що слово можна клікнути: інакше вчитель
            // не здогадається, що тут узагалі щось відбувається.
            _textBlock.Cursor = number > 0 ? Cursors.Hand : null;
        }

        /// <summary>
        /// Привести тло слова до його стану.
        ///
        /// Порядок важливий: помилка > межа > наведення. Помилка попереду тому,
        /// що клік має давати видимий відгук: якби межа перекривала її, вчитель
        /// клікнув би по слові-межі, нічого б не змінилося — і він вирішив би,
        /// що застосунок його не почув. Скільки слів прочитано, видно з показника.
        /// </summary>
        private void ApplyWordBrush(int number)
        {
            var run = RunOf(number);
            if (run == null)
            {
                return;
            }

            if (_errorWordNumbers.Contains(number))
            {
                run.SetResourceReference(TextElement.BackgroundProperty, ErrorBrushKey);
            }
            else if (number == BoundaryWordNumber)
            {
                // Через SetResourceReference, а не FindResource: інакше при зміні
                // теми підсвічене слово лишилося б із кольором старої.
                run.SetResourceReference(TextElement.BackgroundProperty, BoundaryBrushKey);
            }
            else if (number == _hoveredWordNumber)
            {
                run.SetResourceReference(TextElement.BackgroundProperty, HoverBrushKey);
            }
            else
            {
                run.ClearValue(TextElement.BackgroundProperty);
            }
        }

        private Run RunOf(int number)
        {
            return number >= 1 && number <= _wordRuns.Length ? _wordRuns[number - 1] : null;
        }

        private static int WordNumberAt(object source)
        {
            var run = source as Run;
            if (run == null)
            {
                return 0;
            }

            return run.Tag is int ? (int)run.Tag : 0;
        }
    }
}

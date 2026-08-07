using System.Collections.Generic;
using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
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

        public static readonly DependencyProperty WordCommandProperty = DependencyProperty.Register(
            "WordCommand",
            typeof(ICommand),
            typeof(WordReaderView),
            new PropertyMetadata(null));

        private readonly TextBlock _textBlock;

        // Runʼи слів за номером (номер − 1). Потрібні, щоб змінити тло одного слова,
        // не перебираючи тисячі інлайнів: підсвітка межі має бути миттєвою.
        private Run[] _wordRuns = new Run[0];

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

            _textBlock.MouseLeftButtonDown += OnTextMouseLeftButtonDown;
            _textBlock.MouseMove += OnTextMouseMove;
            _textBlock.MouseLeave += OnTextMouseLeave;

            Content = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Content = _textBlock
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

        /// <summary>Команда, яку викликає клік по слову. Параметр — номер слова.</summary>
        public ICommand WordCommand
        {
            get { return (ICommand)GetValue(WordCommandProperty); }
            set { SetValue(WordCommandProperty, value); }
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

            // Межа могла бути задана до того, як контрол став видимим.
            ApplyWordBrush(BoundaryWordNumber);
        }

        // ── Миша ──────────────────────────────────────────────────────────

        private void OnTextMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            var number = WordNumberAt(e.OriginalSource);
            if (number == 0)
            {
                return;
            }

            var command = WordCommand;
            if (command == null || !command.CanExecute(number))
            {
                return;
            }

            command.Execute(number);
            e.Handled = true;
        }

        private void OnTextMouseMove(object sender, MouseEventArgs e)
        {
            SetHoveredWord(WordNumberAt(e.OriginalSource));
        }

        private void OnTextMouseLeave(object sender, MouseEventArgs e)
        {
            SetHoveredWord(0);
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
        /// Привести тло слова до його стану: межа важливіша за наведення, бо
        /// інакше межа гасла б рівно тоді, коли вчитель на неї дивиться.
        /// </summary>
        private void ApplyWordBrush(int number)
        {
            var run = RunOf(number);
            if (run == null)
            {
                return;
            }

            if (number == BoundaryWordNumber)
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

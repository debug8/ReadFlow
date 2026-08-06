using System.Collections.Generic;
using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using ReadFlow.Models;

namespace ReadFlow.Views
{
    /// <summary>
    /// Режим читання: текст рендериться послівно, кожне слово — окремий <see cref="Run"/>
    /// з підказкою «Слово №N».
    ///
    /// Підказка задається рядком прямо на <c>Run</c>, а не окремим обробником миші.
    /// Це виглядає марнотратно, але насправді дешево: <c>ToolTipService</c> створює
    /// вікно підказки лише в момент показу, тож на 3000 слів ми додаємо 3000 коротких
    /// рядків (десятки кілобайт) і жодного зайвого візуального елемента.
    ///
    /// Runʼи потрібні не лише заради підказок: у Задачах 7 і 8 їм задаватимуть тло
    /// (межа читання, помилка), а зробити це можна тільки для окремого елемента.
    /// </summary>
    public class WordReaderView : UserControl
    {
        private const string WordNumberFormatKey = "Str_WordNumberFormat";
        private const string DefaultWordNumberFormat = "Слово №{0}";

        public static readonly DependencyProperty DocumentProperty = DependencyProperty.Register(
            "Document",
            typeof(ReadingDocument),
            typeof(WordReaderView),
            new PropertyMetadata(null, OnDocumentChanged));

        private readonly TextBlock _textBlock;
        private bool _needsRebuild = true;

        public WordReaderView()
        {
            _textBlock = new TextBlock
            {
                TextWrapping = TextWrapping.Wrap,
                LineStackingStrategy = LineStackingStrategy.BlockLineHeight,
                Padding = new Thickness(12)
            };

            // SetResourceReference — це DynamicResource у коді: при зміні теми
            // значення оновиться саме, без перебудови контрола.
            _textBlock.SetResourceReference(TextBlock.ForegroundProperty, "TextForegroundBrush");
            _textBlock.SetResourceReference(TextBlock.FontSizeProperty, "BaseFontSize");
            _textBlock.SetResourceReference(TextBlock.LineHeightProperty, "TextLineHeight");

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

        private static void OnDocumentChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            ((WordReaderView)d).InvalidateContent();
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

            foreach (var segment in segments)
            {
                var run = new Run(segment.Text);

                if (segment.IsWord)
                {
                    run.ToolTip = string.Format(CultureInfo.CurrentCulture, format, segment.WordNumber);
                }

                inlines.Add(run);
            }

            // AddRange, а не Add у циклі: інакше кожне додавання окремо інвалідує
            // розмітку, і на великому тексті перемикання в режим читання підвисає.
            _textBlock.Inlines.AddRange(inlines);
        }
    }
}

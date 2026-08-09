using System;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Media;
using ReadFlow.Core;

namespace ReadFlow.Views
{
    /// <summary>
    /// Друк тексту великим шрифтом — аркуш, який роздають учням.
    ///
    /// Живе у Views, а не в Core: <see cref="PrintDialog"/> — це діалог, і те
    /// саме міркування, що для вікна «Про програму». ViewModel про нього не знає,
    /// інакше її не можна було б перевірити без запуску інтерфейсу.
    /// </summary>
    public static class TextPrinter
    {
        /// <summary>
        /// Кегль друку. Помітно більший за екранний: аркуш роздають дитині,
        /// яка вчиться читати, а не дорослому.
        /// </summary>
        private const double FontSize = 20;

        private const double LineHeight = 34;

        /// <summary>Поля сторінки — один дюйм у пристроєвих одиницях WPF.</summary>
        private const double PageMargin = 96;

        private const double ParagraphSpacing = 14;

        /// <summary>Гарнітура для перемикача «шрифт, зручний для читання».</summary>
        private const string ReadingFontFamily = "Verdana, Tahoma, Segoe UI";

        /// <summary>
        /// Показати діалог друку й надрукувати текст.
        /// </summary>
        /// <param name="text">Текст із поля вводу.</param>
        /// <param name="documentName">Назва завдання в черзі друку.</param>
        /// <param name="useReadingFont">Чи ввімкнено «шрифт, зручний для читання».</param>
        /// <param name="paragraphs">Правило поділу на абзаци (специфікація, 4.6).</param>
        /// <returns><c>false</c>, якщо друкувати нічого або вчитель скасував діалог.</returns>
        public static bool Print(string text, string documentName, bool useReadingFont, ParagraphMode paragraphs)
        {
            if (string.IsNullOrWhiteSpace(text))
            {
                return false;
            }

            try
            {
                var dialog = new PrintDialog();

                if (dialog.ShowDialog() != true)
                {
                    return false;
                }

                var document = BuildDocument(text, useReadingFont, paragraphs, dialog);

                dialog.PrintDocument(
                    ((IDocumentPaginatorSource)document).DocumentPaginator,
                    documentName ?? string.Empty);

                return true;
            }
            catch (Exception ex)
            {
                // Немає принтера, драйвер відмовив, завдання скасоване вже в черзі —
                // усе це не привід валити застосунок посеред уроку.
                Debug.WriteLine("ReadFlow: не вдалося надрукувати — " + ex.Message);
                return false;
            }
        }

        private static FlowDocument BuildDocument(
            string text, bool useReadingFont, ParagraphMode paragraphs, PrintDialog dialog)
        {
            var document = new FlowDocument
            {
                FontSize = FontSize,
                LineHeight = LineHeight,
                PagePadding = new Thickness(PageMargin),
                PageWidth = dialog.PrintableAreaWidth,
                PageHeight = dialog.PrintableAreaHeight,

                // Без цього FlowDocument сам поділить сторінку на кілька колонок:
                // ширина колонки за замовчуванням фіксована, і на аркуші A4
                // великий текст поїхав би у два стовпчики.
                ColumnWidth = double.PositiveInfinity,

                // Колір теми на папір не переноситься. У темній темі аркуш вийшов би
                // білим по чорному — тобто чорною сторінкою й порожньою касетою.
                Foreground = Brushes.Black,
                Background = Brushes.White,

                TextAlignment = TextAlignment.Left
            };

            if (useReadingFont)
            {
                document.FontFamily = new FontFamily(ReadingFontFamily);
            }

            // Поділ на абзаци — той самий, за яким їх рахує нижня панель
            // (специфікація, 4.6). Інакше вчитель бачив би на екрані одне
            // число абзаців, а на аркуші — інше розбиття.
            foreach (var paragraph in TextStatsCalculator.GetParagraphs(text, paragraphs))
            {
                document.Blocks.Add(new Paragraph(new Run(paragraph))
                {
                    Margin = new Thickness(0, 0, 0, ParagraphSpacing)
                });
            }

            return document;
        }
    }
}

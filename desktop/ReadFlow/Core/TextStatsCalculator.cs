using System;
using System.Collections.Generic;
using System.Text;
using ReadFlow.Models;

namespace ReadFlow.Core
{
    /// <summary>
    /// Правила підрахунку з розділу 4 специфікації — в одному місці.
    ///
    /// Клас без стану й без залежностей від WPF: те саме має бути буквально
    /// перенесене в Android-версію. Якщо змінюється правило — правляться
    /// специфікація, обидві реалізації і тести, одним комітом.
    /// </summary>
    public static class TextStatsCalculator
    {
        // Апостроф. Word сам замінює ' на ’, українські розкладки дають ʼ —
        // без повного списку те саме слово рахувалося б по-різному.
        private const string Apostrophes = "'’ʼ‘´`";

        // Дефіс. Тире (– —) і мінус (−) сюди свідомо НЕ входять: вони розділяють слова.
        private const string Hyphens = "-‐‑";

        private const string SentenceTerminators = ".!?…";

        /// <summary>
        /// Розібрати текст на слова з їхніми межами.
        /// Індекси відповідають <b>вихідному</b> тексту, тому їх можна одразу
        /// використовувати для рендеру й підсвічування.
        /// </summary>
        public static IReadOnlyList<WordToken> GetWords(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                return new List<WordToken>(0);
            }

            // Приблизно одне слово на 6 символів — щоб список не перевиділявся на великих текстах.
            var words = new List<WordToken>(text.Length / 6 + 4);
            var position = 0;

            while (position < text.Length)
            {
                if (!IsLetter(text[position]))
                {
                    position++;
                    continue;
                }

                var start = position;
                var letters = 1;
                position++;

                while (position < text.Length)
                {
                    if (IsLetter(text[position]))
                    {
                        letters++;
                        position++;
                        continue;
                    }

                    // Сполучник входить у слово, лише якщо праворуч від нього теж буква.
                    // Ліворуч буква вже гарантована: ми всередині слова.
                    if (IsJoiner(text[position]) &&
                        position + 1 < text.Length &&
                        IsLetter(text[position + 1]))
                    {
                        letters++;
                        position += 2;
                        continue;
                    }

                    break;
                }

                words.Add(new WordToken(
                    words.Count + 1,
                    text.Substring(start, position - start),
                    start,
                    letters));
            }

            return words;
        }

        /// <summary>
        /// Порахувати всю статистику тексту.
        /// </summary>
        /// <param name="options">Параметри користувача; <c>null</c> — значення за замовчуванням.</param>
        public static TextStats Calculate(string text, CountingOptions options = null)
        {
            return Calculate(text, GetWords(text), options);
        }

        /// <summary>
        /// Те саме, але з уже розібраними словами — щоб не робити розбір двічі,
        /// коли слова однаково потрібні для рендеру.
        /// </summary>
        public static TextStats Calculate(string text, IReadOnlyList<WordToken> words, CountingOptions options)
        {
            if (string.IsNullOrEmpty(text))
            {
                return TextStats.Empty;
            }

            if (words == null)
            {
                words = GetWords(text);
            }

            var mode = (options ?? CountingOptions.Default).Paragraphs;

            // Знаки рахуємо на нормалізованому тексті: інакше \r\n дав би на два знаки
            // більше, ніж той самий текст на Android.
            var normalized = NormalizeLineEndings(text);

            var charCountNoSpaces = 0;
            var letterCount = 0;

            for (var i = 0; i < normalized.Length; i++)
            {
                var c = normalized[i];

                if (!char.IsWhiteSpace(c))
                {
                    charCountNoSpaces++;
                }

                if (IsLetter(c))
                {
                    letterCount++;
                }
            }

            // Округлення робиться на ТОЧНОМУ дробі букви/слова, а не на Double.
            //
            // 81/20 — це рівно 4.05, але як Double воно зберігається як
            // 4.04999999999999982…, і чесне округлення такого числа дає 4.0.
            // Math.Round для Double цю похибку компенсує й повертає 4.1, проте
            // покладатися на це не можна: Microsoft прямо попереджає, що для
            // Double серединні значення можуть округлюватися неочікувано.
            // Kotlin такої компенсації не робить узагалі — саме тут платформи
            // й розійшлися. decimal ділить два цілих точно, тож питання зникає.
            var averageWordLength = words.Count == 0
                ? 0d
                : (double)Math.Round((decimal)letterCount / words.Count, 1, MidpointRounding.AwayFromZero);

            return new TextStats(
                words.Count,
                normalized.Length,
                charCountNoSpaces,
                letterCount,
                averageWordLength,
                CountSentences(normalized),
                CountParagraphs(normalized, mode));
        }

        /// <summary>
        /// Скільки знаків без пробілів у тексті до позиції <paramref name="endIndex"/>
        /// (не включно).
        ///
        /// Потрібно для «знаків за хвилину», коли читання зупинилося на слові-межі:
        /// у формулу мають піти знаки лише до неї (специфікація, 4.7).
        ///
        /// Нормалізувати переноси рядків тут не треба, і це не недогляд: і <c>\r</c>,
        /// і <c>\n</c> — пробільні символи, тож у підрахунок без пробілів вони не
        /// потрапляють у жодному разі. А індекси слів — від вихідного тексту, тож
        /// нормалізація ще й зсунула б їх.
        /// </summary>
        public static int CountCharsNoSpaces(string text, int endIndex)
        {
            if (string.IsNullOrEmpty(text) || endIndex <= 0)
            {
                return 0;
            }

            if (endIndex > text.Length)
            {
                endIndex = text.Length;
            }

            var count = 0;

            for (var i = 0; i < endIndex; i++)
            {
                if (!char.IsWhiteSpace(text[i]))
                {
                    count++;
                }
            }

            return count;
        }

        /// <summary>Буква — Unicode-літера або цифра (специфікація, 4.1).</summary>
        public static bool IsLetter(char c)
        {
            // Сполучник ніколи не буква — і це не формальність.
            // Український апостроф ʼ (U+02BC) належить до категорії Lm, тож
            // char.IsLetter вважає його літерою. Без цієї перевірки «мавпʼячий»
            // мав би 9 букв, а «мавп'ячий» — 8: те саме слово, різні числа
            // залежно від того, звідки скопійовано текст.
            if (IsJoiner(c))
            {
                return false;
            }

            return char.IsLetter(c) || char.IsDigit(c);
        }

        /// <summary>Сполучник — апостроф або дефіс; тире й мінус сюди не входять.</summary>
        public static bool IsJoiner(char c)
        {
            return Apostrophes.IndexOf(c) >= 0 || Hyphens.IndexOf(c) >= 0;
        }

        private static int CountSentences(string text)
        {
            var count = 0;
            var hasContent = false;

            for (var i = 0; i < text.Length; i++)
            {
                var c = text[i];

                if (SentenceTerminators.IndexOf(c) >= 0)
                {
                    if (hasContent)
                    {
                        count++;
                        hasContent = false;
                    }

                    // Кілька роздільників поспіль («Ого!!!») дають одне речення:
                    // hasContent уже false, тож наступні просто пропускаються.
                    continue;
                }

                if (IsLetter(c))
                {
                    hasContent = true;
                }
            }

            // Останній фрагмент без крапки теж є реченням.
            if (hasContent)
            {
                count++;
            }

            return count;
        }

        /// <summary>
        /// Розбити текст на абзаци за тим самим правилом, за яким вони рахуються (4.6).
        ///
        /// Потрібно для друку: аркуш, розданий учням, має бути поділений так само,
        /// як показує лічильник абзаців. Окремої логіки розбиття свідомо немає —
        /// <see cref="CountParagraphs"/> рахує рівно те, що повертає цей метод,
        /// інакше два правила рано чи пізно розійшлися б.
        ///
        /// У режимі «блок між порожніми рядками» рядки одного абзацу зливаються
        /// через пробіл: там вони — жорсткі переноси всередині абзацу, а не
        /// окремі абзаци.
        /// </summary>
        public static IReadOnlyList<string> GetParagraphs(string text, ParagraphMode mode)
        {
            if (string.IsNullOrEmpty(text))
            {
                return new string[0];
            }

            return SplitParagraphs(NormalizeLineEndings(text), mode);
        }

        private static int CountParagraphs(string text, ParagraphMode mode)
        {
            return SplitParagraphs(text, mode).Count;
        }

        /// <summary>Спільна робота обох методів вище. Текст уже нормалізований.</summary>
        private static List<string> SplitParagraphs(string text, ParagraphMode mode)
        {
            var result = new List<string>();
            var line = new StringBuilder();
            var block = new StringBuilder();
            var lineHasContent = false;

            for (var i = 0; i <= text.Length; i++)
            {
                var endOfLine = i == text.Length || text[i] == '\n';

                if (!endOfLine)
                {
                    if (!char.IsWhiteSpace(text[i]))
                    {
                        lineHasContent = true;
                    }

                    line.Append(text[i]);
                    continue;
                }

                if (lineHasContent)
                {
                    if (mode == ParagraphMode.NonEmptyLines)
                    {
                        result.Add(line.ToString().Trim());
                    }
                    else
                    {
                        // У режимі блоків новий абзац починається лише після
                        // порожнього рядка, тож сусідні рядки накопичуються.
                        if (block.Length > 0)
                        {
                            block.Append(' ');
                        }

                        block.Append(line.ToString().Trim());
                    }
                }
                else if (block.Length > 0)
                {
                    result.Add(block.ToString());
                    block.Length = 0;
                }

                line.Length = 0;
                lineHasContent = false;
            }

            if (block.Length > 0)
            {
                result.Add(block.ToString());
            }

            return result;
        }

        /// <summary>
        /// Звести <c>\r\n</c> і одиночний <c>\r</c> до <c>\n</c>.
        /// Якщо <c>\r</c> у тексті немає — повертається той самий рядок без копіювання.
        /// </summary>
        private static string NormalizeLineEndings(string text)
        {
            if (text.IndexOf('\r') < 0)
            {
                return text;
            }

            var builder = new StringBuilder(text.Length);

            for (var i = 0; i < text.Length; i++)
            {
                var c = text[i];

                if (c == '\r')
                {
                    builder.Append('\n');

                    if (i + 1 < text.Length && text[i + 1] == '\n')
                    {
                        i++;
                    }

                    continue;
                }

                builder.Append(c);
            }

            return builder.ToString();
        }
    }
}

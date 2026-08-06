using System;
using System.Collections.Generic;

namespace ReadFlow.Models
{
    /// <summary>
    /// Шматок тексту для послівного рендеру: або слово, або те, що між словами
    /// (пробіли, розділові знаки, переноси рядків).
    /// </summary>
    public class ReadingSegment
    {
        public ReadingSegment(string text, int wordNumber)
        {
            Text = text;
            WordNumber = wordNumber;
        }

        /// <summary>Текст шматка, як він записаний у вихідному тексті.</summary>
        public string Text { get; private set; }

        /// <summary>Номер слова від 1, або 0 якщо це роздільник.</summary>
        public int WordNumber { get; private set; }

        /// <summary>Чи є цей шматок словом (а не роздільником).</summary>
        public bool IsWord
        {
            get { return WordNumber > 0; }
        }

        public override string ToString()
        {
            return IsWord ? WordNumber + ": " + Text : "[" + Text + "]";
        }
    }

    /// <summary>
    /// Текст разом із розібраними словами — те, що потрібно режиму читання.
    ///
    /// Текст і слова тримаються разом свідомо: якби View отримував їх двома
    /// окремими привʼязками, між оновленнями траплявся б момент, коли межі слів
    /// уже нові, а текст ще старий — і рендер зрізав би слова посеред символів.
    /// </summary>
    public class ReadingDocument
    {
        public static readonly ReadingDocument Empty =
            new ReadingDocument(string.Empty, new List<WordToken>(0));

        public ReadingDocument(string text, IReadOnlyList<WordToken> words)
        {
            Text = text ?? string.Empty;
            Words = words ?? new List<WordToken>(0);
        }

        public string Text { get; private set; }

        public IReadOnlyList<WordToken> Words { get; private set; }

        /// <summary>
        /// Розбити текст на послідовність слів і роздільників.
        ///
        /// Логіка живе в моделі, а не у View: її треба покрити тестами, а тест
        /// на WPF-контрол коштував би дорожче й ловив би менше.
        /// Гарантія: склеївши <see cref="ReadingSegment.Text"/> усіх сегментів
        /// по порядку, отримаємо рівно вихідний текст — жоден символ не губиться
        /// й не дублюється.
        /// </summary>
        public IReadOnlyList<ReadingSegment> GetSegments()
        {
            var segments = new List<ReadingSegment>(Words.Count * 2 + 1);

            if (Text.Length == 0)
            {
                return segments;
            }

            var position = 0;

            foreach (var word in Words)
            {
                // Захист від неузгоджених даних: якщо межі слів не з цього тексту,
                // краще показати текст без нумерації, ніж кинути виняток у рендері.
                if (word.Start < position || word.End > Text.Length)
                {
                    return new List<ReadingSegment>
                    {
                        new ReadingSegment(Text, 0)
                    };
                }

                if (word.Start > position)
                {
                    segments.Add(new ReadingSegment(Text.Substring(position, word.Start - position), 0));
                }

                segments.Add(new ReadingSegment(word.Text, word.Number));
                position = word.End;
            }

            if (position < Text.Length)
            {
                segments.Add(new ReadingSegment(Text.Substring(position), 0));
            }

            return segments;
        }
    }
}

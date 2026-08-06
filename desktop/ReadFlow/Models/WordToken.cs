namespace ReadFlow.Models
{
    /// <summary>
    /// Одне слово тексту разом із його межами. Межі потрібні, щоб у режимі читання
    /// відрендерити кожне слово окремим елементом і показати «Слово №N» (Задача 4),
    /// а також щоб клік по слову визначав межу читання (Задача 7).
    /// </summary>
    public class WordToken
    {
        public WordToken(int number, string text, int start, int letterCount)
        {
            Number = number;
            Text = text;
            Start = start;
            LetterCount = letterCount;
        }

        /// <summary>Порядковий номер слова в тексті, від 1.</summary>
        public int Number { get; private set; }

        /// <summary>Саме слово, як воно записане в тексті (разом з апострофами й дефісами).</summary>
        public string Text { get; private set; }

        /// <summary>Індекс першого символу слова у вихідному тексті.</summary>
        public int Start { get; private set; }

        /// <summary>Довжина слова в символах.</summary>
        public int Length
        {
            get { return Text.Length; }
        }

        /// <summary>Індекс символу одразу після слова (напівінтервал <c>[Start, End)</c>).</summary>
        public int End
        {
            get { return Start + Text.Length; }
        }

        /// <summary>Кількість букв у слові — без апострофів і дефісів (див. специфікацію, 4.1).</summary>
        public int LetterCount { get; private set; }

        public override string ToString()
        {
            return Number + ": " + Text;
        }
    }
}

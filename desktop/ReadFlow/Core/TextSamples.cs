using System.Collections.Generic;

namespace ReadFlow.Core
{
    /// <summary>
    /// Рівень складності зразка. Клас у назві («Простий (1–2 клас)») — орієнтир
    /// для вчителя, а не фільтр: тексти всіх рівнів завжди видно.
    ///
    /// Чому саме так, а не «клас × рівень»: клас у застосунку вже визначає норму
    /// (4.9). Якби список зразків фільтрувався за класом, учитель, якому потрібен
    /// простіший текст, мусив би перемкнути клас — і мовчки змінив би норму,
    /// за якою дитину оцінять.
    /// </summary>
    public sealed class SampleLevel
    {
        public SampleLevel(string id, string label, string hint)
        {
            Id = id;
            Label = label;
            Hint = hint ?? string.Empty;
        }

        public string Id { get; private set; }

        public string Label { get; private set; }

        /// <summary>Чим цей рівень відрізняється. Показується підказкою.</summary>
        public string Hint { get; private set; }
    }

    /// <summary>
    /// Зразок тексту. Сам текст тут не зберігається: файл читається лише в мить
    /// вставки. Тримати в памʼяті всі тексти, з яких вчитель за урок відкриє
    /// щонайбільше один, немає сенсу.
    /// </summary>
    public sealed class TextSample
    {
        public TextSample(string id, string title, string file, string levelId, int words)
        {
            Id = id;
            Title = title;
            File = file;
            LevelId = levelId;
            Words = words;
        }

        public string Id { get; private set; }

        public string Title { get; private set; }

        /// <summary>Імʼя файлу в <c>shared/samples/</c>, без шляху.</summary>
        public string File { get; private set; }

        public string LevelId { get; private set; }

        /// <summary>
        /// Кількість слів за правилами розділу 4. Записана в реєстрі, а не
        /// порахована на льоту: за нею рахується приблизний час читання ще до
        /// того, як файл відкрито. Тест звіряє її з фактичним текстом.
        /// </summary>
        public int Words { get; private set; }
    }

    /// <summary>
    /// Реєстр зразків із <c>shared/samples/index.json</c>.
    /// </summary>
    public sealed class SamplesCatalog
    {
        public static readonly SamplesCatalog Empty =
            new SamplesCatalog(new SampleLevel[0], new TextSample[0]);

        public SamplesCatalog(IList<SampleLevel> levels, IList<TextSample> samples)
        {
            Levels = new List<SampleLevel>(levels ?? new SampleLevel[0]);
            Samples = new List<TextSample>(samples ?? new TextSample[0]);
        }

        public IList<SampleLevel> Levels { get; private set; }

        /// <summary>Зразки в порядку з реєстру — упорядкування задає файл, не код.</summary>
        public IList<TextSample> Samples { get; private set; }

        public bool IsEmpty
        {
            get { return Samples.Count == 0; }
        }

        /// <summary>Рівень за його <c>id</c> або <c>null</c>.</summary>
        public SampleLevel FindLevel(string id)
        {
            if (string.IsNullOrEmpty(id))
            {
                return null;
            }

            foreach (var level in Levels)
            {
                if (level.Id == id)
                {
                    return level;
                }
            }

            return null;
        }
    }
}

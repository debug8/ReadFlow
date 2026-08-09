using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Windows;

namespace ReadFlow.Core
{
    /// <summary>
    /// Читає реєстр зразків і самі тексти з <c>shared/samples/</c>, вбудованих
    /// у застосунок під час збірки.
    ///
    /// На відміну від норм, зовнішнього файлу поруч із <c>.exe</c> тут немає:
    /// свій текст учитель просто вставляє в поле, і городити для цього папку
    /// з реєстром було б складніше, ніж Ctrl+V.
    ///
    /// Пошкоджений реєстр нічого не валить — просто немає кнопки «Вставити зразок».
    /// </summary>
    public static class SamplesLoader
    {
        private const string IndexUri =
            "pack://application:,,,/ReadFlow;component/Resources/samples/index.json";

        private const string SampleUriFormat =
            "pack://application:,,,/ReadFlow;component/Resources/samples/{0}";

        private static SamplesCatalog _current;

        /// <summary>
        /// Реєстр зразків. Читається один раз: файли вбудовані в <c>.exe</c>
        /// і за час роботи не змінюються.
        /// </summary>
        public static SamplesCatalog Current
        {
            get { return _current ?? (_current = Load()); }
        }

        /// <summary>Прочитати реєстр. Ніколи не кидає виняток.</summary>
        public static SamplesCatalog Load()
        {
            var text = ReadResource(IndexUri);

            if (text == null)
            {
                Debug.WriteLine("ReadFlow: реєстр зразків не прочитався.");
                return SamplesCatalog.Empty;
            }

            return Parse(text) ?? SamplesCatalog.Empty;
        }

        /// <summary>
        /// Текст зразка або <c>null</c>, якщо файл не читається. Реєстр може
        /// згадувати файл, якого немає: тоді краще нічого не вставити, ніж
        /// підставити порожній рядок і зробити вигляд, що все гаразд.
        /// </summary>
        public static string LoadBody(TextSample sample)
        {
            if (sample == null || string.IsNullOrEmpty(sample.File))
            {
                return null;
            }

            var uri = string.Format(
                System.Globalization.CultureInfo.InvariantCulture, SampleUriFormat, sample.File);

            var body = ReadResource(uri);

            if (body == null)
            {
                Debug.WriteLine("ReadFlow: зразок '" + sample.File + "' не прочитався.");
            }

            return body;
        }

        /// <summary>
        /// Розібрати реєстр або <c>null</c>, якщо він непридатний.
        /// </summary>
        public static SamplesCatalog Parse(string json)
        {
            if (string.IsNullOrWhiteSpace(json))
            {
                return null;
            }

            SamplesFile file;

            try
            {
                var serializer = new DataContractJsonSerializer(typeof(SamplesFile));

                using (var stream = new MemoryStream(new UTF8Encoding(false).GetBytes(json)))
                {
                    file = serializer.ReadObject(stream) as SamplesFile;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: index.json зразків не розібрався — " + ex.Message);
                return null;
            }

            if (file == null)
            {
                return null;
            }

            var levels = ConvertLevels(file);
            var samples = ConvertSamples(file, levels);

            return samples.Count == 0 ? null : new SamplesCatalog(levels, samples);
        }

        private static List<SampleLevel> ConvertLevels(SamplesFile file)
        {
            var result = new List<SampleLevel>();

            if (file.Levels == null)
            {
                return result;
            }

            foreach (var level in file.Levels)
            {
                if (level == null || string.IsNullOrWhiteSpace(level.Id) ||
                    string.IsNullOrWhiteSpace(level.Label))
                {
                    continue;
                }

                result.Add(new SampleLevel(level.Id, level.Label, level.Hint));
            }

            return result;
        }

        private static List<TextSample> ConvertSamples(SamplesFile file, List<SampleLevel> levels)
        {
            var result = new List<TextSample>();

            if (file.Samples == null)
            {
                return result;
            }

            foreach (var sample in file.Samples)
            {
                if (sample == null ||
                    string.IsNullOrWhiteSpace(sample.File) ||
                    string.IsNullOrWhiteSpace(sample.Title) ||
                    sample.Words <= 0)
                {
                    Debug.WriteLine("ReadFlow: у реєстрі зразків пропущено неповний запис.");
                    continue;
                }

                // Зразок із невідомим рівнем пропускаємо: у списку він опинився б
                // без підпису рівня, і вчитель не зрозумів би, кому його давати.
                if (FindLevel(levels, sample.Level) == null)
                {
                    Debug.WriteLine("ReadFlow: зразок '" + sample.File +
                                    "' посилається на невідомий рівень '" + sample.Level + "'.");
                    continue;
                }

                result.Add(new TextSample(sample.Id, sample.Title, sample.File, sample.Level, sample.Words));
            }

            return result;
        }

        private static SampleLevel FindLevel(List<SampleLevel> levels, string id)
        {
            if (string.IsNullOrEmpty(id))
            {
                return null;
            }

            foreach (var level in levels)
            {
                if (level.Id == id)
                {
                    return level;
                }
            }

            return null;
        }

        /// <summary>Вміст вбудованого ресурсу або <c>null</c>.</summary>
        private static string ReadResource(string uri)
        {
            try
            {
                var info = Application.GetResourceStream(new Uri(uri, UriKind.Absolute));

                if (info == null || info.Stream == null)
                {
                    return null;
                }

                // StreamReader сам знімає BOM, якщо він раптом є.
                using (var reader = new StreamReader(info.Stream, Encoding.UTF8, true))
                {
                    return reader.ReadToEnd();
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося прочитати ресурс " + uri + " — " + ex.Message);
                return null;
            }
        }

        // ── Формат реєстру ────────────────────────────────────────────────

        [DataContract]
        public sealed class SamplesFile
        {
            [DataMember(Name = "version")]
            public int Version { get; set; }

            [DataMember(Name = "levels")]
            public SamplesFileLevel[] Levels { get; set; }

            [DataMember(Name = "samples")]
            public SamplesFileSample[] Samples { get; set; }
        }

        [DataContract]
        public sealed class SamplesFileLevel
        {
            [DataMember(Name = "id")]
            public string Id { get; set; }

            [DataMember(Name = "label")]
            public string Label { get; set; }

            [DataMember(Name = "hint")]
            public string Hint { get; set; }
        }

        [DataContract]
        public sealed class SamplesFileSample
        {
            [DataMember(Name = "id")]
            public string Id { get; set; }

            [DataMember(Name = "title")]
            public string Title { get; set; }

            [DataMember(Name = "file")]
            public string File { get; set; }

            [DataMember(Name = "level")]
            public string Level { get; set; }

            [DataMember(Name = "words")]
            public int Words { get; set; }
        }
    }
}

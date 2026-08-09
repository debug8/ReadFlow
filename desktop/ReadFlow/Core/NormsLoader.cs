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
    /// Читає довідник норм із <c>norms.json</c>.
    ///
    /// Два джерела, у такому порядку:
    /// 1. файл <c>norms.json</c> поруч із <c>.exe</c> — щоб учитель міг
    ///    поправити норми, не перезбираючи застосунок;
    /// 2. вбудований у <c>.exe</c> ресурс — копія <c>shared/norms.json</c>,
    ///    підключена лінком у <c>ReadFlow.csproj</c>.
    ///
    /// Пошкоджений або відсутній файл нічого не валить: у гіршому разі
    /// довідник порожній, і застосунок просто не показує оцінки за нормою.
    /// </summary>
    public static class NormsLoader
    {
        /// <summary>Імʼя файлу, який шукається поруч із <c>.exe</c>.</summary>
        public const string FileName = "norms.json";

        /// <summary>
        /// Версія формату, яку розуміє цей код. Довідник новішої версії
        /// не читаємо: краще лишитися без оцінки, ніж показати число,
        /// зрозуміле нам не так, як його задумали.
        /// </summary>
        public const int SupportedVersion = 1;

        private const string ResourceUri =
            "pack://application:,,,/ReadFlow;component/Resources/norms.json";

        private static NormsCatalog _current;

        /// <summary>
        /// Довідник для застосунку. Читається один раз: файл на диску за час
        /// роботи не змінюється, а от читати його на кожен замір — марно.
        /// </summary>
        public static NormsCatalog Current
        {
            get { return _current ?? (_current = Load()); }
        }

        /// <summary>Повний шлях до файлу, який шукається поруч із <c>.exe</c>.</summary>
        public static string ExternalPath
        {
            get
            {
                try
                {
                    return Path.Combine(AppDomain.CurrentDomain.BaseDirectory, FileName);
                }
                catch (Exception ex)
                {
                    Debug.WriteLine("ReadFlow: не вдалося визначити шлях до " + FileName + " — " + ex.Message);
                    return null;
                }
            }
        }

        /// <summary>
        /// Прочитати довідник: спершу файл поруч із <c>.exe</c>, потім вбудований
        /// ресурс. Ніколи не кидає виняток — у найгіршому разі
        /// повертає <see cref="NormsCatalog.Empty"/>.
        /// </summary>
        public static NormsCatalog Load()
        {
            var external = LoadFromFile(ExternalPath);
            if (external != null)
            {
                return external;
            }

            var embedded = LoadFromResource();
            if (embedded != null)
            {
                return embedded;
            }

            Debug.WriteLine("ReadFlow: норми не прочитались ні з файлу, ні з ресурсу.");
            return NormsCatalog.Empty;
        }

        /// <summary>
        /// Прочитати довідник із файлу або <c>null</c>, якщо файлу немає
        /// чи він не читається.
        /// </summary>
        public static NormsCatalog LoadFromFile(string path)
        {
            if (string.IsNullOrEmpty(path))
            {
                return null;
            }

            try
            {
                if (!File.Exists(path))
                {
                    return null;
                }

                // StreamReader сам знімає BOM, якщо він є: DataContractJsonSerializer
                // на BOM спотикається, а файл, збережений Блокнотом, його має.
                using (var reader = new StreamReader(path, Encoding.UTF8, true))
                {
                    return Parse(reader.ReadToEnd());
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося прочитати " + path + " — " + ex.Message);
                return null;
            }
        }

        /// <summary>
        /// Прочитати довідник із вбудованого ресурсу або <c>null</c>.
        /// </summary>
        public static NormsCatalog LoadFromResource()
        {
            try
            {
                var info = Application.GetResourceStream(new Uri(ResourceUri, UriKind.Absolute));
                if (info == null || info.Stream == null)
                {
                    return null;
                }

                using (var reader = new StreamReader(info.Stream, Encoding.UTF8, true))
                {
                    return Parse(reader.ReadToEnd());
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося прочитати вбудований " + FileName + " — " + ex.Message);
                return null;
            }
        }

        /// <summary>
        /// Розібрати JSON довідника або <c>null</c>, якщо він непридатний:
        /// не парситься, має незнайому версію або жодного класу.
        /// </summary>
        public static NormsCatalog Parse(string json)
        {
            if (string.IsNullOrWhiteSpace(json))
            {
                return null;
            }

            NormsFile file;

            try
            {
                var serializer = new DataContractJsonSerializer(typeof(NormsFile));

                using (var stream = new MemoryStream(new UTF8Encoding(false).GetBytes(json)))
                {
                    file = serializer.ReadObject(stream) as NormsFile;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: " + FileName + " не розібрався — " + ex.Message);
                return null;
            }

            if (file == null)
            {
                return null;
            }

            if (file.Version > SupportedVersion)
            {
                Debug.WriteLine("ReadFlow: " + FileName + " версії " + file.Version +
                                ", а ця збірка розуміє до " + SupportedVersion + " — довідник пропущено.");
                return null;
            }

            var grades = ConvertGrades(file);
            if (grades.Count == 0)
            {
                Debug.WriteLine("ReadFlow: у " + FileName + " немає жодного придатного класу.");
                return null;
            }

            var labels = file.Evaluation == null
                ? NormLabels.Fallback
                : new NormLabels(file.Evaluation.Below, file.Evaluation.Within, file.Evaluation.Above);

            return new NormsCatalog(file.Version, grades, labels, file.Source, file.Note);
        }

        private static List<GradeNorms> ConvertGrades(NormsFile file)
        {
            var result = new List<GradeNorms>();

            if (file.Grades == null)
            {
                return result;
            }

            foreach (var grade in file.Grades)
            {
                if (grade == null || grade.Grade <= 0 || grade.Semesters == null)
                {
                    continue;
                }

                var semesters = new List<ReadingNorm>();

                foreach (var semester in grade.Semesters)
                {
                    // Норма з переверненими або відʼємними межами — це не норма.
                    // Пропускаємо саме її, а не весь довідник: одна крива цифра
                    // не має позбавляти вчителя решти класів.
                    if (semester == null || semester.Semester <= 0 ||
                        semester.Min < 0 || semester.Max < semester.Min)
                    {
                        Debug.WriteLine("ReadFlow: у " + FileName + " пропущено норму класу " +
                                        grade.Grade + " — межі некоректні.");
                        continue;
                    }

                    semesters.Add(new ReadingNorm(grade.Grade, semester.Semester, semester.Min, semester.Max));
                }

                if (semesters.Count == 0)
                {
                    continue;
                }

                var label = string.IsNullOrWhiteSpace(grade.Label)
                    ? grade.Grade.ToString(System.Globalization.CultureInfo.CurrentCulture)
                    : grade.Label;

                result.Add(new GradeNorms(grade.Grade, label, semesters));
            }

            return result;
        }

        // ── Формат файлу ──────────────────────────────────────────────────
        //
        // DataContractJsonSerializer, а не сторонній парсер: залежності
        // тримаємо на мінімумі (специфікація, 5). Невідомі поля — як-от
        // "$comment" і "unit" — він просто ігнорує.

        [DataContract]
        public sealed class NormsFile
        {
            [DataMember(Name = "version")]
            public int Version { get; set; }

            /// <summary>Звідки норми. Показується вчителю у вікні «Норми читання».</summary>
            [DataMember(Name = "source")]
            public string Source { get; set; }

            /// <summary>Застереження до норм. Теж показується вчителю.</summary>
            [DataMember(Name = "note")]
            public string Note { get; set; }

            [DataMember(Name = "grades")]
            public NormsFileGrade[] Grades { get; set; }

            [DataMember(Name = "evaluation")]
            public NormsFileEvaluation Evaluation { get; set; }
        }

        [DataContract]
        public sealed class NormsFileGrade
        {
            [DataMember(Name = "grade")]
            public int Grade { get; set; }

            [DataMember(Name = "label")]
            public string Label { get; set; }

            [DataMember(Name = "semesters")]
            public NormsFileSemester[] Semesters { get; set; }
        }

        [DataContract]
        public sealed class NormsFileSemester
        {
            [DataMember(Name = "semester")]
            public int Semester { get; set; }

            [DataMember(Name = "min")]
            public int Min { get; set; }

            [DataMember(Name = "max")]
            public int Max { get; set; }
        }

        [DataContract]
        public sealed class NormsFileEvaluation
        {
            [DataMember(Name = "below")]
            public string Below { get; set; }

            [DataMember(Name = "within")]
            public string Within { get; set; }

            [DataMember(Name = "above")]
            public string Above { get; set; }
        }
    }
}

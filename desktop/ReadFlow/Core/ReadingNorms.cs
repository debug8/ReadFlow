using System;
using System.Collections.Generic;

namespace ReadFlow.Core
{
    /// <summary>
    /// Оцінка результату відносно норми (специфікація, 4.9).
    /// </summary>
    public enum NormEvaluation
    {
        /// <summary>Норму не визначено: клас не обраний або його немає в довіднику.</summary>
        Unknown = 0,

        Below = 1,
        Within = 2,
        Above = 3
    }

    /// <summary>
    /// Норма для одного класу й семестру. Межі — включно.
    /// </summary>
    public sealed class ReadingNorm
    {
        public ReadingNorm(int grade, int semester, int min, int max)
        {
            Grade = grade;
            Semester = semester;
            Min = min;
            Max = max;
        }

        public int Grade { get; private set; }

        public int Semester { get; private set; }

        /// <summary>Нижня межа норми, слів за хвилину. Входить у норму.</summary>
        public int Min { get; private set; }

        /// <summary>Верхня межа норми, слів за хвилину. Входить у норму.</summary>
        public int Max { get; private set; }
    }

    /// <summary>
    /// Клас із його нормами по семестрах. Назва класу приходить із
    /// <c>shared/norms.json</c>, а не збирається в коді: інакше українська
    /// «1 клас» опинилася б у двох місцях і розійшлася б із Android.
    /// </summary>
    public sealed class GradeNorms
    {
        public GradeNorms(int grade, string label, IList<ReadingNorm> semesters)
        {
            Grade = grade;
            Label = label;
            Semesters = new List<ReadingNorm>(semesters ?? new ReadingNorm[0]);
        }

        public int Grade { get; private set; }

        /// <summary>Підпис для списку в панелі налаштувань, напр. «2 клас».</summary>
        public string Label { get; private set; }

        public IList<ReadingNorm> Semesters { get; private set; }
    }

    /// <summary>
    /// Підписи оцінок. Живуть у тому самому <c>norms.json</c>, що й числа:
    /// вони частина довідника, а не інтерфейсу, і мусять бути однакові
    /// на десктопі й на телефоні.
    /// </summary>
    public sealed class NormLabels
    {
        // Запасні підписи на випадок, коли в довіднику блоку evaluation немає.
        // Це не обхід правила «норми не хардкодяться»: правило про числа норм,
        // а тут — три слова інтерфейсу, без яких на екрані була б порожнеча
        // замість оцінки. Чинні підписи завжди беруться з norms.json.
        public const string DefaultBelow = "нижче норми";
        public const string DefaultWithin = "у межах норми";
        public const string DefaultAbove = "вище норми";

        public static readonly NormLabels Fallback = new NormLabels(null, null, null);

        public NormLabels(string below, string within, string above)
        {
            Below = string.IsNullOrWhiteSpace(below) ? DefaultBelow : below;
            Within = string.IsNullOrWhiteSpace(within) ? DefaultWithin : within;
            Above = string.IsNullOrWhiteSpace(above) ? DefaultAbove : above;
        }

        public string Below { get; private set; }

        public string Within { get; private set; }

        public string Above { get; private set; }
    }

    /// <summary>
    /// Довідник норм техніки читання.
    ///
    /// Ані числа, ані підписи тут не зашиті: усе приходить із
    /// <c>shared/norms.json</c> (непорушне правило 2). Клас містить лише
    /// правило оцінки — чисту функцію без залежностей від WPF, щоб її можна
    /// було буквально перенести в Kotlin.
    /// </summary>
    public sealed class NormsCatalog
    {
        /// <summary>
        /// Порожній довідник: норми не прочитались. Не помилка й не виняток —
        /// застосунок працює далі, просто без оцінки за нормою.
        /// </summary>
        public static readonly NormsCatalog Empty =
            new NormsCatalog(0, new GradeNorms[0], NormLabels.Fallback, null, null);

        public NormsCatalog(int version, IList<GradeNorms> grades, NormLabels labels, string source, string note)
        {
            Version = version;
            Grades = new List<GradeNorms>(grades ?? new GradeNorms[0]);
            Labels = labels ?? NormLabels.Fallback;
            Source = source ?? string.Empty;
            Note = note ?? string.Empty;
        }

        /// <summary>Версія формату довідника.</summary>
        public int Version { get; private set; }

        public IList<GradeNorms> Grades { get; private set; }

        public NormLabels Labels { get; private set; }

        /// <summary>
        /// Звідки взяті норми — показується вчителю у вікні «Норми читання».
        /// Текст із довідника, а не з коду: правлять норми — правлять і джерело.
        /// </summary>
        public string Source { get; private set; }

        /// <summary>
        /// Застереження до норм (напр. що в НУШ вони рекомендаційні).
        /// Учитель має бачити його поруч із числами, а не дізнаватися окремо.
        /// </summary>
        public string Note { get; private set; }

        /// <summary>Чи є в довіднику хоч один клас.</summary>
        public bool IsEmpty
        {
            get { return Grades.Count == 0; }
        }

        /// <summary>Чи є такий клас у довіднику.</summary>
        public bool HasGrade(int grade)
        {
            foreach (var item in Grades)
            {
                if (item.Grade == grade)
                {
                    return true;
                }
            }

            return false;
        }

        /// <summary>
        /// Норма для класу й семестру або <c>null</c>, якщо такої немає.
        /// </summary>
        public ReadingNorm Find(int grade, int semester)
        {
            foreach (var item in Grades)
            {
                if (item.Grade != grade)
                {
                    continue;
                }

                foreach (var norm in item.Semesters)
                {
                    if (norm.Semester == semester)
                    {
                        return norm;
                    }
                }
            }

            return null;
        }

        /// <summary>
        /// Оцінити швидкість відносно норми класу й семестру.
        /// Правило — специфікація, 4.9: межі входять у норму.
        /// </summary>
        public NormEvaluation Evaluate(int wordsPerMinute, int grade, int semester)
        {
            return Evaluate(wordsPerMinute, Find(grade, semester));
        }

        /// <summary>
        /// Те саме правило, але для вже знайденої норми.
        ///
        /// Порівняння цілих, без дробів: WPM уже округлений «від нуля» на точному
        /// дробі (4.7), і другого округлення тут бути не має — інакше 22.5, яке
        /// стало 23, могло б повернутися до 22 і перескочити межу норми.
        /// </summary>
        public NormEvaluation Evaluate(int wordsPerMinute, ReadingNorm norm)
        {
            if (norm == null)
            {
                return NormEvaluation.Unknown;
            }

            if (wordsPerMinute < norm.Min)
            {
                return NormEvaluation.Below;
            }

            return wordsPerMinute > norm.Max
                ? NormEvaluation.Above
                : NormEvaluation.Within;
        }

        /// <summary>Підпис оцінки з довідника. Для <c>Unknown</c> — порожній рядок.</summary>
        public string Describe(NormEvaluation evaluation)
        {
            switch (evaluation)
            {
                case NormEvaluation.Below:
                    return Labels.Below;
                case NormEvaluation.Within:
                    return Labels.Within;
                case NormEvaluation.Above:
                    return Labels.Above;
                default:
                    return string.Empty;
            }
        }
    }
}

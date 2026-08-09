using System.Windows.Input;
using ReadFlow.Core;

namespace ReadFlow.ViewModels
{
    /// <summary>
    /// Пункт списку «Вставити зразок»: назва, рівень і приблизний час читання
    /// для обраного класу.
    ///
    /// Команда лежить у самому пункті, а не в спільній властивості ViewModel.
    /// Причина технічна: пункти меню живуть у <c>Popup</c>, тобто поза візуальним
    /// деревом вікна, і привʼязка виду <c>RelativeSource AncestorType=Menu</c>
    /// до них не дотягується.
    /// </summary>
    public sealed class SampleOption
    {
        public SampleOption(TextSample sample, string levelLabel, string levelHint, string timeText, ICommand insertCommand)
        {
            Sample = sample;
            LevelLabel = levelLabel ?? string.Empty;
            LevelHint = levelHint ?? string.Empty;
            TimeText = timeText ?? string.Empty;
            InsertCommand = insertCommand;
        }

        public TextSample Sample { get; private set; }

        public string Title
        {
            get { return Sample == null ? string.Empty : Sample.Title; }
        }

        public string LevelLabel { get; private set; }

        public string LevelHint { get; private set; }

        /// <summary>
        /// «≈ 2 хв» для обраного класу й семестру, або кількість слів, якщо
        /// класу не обрано. Порожнім не буває: рядок під назвою має щось казати.
        /// </summary>
        public string TimeText { get; private set; }

        public ICommand InsertCommand { get; private set; }
    }
}

using System;
using System.Collections.Generic;
using System.Linq;
using ReadFlow.Core;
using ReadFlow.Models;

namespace ReadFlow.ViewModels
{
    /// <summary>
    /// ViewModel головного вікна. Поки що каркас: наповнюється в наступних задачах
    /// (текст і статистика — Задачі 2–3, решта налаштувань — Задача 5, таймер — Задача 6).
    /// </summary>
    public class MainViewModel : ViewModelBase
    {
        private ThemeDescriptor _selectedTheme;

        public MainViewModel()
        {
            Themes = ThemeManager.AvailableThemes;

            // Поле, а не властивість: при старті тема вже застосована в App.OnStartup,
            // повторно застосовувати її не треба.
            _selectedTheme = Themes.FirstOrDefault(
                                 t => string.Equals(t.Name, ThemeManager.CurrentThemeName, StringComparison.OrdinalIgnoreCase))
                             ?? Themes.FirstOrDefault();
        }

        /// <summary>Список доступних тем. Формується з файлів у <c>Themes/</c>.</summary>
        public IReadOnlyList<ThemeDescriptor> Themes { get; private set; }

        /// <summary>Обрана тема. Зміна застосовується миттєво й запамʼятовується.</summary>
        public ThemeDescriptor SelectedTheme
        {
            get { return _selectedTheme; }
            set
            {
                if (Set(ref _selectedTheme, value) && value != null)
                {
                    ThemeManager.Apply(value.Name);
                }
            }
        }
    }
}

using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace ReadFlow.ViewModels
{
    /// <summary>
    /// Базовий клас усіх ViewModel: реалізує <see cref="INotifyPropertyChanged"/>
    /// та дає зручний <see cref="Set{T}"/> для властивостей із полем-бекінгом.
    /// </summary>
    public abstract class ViewModelBase : INotifyPropertyChanged
    {
        public event PropertyChangedEventHandler PropertyChanged;

        /// <summary>
        /// Повідомити View про зміну властивості. Імʼя підставляється компілятором.
        /// </summary>
        protected void OnPropertyChanged([CallerMemberName] string propertyName = null)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }

        /// <summary>
        /// Присвоїти значення полю й повідомити про зміну, якщо значення справді нове.
        /// </summary>
        /// <returns><c>true</c>, якщо значення змінилося.</returns>
        protected bool Set<T>(ref T field, T value, [CallerMemberName] string propertyName = null)
        {
            if (EqualityComparer<T>.Default.Equals(field, value))
            {
                return false;
            }

            field = value;
            OnPropertyChanged(propertyName);
            return true;
        }
    }
}

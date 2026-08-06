using System;
using System.Windows.Input;

namespace ReadFlow.ViewModels
{
    /// <summary>
    /// Команда без параметра, побудована на делегатах.
    /// Перепитування <see cref="CanExecute"/> — через <see cref="CommandManager.RequerySuggested"/>,
    /// тому окремо викликати RaiseCanExecuteChanged для типових сценаріїв не потрібно.
    /// </summary>
    public class RelayCommand : ICommand
    {
        private readonly Action _execute;
        private readonly Func<bool> _canExecute;

        public RelayCommand(Action execute, Func<bool> canExecute = null)
        {
            if (execute == null)
            {
                throw new ArgumentNullException(nameof(execute));
            }

            _execute = execute;
            _canExecute = canExecute;
        }

        public event EventHandler CanExecuteChanged
        {
            add { CommandManager.RequerySuggested += value; }
            remove { CommandManager.RequerySuggested -= value; }
        }

        public bool CanExecute(object parameter)
        {
            return _canExecute == null || _canExecute();
        }

        public void Execute(object parameter)
        {
            _execute();
        }

        /// <summary>Примусово попросити WPF перепитати стан усіх команд.</summary>
        public static void RaiseCanExecuteChanged()
        {
            CommandManager.InvalidateRequerySuggested();
        }
    }

    /// <summary>
    /// Команда з типізованим параметром.
    /// </summary>
    public class RelayCommand<T> : ICommand
    {
        private readonly Action<T> _execute;
        private readonly Func<T, bool> _canExecute;

        public RelayCommand(Action<T> execute, Func<T, bool> canExecute = null)
        {
            if (execute == null)
            {
                throw new ArgumentNullException(nameof(execute));
            }

            _execute = execute;
            _canExecute = canExecute;
        }

        public event EventHandler CanExecuteChanged
        {
            add { CommandManager.RequerySuggested += value; }
            remove { CommandManager.RequerySuggested -= value; }
        }

        public bool CanExecute(object parameter)
        {
            return _canExecute == null || _canExecute(Cast(parameter));
        }

        public void Execute(object parameter)
        {
            _execute(Cast(parameter));
        }

        private static T Cast(object parameter)
        {
            return parameter == null ? default(T) : (T)parameter;
        }
    }
}

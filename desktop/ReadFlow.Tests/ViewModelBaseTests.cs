using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.ViewModels;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Перевірка каркаса MVVM. Змістовні тести підрахунку зʼявляться в Задачі 2.
    /// </summary>
    [TestClass]
    public class ViewModelBaseTests
    {
        private class Probe : ViewModelBase
        {
            private string _value;

            public string Value
            {
                get { return _value; }
                set { Set(ref _value, value); }
            }
        }

        [TestMethod]
        public void Set_NewValue_RaisesPropertyChanged()
        {
            var probe = new Probe();
            string changed = null;
            probe.PropertyChanged += (s, e) => changed = e.PropertyName;

            probe.Value = "текст";

            Assert.AreEqual("текст", probe.Value);
            Assert.AreEqual("Value", changed);
        }

        [TestMethod]
        public void Set_SameValue_DoesNotRaisePropertyChanged()
        {
            var probe = new Probe { Value = "текст" };
            var raised = 0;
            probe.PropertyChanged += (s, e) => raised++;

            probe.Value = "текст";

            Assert.AreEqual(0, raised);
        }

        [TestMethod]
        public void RelayCommand_CanExecute_DefaultsToTrue()
        {
            var executed = false;
            var command = new RelayCommand(() => executed = true);

            Assert.IsTrue(command.CanExecute(null));
            command.Execute(null);
            Assert.IsTrue(executed);
        }

        [TestMethod]
        public void RelayCommand_RespectsCanExecutePredicate()
        {
            var allowed = false;
            var command = new RelayCommand(() => { }, () => allowed);

            Assert.IsFalse(command.CanExecute(null));
            allowed = true;
            Assert.IsTrue(command.CanExecute(null));
        }
    }
}

using System.Windows;
using ReadFlow.Core;

namespace ReadFlow
{
    /// <summary>
    /// Точка входу застосунку ReadFlow.
    /// </summary>
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            // Тему застосовуємо до створення головного вікна, щоб не було спалаху
            // типових кольорів на старті.
            ThemeManager.ApplySaved();
            base.OnStartup(e);
        }
    }
}

using System;
using System.Diagnostics;
using System.IO.Packaging;
using System.Windows;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Готує тестовий хост до роботи з ресурсами WPF. Виконується один раз
    /// до всіх тестів — і це принципово: <see cref="ThemeManager.AvailableThemes"/>
    /// кешує результат назавжди, тож якщо перший тест торкнеться його в непідготовленому
    /// оточенні, у кеші лишиться порожній список і решта тестів упаде за компанію.
    /// </summary>
    [TestClass]
    public static class TestEnvironment
    {
        [AssemblyInitialize]
        public static void Initialize(TestContext context)
        {
            RegisterPackScheme();
            PointResourceAssemblyAtReadFlow();
        }

        /// <summary>
        /// У застосунку схему <c>pack://</c> реєструє WPF під час старту.
        /// У тестовому хості цього не робить ніхто.
        /// </summary>
        private static void RegisterPackScheme()
        {
            try
            {
                // Саме звернення до члена запускає статичний конструктор PackUriHelper,
                // який і виконує реєстрацію.
                var scheme = PackUriHelper.UriSchemePack;
                Debug.WriteLine("Тести: схема " + scheme + " зареєстрована: " + UriParser.IsKnownScheme(scheme));
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Тести: не вдалося зареєструвати схему pack — " + ex.Message);
            }
        }

        /// <summary>
        /// Адреси <c>pack://application:,,,/</c> WPF розвʼязує відносно
        /// <see cref="Application.ResourceAssembly"/>. Її гетер, коли поле порожнє,
        /// підставляє <c>Assembly.GetEntryAssembly()</c> — а в тестовому хості це
        /// <c>testhost.exe</c>, у якому ресурсів ReadFlow немає.
        ///
        /// Тому вказуємо збірку явно, і зробити це треба до першого читання
        /// властивості: після нього сетер уже кидає виняток.
        /// </summary>
        private static void PointResourceAssemblyAtReadFlow()
        {
            try
            {
                Application.ResourceAssembly = typeof(ThemeManager).Assembly;
            }
            catch (Exception ex)
            {
                Debug.WriteLine("Тести: не вдалося задати ResourceAssembly — " + ex.Message);
            }
        }
    }
}

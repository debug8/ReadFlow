using System;
using System.Diagnostics;
using System.Globalization;
using System.Linq;
using System.Reflection;
using System.Windows;

namespace ReadFlow.Views
{
    /// <summary>
    /// Вікно «Про програму»: назва, версія, короткий опис і авторство.
    ///
    /// Версія й авторство читаються з атрибутів збірки, а не вписані в розмітку.
    /// Інакше вони існували б у двох місцях і вже з наступним релізом розійшлися б:
    /// про <c>AssemblyInfo.cs</c> при підйомі версії згадують, про текст у вікні — ні.
    /// </summary>
    public partial class AboutWindow : Window
    {
        private const string VersionFormatKey = "Str_AboutVersionFormat";
        private const string DefaultVersionFormat = "Версія {0}";

        public AboutWindow()
        {
            InitializeComponent();

            var assembly = typeof(AboutWindow).Assembly;

            VersionText.Text = string.Format(
                CultureInfo.CurrentCulture,
                TryFindResource(VersionFormatKey) as string ?? DefaultVersionFormat,
                ReadVersion(assembly));

            AuthorText.Text = ReadCopyright(assembly);
        }

        /// <summary>
        /// Версія для показу. Спершу — інформаційна («0.9»), бо саме її бачить
        /// людина; технічна «0.9.0.0» лишається запасним варіантом.
        /// </summary>
        private static string ReadVersion(Assembly assembly)
        {
            var informational = Attribute<AssemblyInformationalVersionAttribute>(assembly);
            if (informational != null && !string.IsNullOrWhiteSpace(informational.InformationalVersion))
            {
                return informational.InformationalVersion;
            }

            var version = assembly.GetName().Version;
            return version == null ? string.Empty : version.ToString();
        }

        private static string ReadCopyright(Assembly assembly)
        {
            var copyright = Attribute<AssemblyCopyrightAttribute>(assembly);
            if (copyright != null && !string.IsNullOrWhiteSpace(copyright.Copyright))
            {
                return copyright.Copyright;
            }

            var company = Attribute<AssemblyCompanyAttribute>(assembly);
            return company == null ? string.Empty : company.Company;
        }

        private static T Attribute<T>(Assembly assembly) where T : System.Attribute
        {
            try
            {
                return assembly.GetCustomAttributes(typeof(T), false).OfType<T>().FirstOrDefault();
            }
            catch (Exception ex)
            {
                // Порожній рядок у вікні «Про програму» — не привід не показати вікно.
                Debug.WriteLine("ReadFlow: не вдалося прочитати атрибут збірки — " + ex.Message);
                return null;
            }
        }

        private void OnCloseClick(object sender, RoutedEventArgs e)
        {
            Close();
        }
    }
}

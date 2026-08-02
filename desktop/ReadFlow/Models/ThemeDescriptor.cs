using System;

namespace ReadFlow.Models
{
    /// <summary>
    /// Опис однієї теми оформлення: технічна назва (імʼя файлу без розширення),
    /// назва для користувача та адреса словника ресурсів.
    /// </summary>
    public class ThemeDescriptor
    {
        public ThemeDescriptor(string name, string displayName, Uri source)
        {
            Name = name;
            DisplayName = displayName;
            Source = source;
        }

        /// <summary>Технічна назва теми, напр. <c>light</c>. Зберігається в налаштуваннях.</summary>
        public string Name { get; private set; }

        /// <summary>Назва у списку тем, напр. «Світла». Береться з ключа <c>ThemeDisplayName</c>.</summary>
        public string DisplayName { get; private set; }

        /// <summary>Pack-адреса словника теми.</summary>
        public Uri Source { get; private set; }

        public override string ToString()
        {
            return DisplayName;
        }
    }
}

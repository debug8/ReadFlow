using System;
using System.Diagnostics;
using System.Media;
using System.Windows;

namespace ReadFlow.Core
{
    /// <summary>
    /// Сигнал про завершення заданої тривалості.
    ///
    /// Власний файл, а не <see cref="SystemSounds"/>: системні звуки вимикаються
    /// в налаштуваннях Windows і в тихому режимі можуть не пролунати взагалі,
    /// а вчитель має почути сигнал через клас. <see cref="SystemSounds"/>
    /// лишається запасним варіантом, якщо ресурс не завантажився.
    /// </summary>
    public static class TimerSound
    {
        private const string SoundUri = "pack://application:,,,/ReadFlow;component/Resources/timer-end.wav";

        private static SoundPlayer _player;
        private static bool _loadFailed;

        public static void Play()
        {
            try
            {
                var player = GetPlayer();

                if (player != null)
                {
                    // Play, а не PlaySync: інтерфейс не має підвисати на час звуку.
                    player.Play();
                    return;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: не вдалося відтворити сигнал — " + ex.Message);
            }

            PlayFallback();
        }

        private static SoundPlayer GetPlayer()
        {
            if (_player != null || _loadFailed)
            {
                return _player;
            }

            try
            {
                var info = Application.GetResourceStream(new Uri(SoundUri, UriKind.Absolute));
                if (info == null)
                {
                    _loadFailed = true;
                    return null;
                }

                var player = new SoundPlayer(info.Stream);
                player.Load();
                _player = player;
            }
            catch (Exception ex)
            {
                // Один раз спробували — далі не витрачаємо час на кожен замір.
                _loadFailed = true;
                Debug.WriteLine("ReadFlow: не вдалося завантажити звук таймера — " + ex.Message);
            }

            return _player;
        }

        private static void PlayFallback()
        {
            try
            {
                SystemSounds.Exclamation.Play();
            }
            catch (Exception ex)
            {
                Debug.WriteLine("ReadFlow: системний звук теж недоступний — " + ex.Message);
            }
        }
    }
}

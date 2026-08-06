using System;
using System.Diagnostics;
using System.Windows.Threading;

namespace ReadFlow.Core
{
    /// <summary>
    /// Таймер заміру читання.
    ///
    /// Час вимірює <see cref="Stopwatch"/>, а <see cref="DispatcherTimer"/> лише
    /// оновлює екран. Це принципово: тіки таймера інтерфейсу затримуються під
    /// навантаженням, і якби ми накопичували час по тіках, за хвилину набігла б
    /// похибка в кілька секунд — а це прилад вимірювання.
    ///
    /// Задана тривалість — позначка, а не межа: коли вона минає, спрацьовує
    /// <see cref="DurationReached"/>, але відлік триває, доки не викличуть
    /// <see cref="Stop"/> (специфікація, 4.8).
    /// </summary>
    public class ReadingTimer
    {
        /// <summary>
        /// Крок оновлення екрана. 100 мс — секунди змінюються без помітного
        /// запізнення, а навантаження лишається непомітним.
        /// </summary>
        private const int RefreshMilliseconds = 100;

        private readonly Stopwatch _stopwatch = new Stopwatch();
        private readonly DispatcherTimer _refreshTimer;
        private bool _durationSignalled;

        public ReadingTimer()
        {
            _refreshTimer = new DispatcherTimer(DispatcherPriority.Render)
            {
                Interval = TimeSpan.FromMilliseconds(RefreshMilliseconds)
            };
            _refreshTimer.Tick += OnRefreshTick;
        }

        /// <summary>Минув крок оновлення — час на екрані треба перемалювати.</summary>
        public event EventHandler Tick;

        /// <summary>Минула задана тривалість. Спрацьовує один раз на замір.</summary>
        public event EventHandler DurationReached;

        /// <summary>Задана тривалість заміру.</summary>
        public TimeSpan Duration { get; set; }

        public TimeSpan Elapsed
        {
            get { return _stopwatch.Elapsed; }
        }

        public bool IsRunning
        {
            get { return _stopwatch.IsRunning; }
        }

        /// <summary>Запустити або продовжити відлік.</summary>
        public void Start()
        {
            if (_stopwatch.IsRunning)
            {
                return;
            }

            _stopwatch.Start();
            _refreshTimer.Start();
            RaiseTick();
        }

        /// <summary>Зупинити відлік, зберігши накопичений час.</summary>
        public void Stop()
        {
            if (!_stopwatch.IsRunning)
            {
                return;
            }

            _stopwatch.Stop();
            _refreshTimer.Stop();
            RaiseTick();
        }

        /// <summary>Зупинити відлік і обнулити час.</summary>
        public void Reset()
        {
            _stopwatch.Reset();
            _refreshTimer.Stop();
            _durationSignalled = false;
            RaiseTick();
        }

        private void OnRefreshTick(object sender, EventArgs e)
        {
            RaiseTick();

            if (_durationSignalled || Duration <= TimeSpan.Zero || _stopwatch.Elapsed < Duration)
            {
                return;
            }

            // Один сигнал на замір: далі відлік просто триває.
            _durationSignalled = true;

            var handler = DurationReached;
            if (handler != null)
            {
                handler(this, EventArgs.Empty);
            }
        }

        private void RaiseTick()
        {
            var handler = Tick;
            if (handler != null)
            {
                handler(this, EventArgs.Empty);
            }
        }
    }
}

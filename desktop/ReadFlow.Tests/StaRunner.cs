using System;
using System.Threading;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace ReadFlow.Tests
{
    /// <summary>
    /// MSTest виконує тести в MTA-потоці, а WPF-обʼєкти (пензлі, <c>DispatcherTimer</c>)
    /// розраховують на STA. Цей хелпер виконує дію в STA-потоці й переносить
    /// виняток назад у тест, щоб падіння лишалося читабельним.
    /// </summary>
    internal static class StaRunner
    {
        public static void Run(Action action)
        {
            Exception failure = null;

            var thread = new Thread(() =>
            {
                try
                {
                    action();
                }
                catch (Exception ex)
                {
                    failure = ex;
                }
            });

            thread.SetApartmentState(ApartmentState.STA);
            thread.IsBackground = true;
            thread.Start();
            thread.Join();

            if (failure != null)
            {
                throw new AssertFailedException(failure.Message, failure);
            }
        }
    }
}

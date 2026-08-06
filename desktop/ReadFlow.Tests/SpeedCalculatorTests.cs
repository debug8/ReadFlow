using Microsoft.VisualStudio.TestTools.UnitTesting;
using ReadFlow.Core;

namespace ReadFlow.Tests
{
    /// <summary>
    /// Формули швидкості читання (специфікація, 4.7).
    /// Ці ж числа має показати Android-версія.
    /// </summary>
    [TestClass]
    public class SpeedCalculatorTests
    {
        [TestMethod]
        public void WordsPerMinute_BasicCases()
        {
            Assert.AreEqual(60, SpeedCalculator.WordsPerMinute(60, 60m));
            Assert.AreEqual(120, SpeedCalculator.WordsPerMinute(60, 30m));
            Assert.AreEqual(30, SpeedCalculator.WordsPerMinute(60, 120m));
            Assert.AreEqual(84, SpeedCalculator.WordsPerMinute(84, 60m));
        }

        [TestMethod]
        public void CharsPerMinute_BasicCases()
        {
            Assert.AreEqual(420, SpeedCalculator.CharsPerMinute(420, 60m));
            Assert.AreEqual(840, SpeedCalculator.CharsPerMinute(420, 30m));
        }

        [TestMethod]
        public void Rounding_IsHalfAwayFromZeroOnExactFraction()
        {
            // 45 слів за 120 с — це рівно 22.5, справжня середина.
            // «Від нуля» дає 23; банківське округлення дало б 22,
            // а обчислення через Double залежало б від платформи.
            Assert.AreEqual(23, SpeedCalculator.WordsPerMinute(45, 120m));
            Assert.AreEqual(24, SpeedCalculator.WordsPerMinute(47, 120m));
            Assert.AreEqual(11, SpeedCalculator.WordsPerMinute(21, 120m));
        }

        [TestMethod]
        public void Rounding_NonMidpointValues()
        {
            // 100 слів за 90 с = 66.66… -> 67
            Assert.AreEqual(67, SpeedCalculator.WordsPerMinute(100, 90m));

            // 100 слів за 91 с = 65.93… -> 66
            Assert.AreEqual(66, SpeedCalculator.WordsPerMinute(100, 91m));
        }

        [TestMethod]
        public void FractionalSeconds_AreSupported()
        {
            // Режим B дає фактичний час, а він майже ніколи не цілий.
            // 84 слова за 47.5 с = 106.10… -> 106
            Assert.AreEqual(106, SpeedCalculator.WordsPerMinute(84, 47.5m));
        }

        [TestMethod]
        public void ZeroOrNegativeTime_ReturnsZeroInsteadOfThrowing()
        {
            Assert.AreEqual(0, SpeedCalculator.WordsPerMinute(100, 0m));
            Assert.AreEqual(0, SpeedCalculator.WordsPerMinute(100, -5m));
            Assert.AreEqual(0, SpeedCalculator.CharsPerMinute(100, 0m));
        }

        [TestMethod]
        public void ZeroOrNegativeAmount_ReturnsZero()
        {
            Assert.AreEqual(0, SpeedCalculator.WordsPerMinute(0, 60m));
            Assert.AreEqual(0, SpeedCalculator.CharsPerMinute(0, 60m));
            Assert.AreEqual(0, SpeedCalculator.WordsPerMinute(-3, 60m));
        }

        [TestMethod]
        public void VeryShortTime_DoesNotOverflow()
        {
            // Учитель випадково натиснув Старт і одразу Стоп.
            Assert.AreEqual(6000, SpeedCalculator.WordsPerMinute(1, 0.01m));
        }
    }
}

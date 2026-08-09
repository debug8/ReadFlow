package net.readflow.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Сигнал про кінець заміру: звук **і** вібрація.
 *
 * Обидва разом, а не на вибір: у класі шумно, і самого звуку вчитель може не
 * почути, а телефон у нього в руці (`SPEC_ANDROID.md`, 2.1).
 *
 * Живе в шарі інтерфейсу, а не у ViewModel: та лишається без залежностей від
 * Android і перевіряється звичайними JVM-тестами.
 */
object MeasurementAlerts {

    private const val VIBRATION_MS = 400L
    private const val BEEP_MS = 350
    private const val BEEP_VOLUME = 90

    /** Просигналити, що обрана тривалість минула. Ніколи не кидає виняток. */
    fun signalDurationReached(context: Context) {
        vibrate(context)
        beep()
    }

    private fun vibrate(context: Context) {
        val vibrator = vibratorOf(context) ?: return

        if (!vibrator.hasVibrator()) {
            return
        }

        try {
            vibrator.vibrate(
                VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: RuntimeException) {
            // Вібромотор зайнятий або заборонений політикою пристрою — не привід
            // валити замір. Звук однаково лунає.
        }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun beep() {
        // ToneGenerator, а не власний файл у ресурсах: короткий сигнал не вартий
        // мегабайта в APK, а системний тон однаково гучний на всіх апаратах.
        var generator: ToneGenerator? = null

        try {
            generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
        } catch (e: RuntimeException) {
            // Аудіоресурс зайнятий іншим застосунком — вібрація вже спрацювала.
        } finally {
            // Звільняти треба після того, як тон дограв, інакше він обірветься.
            generator?.let { tone ->
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({ tone.release() }, (BEEP_MS + 100).toLong())
            }
        }
    }
}

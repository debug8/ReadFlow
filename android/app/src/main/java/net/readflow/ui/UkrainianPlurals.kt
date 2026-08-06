package net.readflow.ui

import androidx.annotation.StringRes
import net.readflow.R

/**
 * Вибір української форми множини.
 *
 * Робиться в коді, а не через ресурс `<plurals>`, свідомо: Android бере правила
 * множини з **локалі пристрою**, а не з мови ресурсів. Додаток україномовний
 * завжди, тому на телефоні з англійською локаллю системний вибір дав би
 * англійське правило («other») і напис «15 слова» замість «15 слів».
 *
 * Правило CLDR для української:
 * - `one`  — 1, 21, 31, 101… (закінчується на 1, крім 11)
 * - `few`  — 2–4, 22–24… (закінчується на 2–4, крім 12–14)
 * - `many` — решта, зокрема 0, 5–20, 11–14
 */
object UkrainianPlurals {

    @StringRes
    fun words(count: Int): Int {
        val abs = if (count < 0) -count else count
        val lastDigit = abs % 10
        val lastTwoDigits = abs % 100

        return when {
            lastDigit == 1 && lastTwoDigits != 11 -> R.string.sample_words_one
            lastDigit in 2..4 && lastTwoDigits !in 12..14 -> R.string.sample_words_few
            else -> R.string.sample_words_many
        }
    }
}

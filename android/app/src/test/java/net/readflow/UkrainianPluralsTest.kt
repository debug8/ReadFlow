package net.readflow

import net.readflow.R
import net.readflow.ui.UkrainianPlurals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Правило множини живе в коді, тому воно й перевіряється як код —
 * без Android і без залежності від локалі пристрою.
 */
class UkrainianPluralsTest {

    /** 1, 21, 101 — «слово». */
    @Test
    fun `singular form`() {
        for (n in listOf(1, 21, 31, 101, 1001)) {
            assertEquals("n=$n", R.string.sample_words_one, UkrainianPlurals.words(n))
        }
    }

    /** 2–4, 22–24 — «слова». */
    @Test
    fun `few form`() {
        for (n in listOf(2, 3, 4, 22, 23, 24, 102, 1003)) {
            assertEquals("n=$n", R.string.sample_words_few, UkrainianPlurals.words(n))
        }
    }

    /** 0, 5–20, 11–14, 25 — «слів». Саме тут англійське правило й помилялося б. */
    @Test
    fun `many form`() {
        for (n in listOf(0, 5, 9, 11, 12, 13, 14, 15, 19, 20, 25, 100, 111, 112)) {
            assertEquals("n=$n", R.string.sample_words_many, UkrainianPlurals.words(n))
        }
    }
}

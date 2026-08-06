package net.readflow

import kotlinx.coroutines.test.runTest
import net.readflow.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * ViewModel не залежить від Android — тому це звичайний JVM-тест без Robolectric.
 *
 * Назви тестів латиницею навмисно: Kotlin робить із них імена class-файлів,
 * а кирилиця в назві файлу ламає збірку на системах, де кодування шляхів не UTF-8.
 */
class MainViewModelTest {

    /** Початковий стан — порожній текст. */
    @Test
    fun `initial state is empty`() = runTest {
        val viewModel = MainViewModel()

        assertEquals("", viewModel.uiState.value.text)
    }

    /** Зміна тексту потрапляє в стан. */
    @Test
    fun `text change reaches the state`() = runTest {
        val viewModel = MainViewModel()

        viewModel.onTextChange("комп'ютер")

        assertEquals("комп'ютер", viewModel.uiState.value.text)
    }

    /** Стан незмінний: кожна зміна дає новий об'єкт, а не править старий. */
    @Test
    fun `state is immutable - every change creates a new object`() = runTest {
        val viewModel = MainViewModel()
        val before = viewModel.uiState.value

        viewModel.onTextChange("текст")

        val after = viewModel.uiState.value
        assertNotSame(before, after)
        assertEquals("", before.text)
        assertEquals("текст", after.text)
    }
}

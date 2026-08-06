package net.readflow.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Єдина ViewModel застосунку.
 *
 * Тримає весь стан екрана в одному [StateFlow] і не залежить від Android UI —
 * тому покривається звичайними JVM-тестами.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())

    /** Стан для екрана. Змінюється лише через методи нижче. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Користувач змінив текст у полі вводу. */
    fun onTextChange(text: String) {
        _uiState.update { current -> current.copy(text = text) }
    }
}

package net.readflow.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.readflow.core.CountingOptions
import net.readflow.core.TextStatsCalculator
import net.readflow.data.AssetSampleRepository
import net.readflow.data.SampleRepository
import net.readflow.model.TextSample
import net.readflow.model.TextStats
import net.readflow.model.WordToken

/**
 * Єдина ViewModel застосунку.
 *
 * Тримає весь стан екрана в одному [StateFlow]. Залежності передаються ззовні,
 * щоб тести обходилися без Android: [samples] підміняється фейком, а
 * [computeDispatcher] — тестовим диспетчером із віртуальним часом.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val samples: SampleRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())

    /** Стан для екрана. Змінюється лише через методи нижче. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Окремий потік тексту: сам текст оновлюється миттєво, а статистика — з дебаунсом. */
    private val textInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            textInput
                .debounce(STATS_DEBOUNCE_MS)
                .mapLatest { text ->
                    // Підрахунок на 3000 слів не має чіплятися до головного потоку.
                    withContext(computeDispatcher) {
                        // Розбір на слова робиться один раз: він однаково потрібен
                        // і статистиці, і режиму читання.
                        val words = TextStatsCalculator.getWords(text)

                        Counted(
                            text = text,
                            words = words,
                            stats = TextStatsCalculator.calculate(
                                text,
                                words,
                                CountingOptions.Default
                            )
                        )
                    }
                }
                .collect { counted ->
                    _uiState.update {
                        it.copy(
                            countedText = counted.text,
                            words = counted.words,
                            stats = counted.stats
                        )
                    }
                }
        }

        viewModelScope.launch {
            val loaded = samples.list()
            _uiState.update { it.copy(samples = loaded) }
        }
    }

    /** Користувач змінив текст у полі вводу або вставив його з буфера. */
    fun onTextChange(text: String) {
        _uiState.update { current ->
            current.copy(
                text = text,
                // Номер слова належав попередньому текстові — після правки він бреше.
                tappedWordNumber = null,
                // Порожній текст нічого читати: режим читання сам вимикається,
                // інакше екран лишився б із порожньою зоною й без поля вводу.
                isReadingMode = current.isReadingMode && text.isNotEmpty()
            )
        }

        textInput.value = text
    }

    /** Перемикач «Читання» над текстом. На порожньому тексті нічого не робить. */
    fun toggleReadingMode() {
        _uiState.update { current ->
            if (current.isEmpty) current else current.copy(isReadingMode = !current.isReadingMode)
        }
    }

    /**
     * Тап по слову в режимі читання. [number] — порядковий номер слова від 1.
     * Задача 6 зробить із нього межу читання (режим A) або позначку помилки (режим C).
     */
    fun onWordTap(number: Int) {
        _uiState.update { current -> current.copy(tappedWordNumber = number) }
    }

    /** Кнопка «Очистити». */
    fun clearText() = onTextChange("")

    /** Тап по рядку статистики розгортає й згортає його. */
    fun toggleStatsExpanded() {
        _uiState.update { current -> current.copy(isStatsExpanded = !current.isStatsExpanded) }
    }

    /** Кнопка «Обрати зразок». */
    fun showSampleSheet() {
        _uiState.update { current -> current.copy(isSampleSheetVisible = true) }
    }

    fun hideSampleSheet() {
        _uiState.update { current -> current.copy(isSampleSheetVisible = false) }
    }

    /** Учитель обрав зразок зі списку: вантажимо текст і закриваємо аркуш. */
    fun onSampleSelected(sample: TextSample) {
        viewModelScope.launch {
            val text = samples.load(sample)

            hideSampleSheet()

            if (text.isNotEmpty()) {
                onTextChange(text)
            }
        }
    }

    /** Результат одного проходу розбору: текст і все, що з нього порахували. */
    private data class Counted(
        val text: String,
        val words: List<WordToken>,
        val stats: TextStats
    )

    companion object {

        /** Дебаунс перерахунку статистики, мс (`SPEC_ANDROID.md`, розділ 5). */
        const val STATS_DEBOUNCE_MS = 300L

        /** Фабрика для екрана: репозиторій зразків потребує `Context`. */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext

            return viewModelFactory {
                initializer { MainViewModel(AssetSampleRepository(appContext)) }
            }
        }
    }
}

package tv.lumen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.Video
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    /** Ряды «Продолжить просмотр» и «Подписки — новое» добавятся в фазе 4 (SPEC 8). */
    data class Content(val trending: List<Video>) : HomeUiState
    data class Error(val error: AppError) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val youtube: YouTubeRepository,
) : ViewModel() {

    val focusMemory = FocusMemory()

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = youtube.trending()) {
                is AppResult.Success -> HomeUiState.Content(result.value)
                is AppResult.Failure -> HomeUiState.Error(result.error)
            }
        }
    }
}
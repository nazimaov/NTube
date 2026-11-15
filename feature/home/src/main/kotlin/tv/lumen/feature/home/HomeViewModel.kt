package tv.lumen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.Video
import tv.lumen.core.model.WatchEntry
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

sealed interface TrendingState {
    data object Loading : TrendingState
    data class Content(val videos: List<Video>) : TrendingState
    data class Error(val error: AppError) : TrendingState
}

/** Ряды главной (SPEC 8): «Продолжить просмотр», «Подписки — новое», «В тренде». */
data class HomeUiState(
    val continueWatching: List<WatchEntry> = emptyList(),
    val subscriptionsNew: List<Video> = emptyList(),
    val trending: TrendingState = TrendingState.Loading,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val youtube: YouTubeRepository,
    private val library: LibraryRepository,
    filter: ContentFilter,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val trending = MutableStateFlow<TrendingState>(TrendingState.Loading)

    // Фильтры 6.4 применяются ко всем рядам, кроме «Продолжить просмотр»
    val state: StateFlow<HomeUiState> = combine(library.continueWatching, library.feed, trending, filter.rules) { cont, feed, tr, rules ->
        HomeUiState(
            continueWatching = cont.filter { it.video.channelId == null || it.video.channelId !in rules.blockedChannels },
            subscriptionsNew = rules.videos(feed).take(ROW_SIZE),
            trending = if (tr is TrendingState.Content) TrendingState.Content(rules.videos(tr.videos)) else tr,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        loadTrending()
        // Лента подписок обновляется, если с прошлого раза прошло больше 30 минут (SPEC 7.1)
        viewModelScope.launch { library.refreshFeed(force = false) }
    }

    fun loadTrending() {
        trending.value = TrendingState.Loading
        viewModelScope.launch {
            trending.value = when (val result = youtube.trending()) {
                is AppResult.Success -> TrendingState.Content(result.value)
                is AppResult.Failure -> TrendingState.Error(result.error)
            }
        }
    }

    private companion object {
        const val ROW_SIZE = 20
    }
}
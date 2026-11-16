package tv.lumen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import tv.lumen.data.library.ProfileRepository
import tv.lumen.data.library.VideoActions
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

sealed interface TrendingState {
    data object Loading : TrendingState
    data class Content(val videos: List<Video>) : TrendingState
    data class Error(val error: AppError) : TrendingState
}

/** Ряды главной (SPEC 8): «Продолжить просмотр», «Рекомендации», «Подписки — новое», «В тренде». */
data class HomeUiState(
    val continueWatching: List<WatchEntry> = emptyList(),
    /** Свои рекомендации профиля (см. [Recommendations]); пусто, пока не загрузились. */
    val recommendations: List<Video> = emptyList(),
    val subscriptionsNew: List<Video> = emptyList(),
    val trending: TrendingState = TrendingState.Loading,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val youtube: YouTubeRepository,
    private val library: LibraryRepository,
    private val profiles: ProfileRepository,
    filter: ContentFilter,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val trending = MutableStateFlow<TrendingState>(TrendingState.Loading)

    // Источники рекомендаций: лайки и недавняя история текущего профиля
    private val seeds: Flow<List<String>> = combine(
        profiles.likedVideoIds(),
        library.history.map { list -> list.take(RECENT_SEEDS).map { it.video.id } },
        profiles.dislikedVideoIds,
    ) { liked, recent, disliked -> Recommendations.seeds(liked, recent, disliked) }.distinctUntilChanged()

    // Похожие видео к каждому источнику; новый профиль без истории — от свежих видео подписок
    private val related: Flow<Pair<List<String>, List<List<Video>>>> = seeds.mapLatest { ids ->
        val sources = ids.ifEmpty { library.feed.first().take(FALLBACK_SEEDS).map { it.id } }
        val limiter = Semaphore(PARALLEL_DETAILS)
        sources to coroutineScope {
            sources.map { id ->
                async { limiter.withPermit { (youtube.videoDetails(id) as? AppResult.Success)?.value?.related.orEmpty() } }
            }.awaitAll()
        }
    }

    private val recommendations: Flow<List<Video>> =
        combine(related, library.progress, profiles.dislikedVideoIds) { (sources, lists), progress, disliked ->
            Recommendations.mix(lists, exclude = progress.keys + disliked + sources)
        }.onStart { emit(emptyList()) }

    // Фильтры 6.4 применяются ко всем рядам, кроме «Продолжить просмотр»
    val state: StateFlow<HomeUiState> = combine(library.continueWatching, library.feed, recommendations, trending, filter.rules) { cont, feed, recs, tr, rules ->
        HomeUiState(
            continueWatching = cont.filter { it.video.channelId == null || it.video.channelId !in rules.blockedChannels },
            recommendations = rules.videos(recs),
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
        const val RECENT_SEEDS = 10
        const val FALLBACK_SEEDS = 3
        const val PARALLEL_DETAILS = 3
    }
}
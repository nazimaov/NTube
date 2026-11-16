package tv.lumen.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.Video
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

/**
 * «Фильмы» и «Сериалы»: чип подраздела сверху, видео подраздела сеткой. Подраздел — это
 * поисковый запрос; нарезки и короткие ролики отсеиваются по длительности.
 */
abstract class CatalogViewModel(
    private val youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val _selectedId = MutableStateFlow<String?>(null)

    /** Выбранный подраздел; null — ещё не выбирали (показывается первый). */
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val raw = MutableStateFlow(PagedState<Video>())
    private val minDuration = MutableStateFlow(0L)
    private var pager: Pager<Video>? = null
    private var collectJob: Job? = null
    private var shown: Pair<String, String>? = null
    private var current: Pair<Category, String>? = null

    /** Результаты после фильтров 6.4 и отсева коротких видео. */
    val results: StateFlow<PagedState<Video>> = combine(raw, minDuration, filter.rules) { state, min, rules ->
        // durationSec = -1 у эфиров и неизвестной длительности: такие в каталог не берём
        state.copy(items = rules.videos(state.items).filter { it.durationSec >= min })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagedState())

    /** Показать подраздел [category]; [query] — его поисковый запрос на языке интерфейса. */
    fun show(category: Category, query: String) {
        _selectedId.value = category.id
        minDuration.value = category.minDurationSec
        current = category to query
        if (shown == category.id to query) return
        shown = category.id to query
        focusMemory.reset()
        collectJob?.cancel()
        val p = Pager<Video>(viewModelScope, { it.id }) { token -> load(category, query, token) }
        pager = p
        collectJob = viewModelScope.launch { p.state.collect { raw.value = it } }
        p.refresh()
    }

    private suspend fun load(category: Category, query: String, token: PageToken?): AppResult<Page<Video>> {
        if (category.trailers) {
            return when (val r = youtube.trailers()) {
                is AppResult.Success -> AppResult.Success(Page(r.value, null))
                is AppResult.Failure -> r
            }
        }
        return when (val r = youtube.search(query, SearchFilter.VIDEOS, token)) {
            is AppResult.Success -> AppResult.Success(
                Page(r.value.items.filterIsInstance<SearchItem.VideoItem>().map { it.video }, r.value.next),
            )
            is AppResult.Failure -> r
        }
    }

    fun loadMore() = pager?.loadMore()

    fun retry() {
        val (category, query) = current ?: return
        shown = null
        show(category, query)
    }
}

@HiltViewModel
class MoviesViewModel @Inject constructor(youtube: YouTubeRepository, library: LibraryRepository, filter: ContentFilter) :
    CatalogViewModel(youtube, library, filter)

@HiltViewModel
class SeriesViewModel @Inject constructor(youtube: YouTubeRepository, library: LibraryRepository, filter: ContentFilter) :
    CatalogViewModel(youtube, library, filter)

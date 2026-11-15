package tv.lumen.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.getOrNull
import kotlinx.coroutines.flow.combine
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _submitted = MutableStateFlow<String?>(null)
    val submitted: StateFlow<String?> = _submitted.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _results = MutableStateFlow<PagedState<SearchItem>?>(null)

    /** Результаты после фильтров 6.4; подгрузка идёт по нефильтрованному списку. */
    val results: StateFlow<PagedState<SearchItem>?> = combine(_results, filter.rules) { r, rules ->
        r?.copy(items = r.items.filter(rules::allows))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var pager: Pager<SearchItem>? = null
    private var collectJob: Job? = null

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<String>> = _query
        .debounce(300)
        .mapLatest { q -> if (q.isBlank() || q == _submitted.value) emptyList() else youtube.searchSuggestions(q).getOrNull().orEmpty().take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun submit(text: String = _query.value) {
        val q = text.trim()
        if (q.isEmpty()) return
        _query.value = q
        _submitted.value = q
        startSearch()
    }

    fun setFilter(filter: SearchFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        if (_submitted.value != null) startSearch()
    }

    fun loadMore() = pager?.loadMore()

    fun retry() = startSearch()

    private fun startSearch() {
        val q = _submitted.value ?: return
        val f = _filter.value
        focusMemory.reset()
        collectJob?.cancel()
        val p = Pager(viewModelScope, ::itemKey) { token -> youtube.search(q, f, token) }
        pager = p
        collectJob = viewModelScope.launch { p.state.collect { _results.value = it } }
        p.refresh()
    }

    companion object {
        fun itemKey(item: SearchItem): String = when (item) {
            is SearchItem.VideoItem -> "v:" + item.video.id
            is SearchItem.ChannelItem -> "c:" + item.channel.id
            is SearchItem.PlaylistItem -> "p:" + item.playlist.id
        }
    }
}
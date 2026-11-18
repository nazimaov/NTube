package tv.lumen.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.Page
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
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.Video
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.getOrNull
import kotlinx.coroutines.flow.combine
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

/**
 * Общая логика поиска: строка, подсказки, фильтр, результаты с подгрузкой.
 * [SearchViewModel] — обычный поиск YouTube, [MusicViewModel] — поиск YouTube Music.
 */
abstract class BaseSearchViewModel(
    protected val youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
    initialFilter: SearchFilter,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _submitted = MutableStateFlow<String?>(null)
    val submitted: StateFlow<String?> = _submitted.asStateFlow()

    private val _filter = MutableStateFlow(initialFilter)
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
        val p = Pager(viewModelScope, ::searchItemKey) { token -> youtube.search(q, f, token) }
        pager = p
        collectJob = viewModelScope.launch { p.state.collect { _results.value = it } }
        p.refresh()
    }

}

fun searchItemKey(item: SearchItem): String = when (item) {
            is SearchItem.VideoItem -> "v:" + item.video.id
            is SearchItem.ChannelItem -> "c:" + item.channel.id
            is SearchItem.PlaylistItem -> "p:" + item.playlist.id
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
) : BaseSearchViewModel(youtube, library, filter, SearchFilter.ALL)

sealed interface MusicChartsState {
    data object Loading : MusicChartsState
    data class Content(val videos: List<Video>) : MusicChartsState
    data class Error(val error: AppError) : MusicChartsState
}

/**
 * «Музыка»: поиск YouTube Music и стартовый экран: язык музыки, плитки настроений и жанров, круглые
 * аватары исполнителей, под ними чарты региона (пока человек ничего не выбрал) или песни выбранного настроения.
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    youtube: YouTubeRepository,
    library: LibraryRepository,
    private val contentFilter: ContentFilter,
    settings: SettingsRepository,
) : BaseSearchViewModel(youtube, library, contentFilter, SearchFilter.MUSIC_SONGS) {

    private val _charts = MutableStateFlow<MusicChartsState>(MusicChartsState.Loading)

    /** Чарты после фильтров 6.4. */
    val charts: StateFlow<MusicChartsState> = combine(_charts, contentFilter.rules) { c, rules ->
        if (c is MusicChartsState.Content) MusicChartsState.Content(rules.videos(c.videos)) else c
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MusicChartsState.Loading)

    private val uiLanguage = MutableStateFlow("en")

    /** Язык музыки: по стране контента из настроек, пока страны нет — по языку интерфейса. */
    val lang: StateFlow<MusicLang> = combine(settings.flow { it.contentCountry }, uiLanguage) { country, ui ->
        MusicBrowse.autoLang(country, ui)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MusicLang.EN)

    private val _mood = MutableStateFlow<Mood?>(null)

    /** Выбранное настроение; null — «Для вас» (чарты региона). */
    val mood: StateFlow<Mood?> = _mood.asStateFlow()

    private val moodRaw = MutableStateFlow(PagedState<Video>())
    private var moodPager: Pager<Video>? = null
    private var moodJob: Job? = null

    /** Песни настроения: без эфиров, Shorts и слишком коротких или длинных видео. */
    val moodSongs: StateFlow<PagedState<Video>> = combine(moodRaw, contentFilter.rules, _mood) { state, rules, mood ->
        state.copy(items = rules.videos(state.items).filter { !it.isLive && !it.isShort && (mood?.anyLength == true || it.durationSec in 90..900) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagedState())

    private val _artists = MutableStateFlow<List<ChannelSummary>>(emptyList())

    /** Исполнители выбранного языка (круглые аватары). */
    val artists: StateFlow<List<ChannelSummary>> = _artists.asStateFlow()
    private val artistCache = HashMap<MusicLang, List<ChannelSummary>>()

    init {
        loadCharts()
        viewModelScope.launch { lang.collect { loadArtists(it) } }
        viewModelScope.launch { combine(_mood, lang) { m, l -> m to l }.collect { (m, l) -> showMood(m, l) } }
    }

    fun setUiLanguage(language: String) {
        uiLanguage.value = language
    }

    fun setMood(mood: Mood?) {
        _mood.value = if (_mood.value == mood) null else mood
    }

    private fun showMood(mood: Mood?, lang: MusicLang) {
        moodJob?.cancel()
        moodPager = null
        if (mood == null) return
        focusMemory.reset()
        val query = mood.terms.getValue(lang)
        val p = Pager<Video>(viewModelScope, { it.id }) { token ->
            when (val r = youtube.search(query, SearchFilter.VIDEOS, token)) {
                is AppResult.Success -> AppResult.Success(
                    Page(r.value.items.filterIsInstance<SearchItem.VideoItem>().map { it.video }, r.value.next),
                )
                is AppResult.Failure -> r
            }
        }
        moodPager = p
        moodJob = viewModelScope.launch { p.state.collect { moodRaw.value = it } }
        p.refresh()
    }

    fun loadMoreMood() = moodPager?.loadMore()

    fun retryMood() = showMood(_mood.value, lang.value)

    private suspend fun loadArtists(lang: MusicLang) {
        artistCache[lang]?.let { _artists.value = it; return }
        _artists.value = emptyList()
        val found = coroutineScope {
            MusicBrowse.artists.getValue(lang).map { name ->
                async {
                    val r = youtube.search(name, SearchFilter.CHANNELS, null) as? AppResult.Success
                    r?.value?.items?.filterIsInstance<SearchItem.ChannelItem>()?.map { it.channel }?.firstOrNull()
                }
            }.awaitAll().filterNotNull().distinctBy { it.id }
        }
        if (found.isNotEmpty()) artistCache[lang] = found
        _artists.value = found
    }

    fun loadCharts() {
        _charts.value = MusicChartsState.Loading
        viewModelScope.launch {
            _charts.value = when (val r = youtube.musicTrending()) {
                is AppResult.Success -> MusicChartsState.Content(r.value)
                is AppResult.Failure -> MusicChartsState.Error(r.error)
            }
        }
    }
}

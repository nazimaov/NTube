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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.datastore.DubLanguage
import tv.lumen.core.datastore.SettingsRepository
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

/** Фильтры экрана: жанр и год. Язык фильмов и сериалов — в настройках. */
data class Filters(val genre: Genre = Genre.ANY, val year: Int? = null)

/**
 * «Фильмы» и «Сериалы»: чип подборки сверху, кнопки «Жанр» и «Год», видео сеткой. Запрос
 * собирается из подборки, жанра, года и языка из настроек ([QueryBuilder]); нарезки и
 * короткие ролики отсеиваются по длительности.
 */
abstract class CatalogViewModel(
    private val kind: CatalogKind,
    private val youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
    settings: SettingsRepository,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    /** Язык фильмов и сериалов из настроек: оригинал или дубляж. */
    val dub: StateFlow<DubLanguage> = settings.flow { it.catalogDub }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.current.value.catalogDub)

    private val _selectedId = MutableStateFlow<String?>(null)

    /** Выбранная подборка; null — ещё не выбирали (показывается первая). */
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val raw = MutableStateFlow(PagedState<Video>())
    private val minDuration = MutableStateFlow(0L)
    private var pager: Pager<Video>? = null
    private var collectJob: Job? = null
    private var shown: String? = null
    private var current: Triple<Category, Filters, QueryLang>? = null

    /** Результаты после фильтров 6.4 и отсева коротких видео. */
    val results: StateFlow<PagedState<Video>> = combine(raw, minDuration, filter.rules) { state, min, rules ->
        // durationSec = -1 у эфиров и неизвестной длительности: такие в каталог не берём (кроме трейлеров)
        state.copy(items = rules.videos(state.items).filter { it.durationSec >= min })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagedState())

    fun setGenre(genre: Genre) = _filters.update { it.copy(genre = genre) }

    fun setYear(year: Int?) = _filters.update { it.copy(year = year) }

    /** Показать подборку [category] с фильтрами [filters]; [uiLang] — язык интерфейса для запроса. */
    fun show(category: Category, filters: Filters, dub: DubLanguage, uiLang: QueryLang) {
        _selectedId.value = category.id
        minDuration.value = category.minDurationSec
        current = Triple(category, filters, uiLang)
        val query = if (category.trailers) "" else QueryBuilder.build(kind, category, dub, filters.genre, filters.year, uiLang)
        val signature = "${category.id}|$query"
        if (shown == signature) return
        shown = signature
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
        val (category, filters, uiLang) = current ?: return
        shown = null
        show(category, filters, dub.value, uiLang)
    }
}

@HiltViewModel
class MoviesViewModel @Inject constructor(
    youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
    settings: SettingsRepository,
) : CatalogViewModel(CatalogKind.MOVIES, youtube, library, filter, settings)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    youtube: YouTubeRepository,
    library: LibraryRepository,
    filter: ContentFilter,
    settings: SettingsRepository,
) : CatalogViewModel(CatalogKind.SERIES, youtube, library, filter, settings)

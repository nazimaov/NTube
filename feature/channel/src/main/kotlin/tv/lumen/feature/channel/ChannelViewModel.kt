package tv.lumen.feature.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelDetails
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

sealed interface ChannelHeaderState {
    data object Loading : ChannelHeaderState
    data class Content(val details: ChannelDetails, val tabs: List<ChannelTab>) : ChannelHeaderState
    data class Error(val error: AppError) : ChannelHeaderState
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtube: YouTubeRepository,
    private val library: LibraryRepository,
    filter: ContentFilter,
) : ViewModel() {

    /** Имя аргумента — поле маршрута Channel(channelId) в app. */
    private val channelId: String = checkNotNull(savedStateHandle["channelId"])

    val focusMemory = FocusMemory()

    private val _header = MutableStateFlow<ChannelHeaderState>(ChannelHeaderState.Loading)
    val header: StateFlow<ChannelHeaderState> = _header.asStateFlow()

    private val _tab = MutableStateFlow(ChannelTab.VIDEOS)
    val tab: StateFlow<ChannelTab> = _tab.asStateFlow()

    private val _items = MutableStateFlow(PagedState<SearchItem>())
    val items: StateFlow<PagedState<SearchItem>> = combine(_items, filter.rules) { s, rules ->
        val own = rules.copy(blockedChannels = emptySet())
        s.copy(items = s.items.filter(own::allows))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PagedState())

    /** Подписан ли пользователь: сравниваем и с ID из маршрута, и с каноническим ID канала. */
    val isSubscribed: StateFlow<Boolean> = combine(library.subscriptions, _header) { subs, header ->
        val canonical = (header as? ChannelHeaderState.Content)?.details?.channel?.id
        subs.any { it.id == channelId || it.id == canonical }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleSubscribe() {
        val channel = (_header.value as? ChannelHeaderState.Content)?.details?.channel ?: return
        viewModelScope.launch {
            if (isSubscribed.value) library.unsubscribe(channel.id) else library.subscribe(channel.id, channel.name, channel.avatarUrl)
        }
    }

    private var pager: Pager<SearchItem>? = null
    private var collectJob: Job? = null

    init {
        load()
    }

    fun load() {
        _header.value = ChannelHeaderState.Loading
        viewModelScope.launch {
            when (val result = youtube.channel(channelId)) {
                is AppResult.Failure -> _header.value = ChannelHeaderState.Error(result.error)
                is AppResult.Success -> {
                    // Shorts на ТВ не показываем (SPEC 1: Shorts — после MVP)
                    val tabs = SHOWN_TABS.filter { it in result.value.tabs }.ifEmpty { listOf(ChannelTab.VIDEOS) }
                    _header.value = ChannelHeaderState.Content(result.value, tabs)
                    if (_tab.value !in tabs) _tab.value = tabs.first()
                    startTab(_tab.value)
                }
            }
        }
    }

    fun selectTab(tab: ChannelTab) {
        if (_tab.value == tab && pager != null) return
        _tab.value = tab
        startTab(tab)
    }

    fun loadMore() = pager?.loadMore()

    fun retryItems() = pager?.refresh()

    private fun startTab(tab: ChannelTab) {
        collectJob?.cancel()
        val p = Pager(viewModelScope, ::itemKey) { token ->
            if (tab == ChannelTab.PLAYLISTS) {
                youtube.channelPlaylists(channelId, token).map { page -> page.copyItems(page.items.map(SearchItem::PlaylistItem)) }
            } else {
                youtube.channelVideos(channelId, tab, token).map { page -> page.copyItems(page.items.map(SearchItem::VideoItem)) }
            }
        }
        pager = p
        collectJob = viewModelScope.launch { p.state.collect { _items.value = it } }
        p.refresh()
    }

    private fun <A, B> tv.lumen.core.model.Page<A>.copyItems(items: List<B>) = tv.lumen.core.model.Page(items, next)

    companion object {
        val SHOWN_TABS = listOf(ChannelTab.VIDEOS, ChannelTab.LIVE, ChannelTab.PLAYLISTS)

        fun itemKey(item: SearchItem): String = when (item) {
            is SearchItem.VideoItem -> "v:" + item.video.id
            is SearchItem.ChannelItem -> "c:" + item.channel.id
            is SearchItem.PlaylistItem -> "p:" + item.playlist.id
        }
    }
}
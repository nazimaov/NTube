package tv.lumen.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.Video
import tv.lumen.core.model.WatchEntry
import kotlinx.coroutines.flow.combine
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val library: LibraryRepository,
    filter: ContentFilter,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    /** null — ещё не загружено из БД. */
    val channels: StateFlow<List<ChannelSummary>?> = library.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val feed: StateFlow<List<Video>> = combine(library.feed, filter.rules) { feed, rules -> rules.videos(feed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed.asStateFlow()

    init {
        // При открытии раздела — если прошло больше 30 минут; старая лента показывается сразу (SPEC 7.1)
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            _refreshFailed.value = library.refreshFeed(force) is AppResult.Failure
            _refreshing.value = false
        }
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    library: LibraryRepository,
) : ViewModel() {
    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    /** null — ещё не загружено из БД. */
    val history: StateFlow<List<WatchEntry>?> = library.history
        .map<List<WatchEntry>, List<WatchEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
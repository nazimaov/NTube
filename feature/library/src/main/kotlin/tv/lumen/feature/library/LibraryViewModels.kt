package tv.lumen.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.lumen.data.library.ProfileRepository
import tv.lumen.data.youtube.YouTubeRepository
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
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.data.account.AccountFeed
import tv.lumen.data.account.AccountState
import tv.lumen.data.account.GoogleAccount
import tv.lumen.data.library.ContentFilter
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.VideoActions
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val library: LibraryRepository,
    filter: ContentFilter,
    private val account: GoogleAccount,
    private val accountFeed: AccountFeed,
) : ViewModel() {

    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    // Подписки аккаунта Google (если вошли) — постранично; иначе локальная лента из RSS
    private val accountItems = MutableStateFlow(PagedState<Video>())
    private var accountPager: Pager<Video>? = null

    /** Вход выполнен: лента и список подписок берутся из аккаунта. */
    val signedIn: StateFlow<Boolean> = account.state.map { it == AccountState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, account.state.value == AccountState.SignedIn)

    val feed: StateFlow<List<Video>> = combine(library.feed, accountItems, signedIn, filter.rules) { local, remote, signed, rules ->
        rules.videos(if (signed && remote.items.isNotEmpty()) remote.items else local)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ещё есть страницы ленты аккаунта. */
    val canLoadMore: StateFlow<Boolean> = combine(accountItems, signedIn) { s, signed -> signed && s.canLoadMore }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Лента аккаунта ещё не загрузилась. */
    val accountLoading: StateFlow<Boolean> = combine(accountItems, signedIn) { s, signed -> signed && s.isLoading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun loadMore() = accountPager?.loadMore()

    /**
     * Ряд каналов: со входом — каналы из ленты аккаунта (список подписок целиком YouTube на ТВ не отдаёт,
     * поэтому видны каналы, у которых есть видео в загруженных страницах); иначе — локальные подписки.
     * null — ещё не загружено из БД.
     */
    val channels: StateFlow<List<ChannelSummary>?> = combine(library.subscriptions, accountItems, signedIn) { local, remote, signed ->
        if (signed && remote.items.isNotEmpty()) channelsOf(remote.items) else local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed.asStateFlow()

    init {
        viewModelScope.launch {
            account.state.collect { state ->
                if (state == AccountState.SignedIn && accountPager == null) startAccountFeed()
                if (state != AccountState.SignedIn) { accountPager = null; accountItems.value = PagedState() }
            }
        }
    }

    private fun startAccountFeed() {
        val p = Pager<Video>(viewModelScope, { it.id }) { token -> accountFeed.subscriptions(token) }
        accountPager = p
        viewModelScope.launch { p.state.collect { accountItems.value = it } }
        p.refresh()
    }

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
    private val account: GoogleAccount,
    private val accountFeed: AccountFeed,
) : ViewModel() {
    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    // История аккаунта Google (если вошли) — постранично; без входа и пока она пуста — локальная история
    private val _account = MutableStateFlow(PagedState<Video>())
    private var accountPager: Pager<Video>? = null

    /** История аккаунта, если вход выполнен и в ней что-то есть. */
    val accountHistory: StateFlow<PagedState<Video>> = _account.asStateFlow()

    fun loadMoreAccount() = accountPager?.loadMore()

    init {
        viewModelScope.launch {
            account.state.collect { state ->
                if (state == AccountState.SignedIn && accountPager == null) {
                    val p = Pager<Video>(viewModelScope, { it.id }) { token -> accountFeed.history(token) }
                    accountPager = p
                    launch { p.state.collect { _account.value = it } }
                    p.refresh()
                }
                if (state != AccountState.SignedIn) { accountPager = null; _account.value = PagedState() }
            }
        }
    }

    /** null — ещё не загружено из БД. */
    val history: StateFlow<List<WatchEntry>?> = library.history
        .map<List<WatchEntry>, List<WatchEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Каналы из видео ленты, без повторов, в порядке появления. */
internal fun channelsOf(videos: List<Video>): List<ChannelSummary> = videos
    .filter { !it.channelId.isNullOrBlank() && !it.channelName.isNullOrBlank() }
    .distinctBy { it.channelId }
    .map { ChannelSummary(id = it.channelId!!, name = it.channelName!!, avatarUrl = null, subscriberCount = -1, isVerified = false) }

/**
 * «Понравившиеся»: со входом — плейлист «Понравившиеся» аккаунта Google (постранично),
 * без входа — свои лайки 👍 из плеера (хранятся на устройстве), с подгрузкой данных видео.
 */
@HiltViewModel
class LikedViewModel @Inject constructor(
    library: LibraryRepository,
    filter: ContentFilter,
    private val account: GoogleAccount,
    private val accountFeed: AccountFeed,
    private val profiles: ProfileRepository,
    private val youtube: YouTubeRepository,
) : ViewModel() {
    val focusMemory = FocusMemory()
    val actions = VideoActions(library, viewModelScope)

    private var pager: Pager<Video>? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val raw: Flow<PagedState<Video>> = account.state.map { it == AccountState.SignedIn }.distinctUntilChanged()
        .flatMapLatest { signed ->
            if (signed) {
                val p = Pager<Video>(viewModelScope, { it.id }) { token -> accountFeed.liked(token) }
                pager = p
                p.refresh()
                p.state
            } else {
                pager = null
                localLikes()
            }
        }

    val state: StateFlow<PagedState<Video>> = combine(raw, filter.rules) { s, rules -> s.copy(items = rules.videos(s.items)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PagedState())

    fun loadMore() = pager?.loadMore()

    fun retry() = pager?.refresh()

    /** Лайки хранят только id видео, поэтому карточки собираются по данным видео (кеш YouTube, до 3 запросов разом). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun localLikes(): Flow<PagedState<Video>> = profiles.likedVideoIds(LOCAL_LIMIT).mapLatest { ids ->
        val limiter = Semaphore(3)
        val videos = coroutineScope {
            ids.map { id -> async { limiter.withPermit { (youtube.videoDetails(id) as? AppResult.Success)?.value?.video } } }.awaitAll()
        }.filterNotNull()
        PagedState(items = videos, isLoading = false)
    }.onStart { emit(PagedState()) }

    private companion object {
        const val LOCAL_LIMIT = 50
    }
}

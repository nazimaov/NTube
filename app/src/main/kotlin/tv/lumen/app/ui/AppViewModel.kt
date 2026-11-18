package tv.lumen.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import tv.lumen.app.navigation.TopLevelDestination
import tv.lumen.core.datastore.MenuItem
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.datastore.UserLists
import tv.lumen.core.designsystem.component.MenuExtras
import tv.lumen.core.designsystem.component.PlaylistRef
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import tv.lumen.core.designsystem.component.QueueActions
import tv.lumen.core.designsystem.component.VideoDecorator
import tv.lumen.core.model.Video
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.dearrow.DeArrowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.lumen.data.updater.UpdateInfo
import tv.lumen.data.updater.UpdateManager
import tv.lumen.data.updater.UpdateState

/** Пункты бокового меню по настройкам видимости и порядка (SPEC 6.4); «Настройки» всегда последние. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    private val updates: UpdateManager,
    dearrow: DeArrowRepository,
    /** Недостающие аватары каналов на карточках. */
    val channelAvatars: ChannelAvatars,
    library: LibraryRepository,
    private val userLists: UserLists,
) : ViewModel() {

    /** Новый декоратор при смене настроек — карточки перерисовываются сразу, без перезапуска. */
    val decorator: StateFlow<VideoDecorator?> = settings.flow { it.deArrowTitles to it.deArrowThumbnails }
        .map { (titles, thumbs) -> if (titles || thumbs) DeArrowDecorator(dearrow, titles, thumbs) else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Язык интерфейса из настроек — без начального значения, только реально сохранённый. */
    val appLanguage: kotlinx.coroutines.flow.Flow<String?> = settings.flow { it.appLanguage }

    /** Очередь воспроизведения для меню карточек. */
    val queueActions: QueueActions = object : QueueActions {
        override fun add(video: Video) = library.queue.add(video)
        override fun contains(videoId: String) = library.queue.contains(videoId)
    }

    /** «Не интересно», «Смотреть позже» и свои плейлисты для меню карточек. */
    val menuExtras: MenuExtras = object : MenuExtras {
        override val playlists: StateFlow<List<PlaylistRef>> = userLists.playlists
            .map { all -> all.filter { !it.id.startsWith(UserLists.KIDS_WATCHED) }.map { PlaylistRef(it.id, it.name, it.videos.size, it.videos.mapTo(HashSet()) { v -> v.id }) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        override fun hide(video: Video) {
            viewModelScope.launch { userLists.hide(video.id) }
        }

        override fun isWatchLater(videoId: String) =
            userLists.playlists.value.firstOrNull { it.id == UserLists.WATCH_LATER }?.videos?.any { it.id == videoId } == true

        override fun toggleWatchLater(video: Video, defaultName: String) {
            viewModelScope.launch {
                userLists.create(defaultName, id = UserLists.WATCH_LATER)
                if (isWatchLater(video.id)) userLists.remove(UserLists.WATCH_LATER, video.id) else userLists.add(UserLists.WATCH_LATER, video)
            }
        }

        override fun toggleInPlaylist(playlistId: String, video: Video) {
            viewModelScope.launch {
                val has = userLists.playlists.value.firstOrNull { it.id == playlistId }?.videos?.any { it.id == video.id } == true
                if (has) userLists.remove(playlistId, video.id) else userLists.add(playlistId, video)
            }
        }

        override fun createPlaylist(video: Video, name: String) {
            viewModelScope.launch { userLists.create(name, video) }
        }
    }

    val updateState: StateFlow<UpdateState> = updates.state

    /** Есть найденное, но не установленное обновление: красная точка на шестерёнке. */
    val updatePending: StateFlow<Boolean> = updates.pending.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // При запуске, не чаще раза в 3 часа (SPEC 10.5); выключается в настройках
        viewModelScope.launch {
            if (settings.settings.first().updateCheckOnStart) updates.check(force = false)
        }
    }

    fun installUpdate(info: UpdateInfo) {
        viewModelScope.launch { updates.downloadAndInstall(info) }
    }

    fun dismissUpdate() = updates.dismiss()

    val menu: StateFlow<List<TopLevelDestination>> = settings.flow { s ->
        s.menuItems.filter { it !in s.hiddenMenuItems }.map { it.toDestination() } + TopLevelDestination.SETTINGS
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TopLevelDestination.entries)

    private fun MenuItem.toDestination() = when (this) {
        MenuItem.SEARCH -> TopLevelDestination.SEARCH
        MenuItem.HOME -> TopLevelDestination.HOME
        MenuItem.MUSIC -> TopLevelDestination.MUSIC
        MenuItem.CINEMA -> TopLevelDestination.CINEMA
        MenuItem.EXPLORE -> TopLevelDestination.EXPLORE
        MenuItem.KIDS -> TopLevelDestination.KIDS
        MenuItem.LIBRARY -> TopLevelDestination.LIBRARY
    }
}
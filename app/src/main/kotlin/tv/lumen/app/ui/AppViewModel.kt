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
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import tv.lumen.core.designsystem.component.VideoDecorator
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
) : ViewModel() {

    /** Новый декоратор при смене настроек — карточки перерисовываются сразу, без перезапуска. */
    val decorator: StateFlow<VideoDecorator?> = settings.flow { it.deArrowTitles to it.deArrowThumbnails }
        .map { (titles, thumbs) -> if (titles || thumbs) DeArrowDecorator(dearrow, titles, thumbs) else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Язык интерфейса из настроек — без начального значения, только реально сохранённый. */
    val appLanguage: kotlinx.coroutines.flow.Flow<String?> = settings.flow { it.appLanguage }

    val updateState: StateFlow<UpdateState> = updates.state

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
        MenuItem.MOVIES -> TopLevelDestination.MOVIES
        MenuItem.SERIES -> TopLevelDestination.SERIES
        MenuItem.SUBSCRIPTIONS -> TopLevelDestination.SUBSCRIPTIONS
        MenuItem.HISTORY -> TopLevelDestination.HISTORY
    }
}
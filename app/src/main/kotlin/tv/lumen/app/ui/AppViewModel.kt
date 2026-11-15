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

/** Пункты бокового меню по настройкам видимости и порядка (SPEC 6.4); «Настройки» всегда последние. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val menu: StateFlow<List<TopLevelDestination>> = settings.flow { s ->
        s.menuItems.filter { it !in s.hiddenMenuItems }.map { it.toDestination() } + TopLevelDestination.SETTINGS
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TopLevelDestination.entries)

    private fun MenuItem.toDestination() = when (this) {
        MenuItem.SEARCH -> TopLevelDestination.SEARCH
        MenuItem.HOME -> TopLevelDestination.HOME
        MenuItem.SUBSCRIPTIONS -> TopLevelDestination.SUBSCRIPTIONS
        MenuItem.HISTORY -> TopLevelDestination.HISTORY
    }
}
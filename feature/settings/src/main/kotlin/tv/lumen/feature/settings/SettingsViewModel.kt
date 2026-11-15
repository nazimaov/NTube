package tv.lumen.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.datastore.MenuItem
import tv.lumen.core.datastore.Settings
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.ChannelSummary
import tv.lumen.data.library.LibraryRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val focusMemory = FocusMemory()

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.current.value)

    val blockedChannels: StateFlow<List<ChannelSummary>> = library.blockedChannelList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Любое изменение сохраняется сразу и действует без перезапуска (SPEC 9.2). */
    fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { repository.set(change) }
    }

    fun addBlockedWord(word: String) {
        val w = word.trim()
        if (w.isNotEmpty()) update { it.copy(blockedWords = it.blockedWords + w) }
    }

    fun removeBlockedWord(word: String) = update { it.copy(blockedWords = it.blockedWords - word) }

    fun unblockChannel(id: String) {
        viewModelScope.launch { library.unblockChannel(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    fun toggleMenuItem(item: MenuItem) = update {
        it.copy(hiddenMenuItems = if (item in it.hiddenMenuItems) it.hiddenMenuItems - item else it.hiddenMenuItems + item)
    }

    fun moveMenuItemUp(item: MenuItem) = update { s ->
        val list = s.menuItems.toMutableList()
        val i = list.indexOf(item)
        if (i > 0) {
            list.removeAt(i)
            list.add(i - 1, item)
        }
        s.copy(menuItems = list)
    }
}
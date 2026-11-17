package tv.lumen.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.model.Profile
import tv.lumen.data.account.AccountState
import tv.lumen.data.account.GoogleAccount
import tv.lumen.data.library.ProfileRepository
import tv.lumen.core.datastore.MenuItem
import tv.lumen.core.datastore.Settings
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.ChannelSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.backup.BackupServer
import tv.lumen.data.library.backup.ImportResult
import tv.lumen.data.updater.UpdateManager
import javax.inject.Inject

/** url = null — сеть недоступна. */
data class BackupUiState(val url: String?, val lastResult: ImportResult? = null, val lastFailed: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val library: LibraryRepository,
    private val updates: UpdateManager,
    private val backupServer: BackupServer,
    private val profileRepository: ProfileRepository,
    private val googleAccount: GoogleAccount,
) : ViewModel() {

    /** Вход в аккаунт Google (необязательный). */
    val account: StateFlow<AccountState> = googleAccount.state
    fun signIn() = googleAccount.startSignIn()
    fun cancelSignIn() = googleAccount.cancelSignIn()
    fun signOut() = googleAccount.signOut()

    /** Локальные профили: отметки «нравится» хранятся отдельно для каждого. */
    val profiles: StateFlow<List<Profile>> = profileRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val currentProfile: StateFlow<Profile?> = profileRepository.current
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createProfile(name: String) { viewModelScope.launch { profileRepository.create(name) } }
    fun selectProfile(id: Long) { viewModelScope.launch { profileRepository.select(id) } }
    fun renameProfile(id: Long, name: String) { viewModelScope.launch { profileRepository.rename(id, name) } }
    fun deleteProfile(id: Long) { viewModelScope.launch { profileRepository.delete(id) } }

    private val _backup = MutableStateFlow<BackupUiState?>(null)
    val backup: StateFlow<BackupUiState?> = _backup.asStateFlow()

    /** Импорт и экспорт (SPEC 7.1): сервер работает, пока открыт диалог. */
    fun openBackup() {
        val url = backupServer.start { result -> _backup.update { it?.copy(lastResult = result, lastFailed = result == null) } }
        _backup.value = BackupUiState(url = url)
    }

    fun closeBackup() {
        backupServer.stop()
        _backup.value = null
    }

    override fun onCleared() {
        backupServer.stop()
    }

    /** Результат показывает общий диалог обновления в app. */
    /** Найденное, но не установленное обновление (красная точка). */
    val pendingUpdate: StateFlow<tv.lumen.data.updater.UpdateInfo?> = updates.pending

    fun checkForUpdates() {
        viewModelScope.launch { updates.check(force = true, userInitiated = true) }
    }

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
package tv.lumen.feature.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import tv.lumen.player.PlayerController
import tv.lumen.player.PlayerControllerFactory
import tv.lumen.player.PlayerState
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tv.lumen.core.model.AppError
import tv.lumen.data.updater.UpdateManager

@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    factory: PlayerControllerFactory,
    private val updates: UpdateManager,
) : ViewModel() {

    val controller: PlayerController = factory.create(viewModelScope)
    val state: StateFlow<PlayerState> = controller.state

    /** Плейлист, из которого запущено видео: следующее видео берётся из него. */
    val playlistId: String?

    init {
        // Имена аргументов — поля маршрута Watch(videoId, playlistId, startMs) в app
        val videoId: String = checkNotNull(savedStateHandle["videoId"]) { "Нет videoId" }
        val startMs: Long = savedStateHandle["startMs"] ?: 0L
        playlistId = savedStateHandle["playlistId"]
        controller.load(videoId, startMs, playlistId)
        // Сразу после ошибки разбора — тихая проверка обновления (SPEC 10.5, п. 1)
        viewModelScope.launch {
            controller.state.map { it.error }.distinctUntilChanged().collect { error ->
                if (error == AppError.ExtractionBroken) updates.check(force = true)
            }
        }
    }

    /** «Проверить обновление» на экране ошибки (SPEC 3, 10.5). */
    fun checkForUpdates() {
        viewModelScope.launch { updates.check(force = true, userInitiated = true) }
    }

    override fun onCleared() {
        controller.release()
    }
}
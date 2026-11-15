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

@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    factory: PlayerControllerFactory,
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
    }

    override fun onCleared() {
        controller.release()
    }
}
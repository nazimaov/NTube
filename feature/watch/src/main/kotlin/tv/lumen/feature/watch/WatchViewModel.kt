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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.model.Comment
import tv.lumen.data.ryd.DislikeRepository
import tv.lumen.data.youtube.YouTubeRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tv.lumen.core.model.AppError
import tv.lumen.data.updater.UpdateManager

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    factory: PlayerControllerFactory,
    private val updates: UpdateManager,
    private val dislikeRepository: DislikeRepository,
    private val youtube: YouTubeRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val controller: PlayerController = factory.create(viewModelScope)

    /** Автоподстройка частоты кадров (SPEC 5.6) — читается экраном. */
    val matchFrameRate: StateFlow<Boolean> = settings.flow { it.matchFrameRate }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.current.value.matchFrameRate)

    /** «≈ дизлайки» (SPEC 6.3); null — мод выключен или сервер недоступен. */
    val dislikes: StateFlow<Long?> = combine(controller.state.map { it.details?.video?.id }.distinctUntilChanged(), settings.flow { it.rydEnabled }) { id, on -> id to on }
        .mapLatest { (id, on) -> if (id != null && on) dislikeRepository.dislikes(id) else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var commentsPager: Pager<Comment>? = null
    private val _comments = MutableStateFlow<PagedState<Comment>?>(null)
    val comments: StateFlow<PagedState<Comment>?> = _comments.asStateFlow()

    /** Комментарии грузятся только при открытии панели. */
    fun openComments() {
        val id = controller.state.value.videoId ?: return
        if (commentsPager != null) return
        val pager = Pager<Comment>(viewModelScope, { it.id }) { token -> youtube.comments(id, token) }
        commentsPager = pager
        viewModelScope.launch { pager.state.collect { _comments.value = it } }
        pager.refresh()
    }

    fun loadMoreComments() = commentsPager?.loadMore()

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
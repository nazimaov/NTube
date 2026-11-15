package tv.lumen.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import tv.lumen.core.common.PagedState
import tv.lumen.core.common.Pager
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.model.Video
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    youtube: YouTubeRepository,
) : ViewModel() {

    /** Имена аргументов — поля маршрута Playlist(playlistId, title) в app. */
    val playlistId: String = checkNotNull(savedStateHandle["playlistId"])
    val title: String = savedStateHandle["title"] ?: ""

    val focusMemory = FocusMemory()

    private val pager = Pager<Video>(viewModelScope, { it.id }) { token -> youtube.playlist(playlistId, token) }
    val items: StateFlow<PagedState<Video>> = pager.state

    init {
        pager.refresh()
    }

    fun loadMore() = pager.loadMore()
    fun retry() = pager.refresh()
}
package tv.lumen.data.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.lumen.core.model.Video

/**
 * Действия меню карточки (SPEC 8): подписаться/отписаться, не показывать канал, удалить из истории.
 * Создаётся во ViewModel экрана с её scope.
 */
class VideoActions(private val library: LibraryRepository, private val scope: CoroutineScope) {

    val subscribedIds: StateFlow<Set<String>> = library.subscriptions
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val progress: StateFlow<Map<String, Float>> = library.progress
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun toggleSubscribe(video: Video) {
        val channelId = video.channelId ?: return
        scope.launch {
            if (channelId in subscribedIds.value) library.unsubscribe(channelId)
            else library.subscribe(channelId, video.channelName.orEmpty(), null)
        }
    }

    fun hideChannel(video: Video) {
        val channelId = video.channelId ?: return
        scope.launch { library.blockChannel(channelId, video.channelName.orEmpty()) }
    }

    fun removeFromHistory(video: Video) {
        scope.launch { library.removeFromHistory(video.id) }
    }
}
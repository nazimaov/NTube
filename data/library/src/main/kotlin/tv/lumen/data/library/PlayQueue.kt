package tv.lumen.data.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.lumen.core.model.Video
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Очередь воспроизведения — свой список видео, который собирает пользователь. Живёт только в
 * памяти, пока работает приложение: на диск не сохраняется и между запусками не переносится.
 */
@Singleton
class PlayQueue @Inject constructor() {
    private val _items = MutableStateFlow<List<Video>>(emptyList())
    val items: StateFlow<List<Video>> = _items.asStateFlow()

    /** Добавляет в конец. Повторное добавление того же видео ничего не меняет; true — добавлено. */
    fun add(video: Video): Boolean {
        var added = false
        _items.update { list ->
            if (list.any { it.id == video.id }) list else (list + video).also { added = true }
        }
        return added
    }

    fun contains(videoId: String): Boolean = _items.value.any { it.id == videoId }

    fun remove(videoId: String) = _items.update { list -> list.filterNot { it.id == videoId } }

    /** Сдвигает видео на [delta] мест (−1 — левее, +1 — правее); за границы списка не выходит. */
    fun move(videoId: String, delta: Int) = _items.update { list ->
        val from = list.indexOfFirst { it.id == videoId }
        if (from < 0) return@update list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return@update list
        list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun clear() = _items.update { emptyList() }

    /**
     * Что играть после [currentId]: следующее из очереди; если текущего в очереди нет — первое.
     * null — очередь кончилась или пуста (дальше работает обычная логика «похожих»).
     */
    fun nextAfter(currentId: String?): Video? {
        val list = _items.value
        if (list.isEmpty()) return null
        val index = list.indexOfFirst { it.id == currentId }
        return if (index < 0) list.first() else list.getOrNull(index + 1)
    }
}

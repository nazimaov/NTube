package tv.lumen.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tv.lumen.core.common.ApplicationScope
import tv.lumen.core.model.Video
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.listsStore: DataStore<Preferences> by preferencesDataStore(name = "lists")

/** Видео в своём плейлисте: только то, что нужно для карточки. */
@Serializable
data class ListVideo(
    val id: String,
    val title: String,
    val channelId: String? = null,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val durationSec: Long = -1,
) {
    fun toVideo() = Video(
        id = id, title = title, channelId = channelId, channelName = channelName, thumbnailUrl = thumbnailUrl,
        durationSec = durationSec, viewCount = -1, publishedAt = null, publishedText = null, isLive = false, isShort = false,
    )

    companion object {
        fun of(video: Video) = ListVideo(video.id, video.title, video.channelId, video.channelName, video.thumbnailUrl, video.durationSec)
    }
}

/** Свой плейлист на устройстве: новые видео добавляются в конец. */
@Serializable
data class UserPlaylist(val id: String, val name: String, val videos: List<ListVideo> = emptyList())

/**
 * Списки пользователя на устройстве: скрытые видео («Не интересно») и свои плейлисты, включая
 * «Смотреть позже» ([WATCH_LATER]). Хранятся в отдельном DataStore; в YouTube ничего не отправляется.
 */
@Singleton
class UserLists @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = context.listsStore
    private val json = Json { ignoreUnknownKeys = true }

    /** Идентификаторы скрытых видео; лента их больше не показывает. */
    val hiddenVideos: StateFlow<Set<String>> = store.data
        .map { p -> p[HIDDEN].orEmpty().split(',').filter { it.isNotBlank() }.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val playlists: StateFlow<List<UserPlaylist>> = store.data
        .map { p -> decode(p[PLAYLISTS]) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun hide(videoId: String) {
        store.edit { p ->
            val ids = p[HIDDEN].orEmpty().split(',').filter { it.isNotBlank() && it != videoId } + videoId
            p[HIDDEN] = ids.takeLast(MAX_HIDDEN).joinToString(",")
        }
    }

    suspend fun unhide(videoId: String) {
        store.edit { p -> p[HIDDEN] = p[HIDDEN].orEmpty().split(',').filter { it.isNotBlank() && it != videoId }.joinToString(",") }
    }

    /** Новый плейлист (сразу с [first], если передано); возвращает его id. */
    suspend fun create(name: String, first: Video? = null, id: String = UUID.randomUUID().toString()): String {
        update { list -> if (list.any { it.id == id }) list else list + UserPlaylist(id, name, listOfNotNull(first?.let(ListVideo::of))) }
        return id
    }

    suspend fun add(playlistId: String, video: Video) = update { list ->
        list.map { if (it.id == playlistId && it.videos.none { v -> v.id == video.id }) it.copy(videos = it.videos + ListVideo.of(video)) else it }
    }

    suspend fun remove(playlistId: String, videoId: String) = update { list ->
        list.map { if (it.id == playlistId) it.copy(videos = it.videos.filter { v -> v.id != videoId }) else it }
    }

    suspend fun delete(playlistId: String) = update { list -> list.filter { it.id != playlistId } }

    suspend fun rename(playlistId: String, name: String) = update { list -> list.map { if (it.id == playlistId) it.copy(name = name) else it } }

    private suspend fun update(change: (List<UserPlaylist>) -> List<UserPlaylist>) {
        store.edit { p -> p[PLAYLISTS] = json.encodeToString(change(decode(p[PLAYLISTS]))) }
    }

    private fun decode(text: String?): List<UserPlaylist> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString<List<UserPlaylist>>(text) }.getOrDefault(emptyList())

    companion object {
        /** Идентификатор встроенного плейлиста «Смотреть позже». */
        const val WATCH_LATER = "watch_later"

        /** Служебный список: видео, открытые в «Детском»; по ним строятся рекомендации раздела, в «Плейлистах» не показывается. */
        const val KIDS_WATCHED = "kids_watched"
        private const val MAX_HIDDEN = 3000
        private val HIDDEN = stringPreferencesKey("hidden_videos")
        private val PLAYLISTS = stringPreferencesKey("playlists")
    }
}

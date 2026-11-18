package tv.lumen.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf
import tv.lumen.core.model.Video

/** Замена заголовка и обложки карточки (DeArrow, SPEC 6.2). null в полях — оставить оригинал. */
data class VideoDecoration(val title: String?, val thumbnailUrl: String?)

/** Подключается в app; экраны о нём не знают. Запрашивается лениво — только для показанных карточек. */
fun interface VideoDecorator {
    suspend fun decorate(video: Video): VideoDecoration?
}

val LocalVideoDecorator = staticCompositionLocalOf<VideoDecorator?> { null }

/**
 * Аватар канала для карточки, если его нет в самом видео (история, импортированные подписки).
 * Подключается в app; null — показать букву.
 */
fun interface ChannelAvatarResolver {
    suspend fun avatarUrl(channelId: String): String?
}

val LocalChannelAvatarResolver = staticCompositionLocalOf<ChannelAvatarResolver?> { null }


/** Очередь воспроизведения для меню карточек; подключается в app, экраны о ней не знают. */
interface QueueActions {
    /** true — добавлено, false — такое видео уже в очереди. */
    fun add(video: Video): Boolean
    fun contains(videoId: String): Boolean
}

val LocalQueueActions = staticCompositionLocalOf<QueueActions?> { null }

/** Свой плейлист для меню карточки: название, число видео и id видео внутри (для отметки «уже добавлено»). */
data class PlaylistRef(val id: String, val name: String, val size: Int, val videoIds: Set<String>)

/**
 * Что меню карточки умеет сверх очереди: скрыть видео («Не интересно»), «Смотреть позже» и свои плейлисты.
 * Подключается в app, экраны о нём не знают: меню у всех карточек получает пункты само.
 */
interface MenuExtras {
    val playlists: kotlinx.coroutines.flow.StateFlow<List<PlaylistRef>>

    /** Видео сразу исчезает из всех списков и больше не показывается. */
    fun hide(video: Video)

    fun isWatchLater(videoId: String): Boolean
    fun toggleWatchLater(video: Video, defaultName: String)

    /** Добавляет видео в плейлист, а если оно там уже есть — убирает. */
    fun toggleInPlaylist(playlistId: String, video: Video)

    /** Новый плейлист [name] сразу с этим видео. */
    fun createPlaylist(video: Video, name: String)

    companion object {
        const val WATCH_LATER = "watch_later"
    }
}

val LocalMenuExtras = staticCompositionLocalOf<MenuExtras?> { null }

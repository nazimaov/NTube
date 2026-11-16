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

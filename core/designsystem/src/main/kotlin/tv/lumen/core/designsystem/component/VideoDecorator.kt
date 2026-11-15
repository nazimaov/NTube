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
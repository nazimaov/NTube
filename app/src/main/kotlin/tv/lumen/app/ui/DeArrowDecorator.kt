package tv.lumen.app.ui

import tv.lumen.core.designsystem.component.VideoDecoration
import tv.lumen.core.designsystem.component.VideoDecorator
import tv.lumen.core.model.Video
import tv.lumen.data.dearrow.DeArrowRepository

/** DeArrow для карточек: включается отдельно для заголовков и обложек (SPEC 6.2, 9.2). */
class DeArrowDecorator(
    private val dearrow: DeArrowRepository,
    private val titles: Boolean,
    private val thumbnails: Boolean,
) : VideoDecorator {
    override suspend fun decorate(video: Video): VideoDecoration? {
        if (video.isLive) return null
        val branding = dearrow.branding(video.id) ?: return null
        val title = branding.title.takeIf { titles }
        val thumb = branding.thumbnailTime?.takeIf { thumbnails }?.let { dearrow.thumbnailUrl(video.id, it) }
        return if (title == null && thumb == null) null else VideoDecoration(title, thumb)
    }
}
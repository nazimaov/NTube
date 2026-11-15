package tv.lumen.core.designsystem.format

import android.icu.text.CompactDecimalFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import tv.lumen.core.designsystem.R
import tv.lumen.core.model.Video
import java.util.Locale

/** 635 → «10:35», 3725 → «1:02:05». */
fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 1 234 567 → «1,2 млн» / «1.2M». */
fun formatCompact(count: Long, locale: Locale = Locale.getDefault()): String =
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)

@Composable
fun viewsText(count: Long): String? {
    if (count < 0) return null
    val locale = LocalConfiguration.current.locales[0]
    return pluralStringResource(R.plurals.ds_views, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatCompact(count, locale))
}

@Composable
fun subscribersText(count: Long): String? {
    if (count < 0) return null
    val locale = LocalConfiguration.current.locales[0]
    return pluralStringResource(R.plurals.ds_subscribers, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatCompact(count, locale))
}

@Composable
fun videoCountText(count: Long): String? {
    if (count < 0) return null
    return pluralStringResource(R.plurals.ds_videos, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count.toString())
}

/** «2 дня назад» по точной дате, иначе текст YouTube. */
fun publishedText(video: Video, now: Long = System.currentTimeMillis()): String? {
    val at = video.publishedAt
    if (at != null) {
        return DateUtils.getRelativeTimeSpanString(at.toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    return video.publishedText
}

/** «1,2 млн просмотров · 2 дня назад». */
@Composable
fun videoMeta(video: Video): String =
    listOfNotNull(viewsText(video.viewCount), publishedText(video)).joinToString(" · ")
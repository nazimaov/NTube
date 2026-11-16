package tv.lumen.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import tv.lumen.core.designsystem.R
import tv.lumen.core.designsystem.format.formatDuration
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.designsystem.format.subscribersText
import tv.lumen.core.designsystem.format.videoCountText
import tv.lumen.core.designsystem.format.videoMeta
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.PlaylistSummary
import tv.lumen.core.model.Video

/** Ширина карточки в рядах; в сетке карточка занимает ячейку. */
val CardWidth: Dp = 240.dp

private val CardShape = RoundedCornerShape(12.dp)
private val ThumbShape = RoundedCornerShape(10.dp)

@Composable
private fun focusBorder(shape: Shape = CardShape) = CardDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, LumenColors.FocusRing), shape = shape),
)

/** В фокусе — увеличение 1,03 и светлая рамка, как у YouTube на ТВ. */
private val FocusScale = CardDefaults.scale(focusedScale = 1.03f)

@Composable
private fun cardColors() = CardDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = LumenColors.SurfaceCard,
    pressedContainerColor = LumenColors.SurfaceCard,
)

/** Круглый аватар канала; пока нет картинки или её нет совсем — первая буква названия. */
@Composable
fun ChannelAvatar(url: String?, name: String?, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val letter = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    val hue = ((name?.hashCode() ?: 0) and 0x7FFFFFFF) % 360
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(Color.hsv(hue.toFloat(), 0.45f, 0.45f)),
    ) {
        Text(letter, style = MaterialTheme.typography.labelMedium, color = Color.White)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Карточка видео в стиле YouTube: обложка 16:9, длительность в углу, красная полоса прогресса [progress]
 * (0..1, null — не смотрели); ниже аватар канала, заголовок в 2 строки, канал, просмотры и дата.
 * Фокус охватывает всю карточку.
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null,
    showAvatar: Boolean = true,
) {
    // DeArrow: сначала оригинал, подмена после ответа — без мигания (SPEC 6.2)
    val decorator = LocalVideoDecorator.current
    val decoration by produceState<VideoDecoration?>(null, video.id, decorator) {
        value = decorator?.let { runCatching { it.decorate(video) }.getOrNull() }
    }
    // Аватара нет в видео (история, импорт) — спросить у приложения
    val avatarResolver = LocalChannelAvatarResolver.current
    val channelAvatar by produceState(video.channelAvatarUrl, video.channelAvatarUrl, video.channelId, avatarResolver, showAvatar) {
        val channelId = video.channelId
        if (value == null && showAvatar && channelId != null && avatarResolver != null) {
            value = runCatching { avatarResolver.avatarUrl(channelId) }.getOrNull()
        }
    }
    val originalThumb = rememberAsyncImagePainter(video.thumbnailUrl)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = CardDefaults.shape(CardShape),
        colors = cardColors(),
        scale = FocusScale,
        border = focusBorder(),
        modifier = modifier,
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(ThumbShape)) {
                AsyncImage(
                    model = decoration?.thumbnailUrl ?: video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = originalThumb,
                    error = originalThumb, // 204 у DeArrow — показать оригинал
                    modifier = Modifier.fillMaxSize().background(LumenColors.Surface),
                )
                val badge = if (video.isLive) stringResource(R.string.ds_live) else formatDuration(video.durationSec)
                if (badge.isNotEmpty()) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (video.isLive) LumenColors.Red else Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (progress != null && progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f)))
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp)
                            .background(LumenColors.Red),
                    )
                }
            }
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showAvatar) ChannelAvatar(channelAvatar, video.channelName)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = decoration?.title ?: video.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondary = listOfNotNull(video.channelName, videoMeta(video).takeIf { it.isNotEmpty() })
                    if (secondary.isNotEmpty()) {
                        Text(
                            text = secondary.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = LumenColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(channel: ChannelSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(CircleShape),
            scale = FocusScale,
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, LumenColors.FocusRing), shape = CircleShape)),
            modifier = Modifier.size(120.dp),
        ) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subscribersText(channel.subscriberCount)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(CardShape),
            scale = FocusScale,
            border = focusBorder(),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                )
                videoCountText(playlist.videoCount)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(8.dp),
                    )
                }
            }
        }
        Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        playlist.channelName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private val IntrinsicHalf = 120.dp
package tv.lumen.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import tv.lumen.core.designsystem.R
import tv.lumen.core.designsystem.format.formatDuration
import tv.lumen.core.designsystem.format.subscribersText
import tv.lumen.core.designsystem.format.videoCountText
import tv.lumen.core.designsystem.format.videoMeta
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.PlaylistSummary
import tv.lumen.core.model.Video

/** Ширина карточки в рядах; в сетке карточка занимает ячейку. */
val CardWidth: Dp = 296.dp

private val CardShape = RoundedCornerShape(12.dp)

@Composable
private fun focusBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = CardShape),
)

/** В фокусе — увеличение 1,05 и рамка (SPEC 8). */
private val FocusScale = CardDefaults.scale(focusedScale = 1.05f)

/**
 * Карточка видео: обложка 16:9, длительность, заголовок в 2 строки, канал, просмотры и дата,
 * полоса прогресса [progress] (0..1, null — не смотрели).
 */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = CardDefaults.shape(CardShape),
            scale = FocusScale,
            border = focusBorder(),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                )
                val badge = if (video.isLive) stringResource(R.string.ds_live) else formatDuration(video.durationSec)
                if (badge.isNotEmpty()) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (video.isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (progress != null && progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f)))
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val secondary = listOfNotNull(video.channelName, videoMeta(video).takeIf { it.isNotEmpty() })
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = CircleShape)),
            modifier = Modifier.size(140.dp),
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
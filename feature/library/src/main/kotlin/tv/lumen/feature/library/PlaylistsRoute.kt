package tv.lumen.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.component.LocalQueueActions
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.component.VideoMenu
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.Video

private val TileShape = RoundedCornerShape(16.dp)

/** «Плейлисты»: свои плейлисты на устройстве («Смотреть позже» и созданные из меню карточек). */
@Composable
fun PlaylistsRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Video?>(null) }
    val queue = LocalQueueActions.current
    FocusMemoryScope(viewModel.focusMemory)

    val open = playlists.firstOrNull { it.id == openId }
    if (open == null) {
        if (playlists.isEmpty()) {
            Message(stringResource(R.string.playlists_empty), modifier)
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal, top = 16.dp, bottom = LumenDimens.SafeVertical),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = modifier.fillMaxSize().focusRestorer(),
        ) {
            items(playlists, key = { it.id }) { list ->
                Surface(
                    onClick = { openId = list.id },
                    shape = ClickableSurfaceDefaults.shape(TileShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = LumenColors.Surface,
                        contentColor = LumenColors.TextPrimary,
                        focusedContainerColor = LumenColors.TextPrimary,
                        focusedContentColor = LumenColors.Background,
                    ),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, LumenColors.FocusRing), shape = TileShape)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier.rememberFocus(viewModel.focusMemory, list.id, isFirst = list == playlists.first()),
                ) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(list.name, style = MaterialTheme.typography.titleMedium, color = Color.Unspecified, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.playlists_count, list.videos.size), style = MaterialTheme.typography.bodySmall, color = Color.Unspecified)
                    }
                }
            }
        }
        return
    }

    BackHandler { openId = null }
    val videos = open.videos.map { it.toVideo() }
    PagedGrid(
        items = videos,
        key = { it.id },
        focusMemory = viewModel.focusMemory,
        modifier = modifier,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(open.name, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            videos.forEach { queue?.add(it) }
                            videos.firstOrNull()?.let { onOpenVideo(it.id) }
                        },
                        enabled = videos.isNotEmpty(),
                        modifier = Modifier.rememberFocus(viewModel.focusMemory, "play_all", isFirst = true),
                    ) { Text(stringResource(R.string.playlists_play_all)) }
                    Button(onClick = { viewModel.delete(open.id); openId = null }) { Text(stringResource(R.string.playlists_delete)) }
                    Button(onClick = { openId = null }) { Text(stringResource(R.string.playlists_back)) }
                }
                if (videos.isEmpty()) Text(stringResource(R.string.playlists_list_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { video, itemModifier ->
        VideoCard(video, onClick = { onOpenVideo(video.id) }, onLongClick = { menuFor = video }, progress = progress[video.id], modifier = itemModifier)
    }

    menuFor?.let { video ->
        VideoMenu(
            video = video,
            isSubscribed = video.channelId in subscribed,
            onDismiss = { menuFor = null },
            onOpenChannel = { video.channelId?.let(onOpenChannel) },
            onToggleSubscribe = { viewModel.actions.toggleSubscribe(video) },
            onHideChannel = { viewModel.actions.hideChannel(video) },
            onRemoveFromHistory = null,
            extraItems = listOf(stringResource(R.string.playlists_remove) to { viewModel.remove(open.id, video.id) }),
        )
    }
}

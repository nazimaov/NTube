package tv.lumen.feature.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import tv.lumen.core.designsystem.component.ChannelAvatar
import tv.lumen.core.designsystem.component.LumenChip
import tv.lumen.core.designsystem.theme.LumenColors
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.lumen.core.designsystem.component.EmptyState
import tv.lumen.core.designsystem.component.ErrorState
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.PlaylistCard
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.format.subscribersText
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.SearchItem

@Composable
fun ChannelRoute(
    onOpenVideo: (String) -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    FocusMemoryScope(viewModel.focusMemory)

    when (val h = header) {
        ChannelHeaderState.Loading -> LoadingState(modifier)
        is ChannelHeaderState.Error -> ErrorState(h.error, onRetry = viewModel::load, modifier = modifier)
        is ChannelHeaderState.Content -> PagedGrid(
            items = items.items,
            key = ChannelViewModel::itemKey,
            focusMemory = viewModel.focusMemory,
            canLoadMore = items.canLoadMore,
            onLoadMore = viewModel::loadMore,
            modifier = modifier,
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    h.details.bannerUrl?.let { banner ->
                        AsyncImage(
                            model = banner,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
                                .background(LumenColors.Surface),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(h.details.channel.avatarUrl, h.details.channel.name, size = 72.dp)
                        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(h.details.channel.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                            subscribersText(h.details.channel.subscriberCount)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextSecondary)
                            }
                        }
                        // Как у YouTube: «Подписаться» — светлая кнопка, «Вы подписаны» — серая
                        LumenChip(
                            text = stringResource(if (isSubscribed) R.string.channel_subscribed else R.string.channel_subscribe),
                            selected = !isSubscribed,
                            onClick = viewModel::toggleSubscribe,
                            modifier = Modifier.rememberFocus(viewModel.focusMemory, "subscribe"),
                        )
                    }
                    if (h.tabs.size > 1) {
                        TabRow(selectedTabIndex = h.tabs.indexOf(tab).coerceAtLeast(0)) {
                            h.tabs.forEach { t ->
                                Tab(
                                    selected = t == tab,
                                    onFocus = { viewModel.selectTab(t) },
                                    modifier = Modifier.rememberFocus(viewModel.focusMemory, "tab:$t"),
                                ) {
                                    Text(stringResource(t.label()), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                    when {
                        items.isLoading -> Box(Modifier.fillMaxWidth().height(200.dp)) { LoadingState() }
                        items.error != null -> Box(Modifier.fillMaxWidth().height(300.dp)) { ErrorState(items.error!!, onRetry = { viewModel.retryItems() }) }
                        items.items.isEmpty() -> Box(Modifier.fillMaxWidth().height(200.dp)) { EmptyState() }
                    }
                }
            },
        ) { item, itemModifier ->
            when (item) {
                is SearchItem.VideoItem -> VideoCard(item.video, onClick = { onOpenVideo(item.video.id) }, modifier = itemModifier, showAvatar = false)
                is SearchItem.PlaylistItem -> PlaylistCard(
                    item.playlist,
                    onClick = { onOpenPlaylist(item.playlist.id, item.playlist.title) },
                    modifier = itemModifier,
                )
                is SearchItem.ChannelItem -> Unit
            }
        }
    }
}

private fun ChannelTab.label(): Int = when (this) {
    ChannelTab.VIDEOS -> R.string.channel_tab_videos
    ChannelTab.LIVE -> R.string.channel_tab_live
    ChannelTab.PLAYLISTS -> R.string.channel_tab_playlists
    ChannelTab.SHORTS -> R.string.channel_tab_videos
}
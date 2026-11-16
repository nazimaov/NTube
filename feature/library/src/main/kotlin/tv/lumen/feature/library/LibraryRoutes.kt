package tv.lumen.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.component.ChannelCard
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.component.VideoMenu
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.Video

/** Подписки: ряд аватаров каналов и лента новых видео (SPEC 8). */
@Composable
fun SubscriptionsRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshFailed by viewModel.refreshFailed.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    FocusMemoryScope(viewModel.focusMemory)

    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.canLoadMore.collectAsStateWithLifecycle()
    val accountLoading by viewModel.accountLoading.collectAsStateWithLifecycle()

    val list = channels ?: return LoadingState(modifier)
    if (signedIn && accountLoading && feed.isEmpty()) return LoadingState(modifier)
    if (list.isEmpty() && feed.isEmpty()) return Message(stringResource(R.string.subscriptions_empty), modifier)

    PagedGrid(
        items = feed,
        key = { it.id },
        focusMemory = viewModel.focusMemory,
        modifier = modifier,
        canLoadMore = canLoadMore,
        onLoadMore = viewModel::loadMore,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(stringResource(R.string.subscriptions_title), style = MaterialTheme.typography.headlineMedium)
                    Button(
                        onClick = { viewModel.refresh(force = true) },
                        enabled = !refreshing,
                        modifier = Modifier.rememberFocus(viewModel.focusMemory, "refresh"),
                    ) {
                        Text(stringResource(if (refreshing) tv.lumen.core.designsystem.R.string.ds_loading else tv.lumen.core.designsystem.R.string.ds_refresh))
                    }
                }
                if (refreshFailed) {
                    Text(stringResource(R.string.subscriptions_feed_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (list.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer()) {
                    items(list, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { onOpenChannel(channel.id) },
                            modifier = Modifier.width(160.dp).rememberFocus(viewModel.focusMemory, "ch:${channel.id}"),
                        )
                    }
                }
            }
        },
    ) { video, itemModifier ->
        VideoCard(
            video = video,
            onClick = { onOpenVideo(video.id) },
            onLongClick = { menuFor = video },
            progress = progress[video.id],
            modifier = itemModifier,
        )
    }

    menuFor?.let { video ->
        VideoMenu(
            video = video,
            isSubscribed = video.channelId in subscribed,
            onDismiss = { menuFor = null },
            onOpenChannel = { video.channelId?.let(onOpenChannel) },
            onToggleSubscribe = { viewModel.actions.toggleSubscribe(video) },
            onHideChannel = { viewModel.actions.hideChannel(video) },
            onRemoveFromHistory = if (progress.containsKey(video.id)) ({ viewModel.actions.removeFromHistory(video) }) else null,
        )
    }
}

/** История: последние 500 просмотренных с полосой прогресса (SPEC 7.2). */
@Composable
fun HistoryRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    FocusMemoryScope(viewModel.focusMemory)

    val entries = history ?: return LoadingState(modifier)
    if (entries.isEmpty()) return Message(stringResource(R.string.history_empty), modifier)

    PagedGrid(
        items = entries,
        key = { it.video.id },
        focusMemory = viewModel.focusMemory,
        modifier = modifier,
        header = { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium) },
    ) { entry, itemModifier ->
        VideoCard(
            video = entry.video,
            onClick = { onOpenVideo(entry.video.id) },
            onLongClick = { menuFor = entry.video },
            progress = entry.progress,
            modifier = itemModifier,
        )
    }

    menuFor?.let { video ->
        VideoMenu(
            video = video,
            isSubscribed = video.channelId in subscribed,
            onDismiss = { menuFor = null },
            onOpenChannel = { video.channelId?.let(onOpenChannel) },
            onToggleSubscribe = { viewModel.actions.toggleSubscribe(video) },
            onHideChannel = { viewModel.actions.hideChannel(video) },
            onRemoveFromHistory = { viewModel.actions.removeFromHistory(video) },
        )
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = LumenDimens.SafeHorizontal), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
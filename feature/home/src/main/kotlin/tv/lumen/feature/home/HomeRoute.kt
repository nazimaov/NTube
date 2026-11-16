package tv.lumen.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumen.core.designsystem.component.ContentRow
import tv.lumen.core.designsystem.component.ChipRow
import tv.lumen.core.designsystem.component.ErrorState
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.LumenChip
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.component.VideoMenu
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.Video

@Composable
fun HomeRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    var selected by rememberSaveable { mutableStateOf(HomeChip.All) }
    FocusMemoryScope(viewModel.focusMemory)

    val trending = state.trending
    // Пока ничего нет — экран загрузки или ошибки трендов
    if (state.continueWatching.isEmpty() && state.subscriptionsNew.isEmpty()) {
        when (trending) {
            TrendingState.Loading -> return LoadingState(modifier)
            is TrendingState.Error -> return ErrorState(trending.error, onRetry = viewModel::loadTrending, modifier = modifier)
            is TrendingState.Content -> Unit
        }
    }

    val firstRow = when {
        state.continueWatching.isNotEmpty() -> "continue"
        state.subscriptionsNew.isNotEmpty() -> "subs"
        else -> "trending"
    }

    @Composable
    fun card(video: Video, itemModifier: Modifier, progressOverride: Float? = null) = VideoCard(
        video = video,
        onClick = { onOpenVideo(video.id) },
        onLongClick = { menuFor = video },
        progress = progressOverride ?: progress[video.id],
        modifier = itemModifier,
    )

    Column(modifier = modifier.fillMaxSize()) {
        ChipRow(Modifier.padding(start = LumenDimens.SafeHorizontal, top = LumenDimens.SafeVertical, bottom = 4.dp)) {
            HomeChip.entries.forEach { chip ->
                LumenChip(text = stringResource(chip.label), selected = chip == selected, onClick = { selected = chip })
            }
        }
        when (selected) {
            HomeChip.All -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (state.continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        ContentRow(
                            title = stringResource(R.string.home_continue),
                            items = state.continueWatching,
                            key = { it.video.id },
                            focusMemory = viewModel.focusMemory,
                            rowKey = "continue",
                            isFirstRow = firstRow == "continue",
                        ) { entry, m -> card(entry.video, m, entry.progress) }
                    }
                }
                if (state.subscriptionsNew.isNotEmpty()) {
                    item(key = "subs") {
                        ContentRow(
                            title = stringResource(R.string.home_subscriptions),
                            items = state.subscriptionsNew,
                            key = { it.id },
                            focusMemory = viewModel.focusMemory,
                            rowKey = "subs",
                            isFirstRow = firstRow == "subs",
                        ) { video, m -> card(video, m) }
                    }
                }
                if (trending is TrendingState.Content && trending.videos.isNotEmpty()) {
                    item(key = "trending") {
                        ContentRow(
                            title = stringResource(R.string.home_trending),
                            items = trending.videos,
                            key = { it.id },
                            focusMemory = viewModel.focusMemory,
                            rowKey = "trending",
                            isFirstRow = firstRow == "trending",
                        ) { video, m -> card(video, m) }
                    }
                }
            }
            HomeChip.Subscriptions -> PagedGrid(
                items = state.subscriptionsNew,
                key = { it.id },
                focusMemory = viewModel.focusMemory,
                columns = 3,
            ) { video, m -> card(video, m) }
            HomeChip.Trending -> PagedGrid(
                items = (trending as? TrendingState.Content)?.videos.orEmpty(),
                key = { it.id },
                focusMemory = viewModel.focusMemory,
                columns = 3,
            ) { video, m -> card(video, m) }
        }
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
private enum class HomeChip(@StringRes val label: Int) {
    All(R.string.home_chip_all),
    Subscriptions(R.string.home_chip_subscriptions),
    Trending(R.string.home_chip_trending),
}

package tv.lumen.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.component.EmptyState
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
fun MoviesRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoviesViewModel = hiltViewModel(),
) = CatalogScreen(CatalogKind.MOVIES, viewModel, onOpenVideo, onOpenChannel, modifier)

@Composable
fun SeriesRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) = CatalogScreen(CatalogKind.SERIES, viewModel, onOpenVideo, onOpenChannel, modifier)

@Composable
private fun CatalogScreen(
    kind: CatalogKind,
    viewModel: CatalogViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier,
) {
    val categories = remember(kind) { Categories.of(kind) }
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    // Выбранный чип; пока ничего не выбирали — первый подраздел
    var chosen by remember { mutableStateOf(categories.firstOrNull { it.id == selectedId } ?: categories.first()) }
    FocusMemoryScope(viewModel.focusMemory)

    val query = stringResource(chosen.query)
    // Новый запрос — при смене подраздела и при смене языка интерфейса
    LaunchedEffect(chosen.id, query) { viewModel.show(chosen, query) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(if (kind == CatalogKind.MOVIES) R.string.catalog_movies_title else R.string.catalog_series_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = LumenDimens.ContentStart, top = LumenDimens.SafeVertical),
        )
        LazyRow(
            contentPadding = PaddingValues(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(categories, key = { it.id }) { category ->
                LumenChip(
                    text = stringResource(category.label),
                    selected = category.id == chosen.id,
                    onClick = { chosen = category },
                )
            }
        }
        when {
            results.isLoading -> LoadingState()
            results.error != null -> ErrorState(results.error!!, onRetry = viewModel::retry)
            results.items.isEmpty() && !results.canLoadMore -> EmptyState()
            else -> PagedGrid(
                items = results.items,
                key = { it.id },
                focusMemory = viewModel.focusMemory,
                canLoadMore = results.canLoadMore,
                onLoadMore = viewModel::loadMore,
            ) { video, itemModifier ->
                VideoCard(
                    video,
                    onClick = { onOpenVideo(video.id) },
                    onLongClick = { menuFor = video },
                    progress = progress[video.id],
                    modifier = itemModifier,
                )
            }
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

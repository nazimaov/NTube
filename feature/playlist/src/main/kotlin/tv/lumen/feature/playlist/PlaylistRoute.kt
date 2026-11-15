package tv.lumen.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.component.EmptyState
import tv.lumen.core.designsystem.component.ErrorState
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus

@Composable
fun PlaylistRoute(
    /** videoId, playlistId */
    onOpenVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    FocusMemoryScope(viewModel.focusMemory)
    when {
        state.isLoading -> LoadingState(modifier)
        state.error != null -> ErrorState(state.error!!, onRetry = viewModel::retry, modifier = modifier)
        state.items.isEmpty() -> EmptyState(modifier)
        else -> PagedGrid(
            items = state.items,
            key = { it.id },
            focusMemory = viewModel.focusMemory,
            canLoadMore = state.canLoadMore,
            onLoadMore = viewModel::loadMore,
            modifier = modifier,
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(viewModel.title, style = MaterialTheme.typography.headlineMedium)
                    Button(
                        onClick = { onOpenVideo(state.items.first().id, viewModel.playlistId) },
                        modifier = Modifier.rememberFocus(viewModel.focusMemory, "play_all", isFirst = true),
                    ) {
                        Text(stringResource(R.string.playlist_play_all))
                    }
                }
            },
        ) { video, itemModifier ->
            VideoCard(video, onClick = { onOpenVideo(video.id, viewModel.playlistId) }, modifier = itemModifier)
        }
    }
}
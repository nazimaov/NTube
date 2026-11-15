package tv.lumen.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.component.ContentRow
import tv.lumen.core.designsystem.component.EmptyState
import tv.lumen.core.designsystem.component.ErrorState
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.theme.LumenDimens

@Composable
fun HomeRoute(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FocusMemoryScope(viewModel.focusMemory)
    when (val s = state) {
        HomeUiState.Loading -> LoadingState(modifier)
        is HomeUiState.Error -> ErrorState(s.error, onRetry = viewModel::load, modifier = modifier)
        is HomeUiState.Content -> if (s.trending.isEmpty()) {
            EmptyState(modifier)
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = LumenDimens.SafeVertical),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = LumenDimens.SafeHorizontal),
                    )
                }
                item {
                    ContentRow(
                        title = stringResource(R.string.home_trending),
                        items = s.trending,
                        key = { it.id },
                        focusMemory = viewModel.focusMemory,
                        rowKey = "trending",
                        isFirstRow = true,
                    ) { video, itemModifier ->
                        VideoCard(video = video, onClick = { onOpenVideo(video.id) }, modifier = itemModifier)
                    }
                }
            }
        }
    }
}
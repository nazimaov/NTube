package tv.lumen.feature.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import tv.lumen.core.designsystem.component.ChannelCard
import tv.lumen.core.designsystem.component.EmptyState
import tv.lumen.core.designsystem.component.ErrorState
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.PlaylistCard
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.Video
import tv.lumen.core.designsystem.component.VideoMenu

// FilterChip в tv-material 1.1.0 помечен как экспериментальный
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val submitted by viewModel.submitted.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    FocusMemoryScope(viewModel.focusMemory)

    fun submit(text: String) {
        keyboard?.hide()
        viewModel.submit(text)
    }

    Column(modifier = modifier.fillMaxSize().padding(top = LumenDimens.SafeVertical)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LumenDimens.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                onSearch = { submit(query) },
                modifier = Modifier.weight(1f).rememberFocus(viewModel.focusMemory, "field", isFirst = true),
            )
            VoiceButton(onResult = ::submit)
        }

        Row(
            modifier = Modifier.padding(horizontal = LumenDimens.SafeHorizontal, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchFilter.entries.forEach { f ->
                FilterChip(selected = f == filter, onClick = { viewModel.setFilter(f) }) {
                    Text(stringResource(f.label()))
                }
            }
        }

        val showSuggestions = suggestions.isNotEmpty() && query != submitted
        when {
            showSuggestions -> LazyColumn(
                modifier = Modifier.padding(horizontal = LumenDimens.SafeHorizontal),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(suggestions) { text ->
                    ListItem(
                        selected = false,
                        onClick = { submit(text) },
                        headlineContent = { Text(text) },
                        leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                    )
                }
            }
            results == null -> Unit
            else -> {
                val r = results!!
                when {
                    r.isLoading -> LoadingState()
                    r.error != null -> ErrorState(r.error!!, onRetry = viewModel::retry)
                    r.items.isEmpty() -> EmptyState()
                    else -> PagedGrid(
                        items = r.items,
                        key = SearchViewModel::itemKey,
                        focusMemory = viewModel.focusMemory,
                        canLoadMore = r.canLoadMore,
                        onLoadMore = viewModel::loadMore,
                    ) { item, itemModifier ->
                        when (item) {
                            is SearchItem.VideoItem -> VideoCard(
                                item.video,
                                onClick = { onOpenVideo(item.video.id) },
                                onLongClick = { menuFor = item.video },
                                progress = progress[item.video.id],
                                modifier = itemModifier,
                            )
                            is SearchItem.ChannelItem -> ChannelCard(item.channel, onClick = { onOpenChannel(item.channel.id) }, modifier = itemModifier)
                            is SearchItem.PlaylistItem -> PlaylistCard(
                                item.playlist,
                                onClick = { onOpenPlaylist(item.playlist.id, item.playlist.title) },
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { video ->
        SearchMenu(video, subscribed, progress, viewModel, onOpenChannel) { menuFor = null }
    }
}

@Composable
private fun SearchMenu(
    video: Video,
    subscribed: Set<String>,
    progress: Map<String, Float>,
    viewModel: SearchViewModel,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    VideoMenu(
        video = video,
        isSubscribed = video.channelId in subscribed,
        onDismiss = onDismiss,
        onOpenChannel = { video.channelId?.let(onOpenChannel) },
        onToggleSubscribe = { viewModel.actions.toggleSubscribe(video) },
        onHideChannel = { viewModel.actions.hideChannel(video) },
        onRemoveFromHistory = if (progress.containsKey(video.id)) ({ viewModel.actions.removeFromHistory(video) }) else null,
    )
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.border.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .border(BorderStroke(if (focused) 3.dp else 1.dp, border), RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

/** Голосовой ввод через RecognizerIntent; если распознавателя нет — кнопка скрыта (SPEC 8). */
@Composable
private fun VoiceButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val available = remember { intent.resolveActivity(context.packageManager) != null }
    if (!available) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onResult)
    }
    Button(onClick = { runCatching { launcher.launch(intent) } }) {
        Text(stringResource(R.string.search_voice))
    }
}

private fun SearchFilter.label(): Int = when (this) {
    SearchFilter.ALL -> R.string.search_filter_all
    SearchFilter.VIDEOS -> R.string.search_filter_videos
    SearchFilter.CHANNELS -> R.string.search_filter_channels
    SearchFilter.PLAYLISTS -> R.string.search_filter_playlists
}
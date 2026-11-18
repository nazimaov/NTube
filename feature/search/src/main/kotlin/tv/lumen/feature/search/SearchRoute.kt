package tv.lumen.feature.search

import androidx.annotation.StringRes
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import tv.lumen.core.designsystem.theme.LumenColors
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import tv.lumen.core.designsystem.component.LumenChip
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

@Composable
fun SearchRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) = SearchScreen(viewModel, SearchFilter.general, R.string.search_hint, onOpenVideo, onOpenChannel, onOpenPlaylist, modifier)

/**
 * «Музыка»: поиск YouTube Music; пока ничего не искали — язык музыки, плитки настроений и жанров, круглые
 * аватары исполнителей и под ними чарты региона (или песни выбранного настроения).
 */
@Composable
fun MusicRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val charts by viewModel.charts.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val moodSongs by viewModel.moodSongs.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    val uiLanguage = LocalConfiguration.current.locales[0].language
    LaunchedEffect(uiLanguage) { viewModel.setUiLanguage(uiLanguage) }

    SearchScreen(viewModel, SearchFilter.music, R.string.music_hint, onOpenVideo, onOpenChannel, onOpenPlaylist, modifier, fieldFirst = false) {
        val header: @Composable () -> Unit = { MusicHeader(viewModel, mood, onOpenChannel) }
        val card: @Composable (Video, Modifier) -> Unit = { video, itemModifier ->
            VideoCard(video, onClick = { onOpenVideo(video.id) }, onLongClick = { menuFor = video }, progress = progress[video.id], modifier = itemModifier)
        }
        // Одна и та же сетка во всех состояниях: загрузка и ошибка идут под шапкой, шапка не пересоздаётся
        val chosen = mood
        val state = charts
        val items: List<Video> = when {
            chosen != null -> moodSongs.items
            state is MusicChartsState.Content -> state.videos
            else -> emptyList()
        }
        val footer: (@Composable () -> Unit)? = when {
            chosen != null && moodSongs.isLoading -> ({ LoadingState() })
            chosen != null && moodSongs.error != null -> ({ ErrorState(moodSongs.error!!, onRetry = viewModel::retryMood) })
            chosen == null && state is MusicChartsState.Loading -> ({ LoadingState() })
            chosen == null && state is MusicChartsState.Error -> ({ ErrorState(state.error, onRetry = viewModel::loadCharts) })
            else -> null
        }
        PagedGrid(
            items = items,
            key = { it.id },
            focusMemory = viewModel.focusMemory,
            canLoadMore = chosen != null && moodSongs.canLoadMore,
            onLoadMore = viewModel::loadMoreMood,
            header = header,
            footer = footer,
        ) { video, itemModifier -> card(video, itemModifier) }
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

/** Шапка «Музыки»: язык, настроения и жанры, исполнители, заголовок списка ниже. */
@Composable
private fun MusicHeader(viewModel: MusicViewModel, mood: Mood?, onOpenChannel: (String) -> Unit) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.music_moods), style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusRestorer()) {
            itemsIndexed(MusicBrowse.moods, key = { _, m -> m.id }) { index, m ->
                MoodTile(
                    m, selected = m == mood, onClick = { viewModel.setMood(m) },
                    modifier = Modifier.rememberFocus(viewModel.focusMemory, "mood:" + m.id, isFirst = index == 0),
                )
            }
        }
        if (artists.isNotEmpty()) {
            Text(stringResource(R.string.music_artists), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.focusRestorer()) {
                items(artists, key = { it.id }) { a ->
                    ChannelCard(a, onClick = { onOpenChannel(a.id) }, modifier = Modifier.width(132.dp))
                }
            }
        }
        Text(
            if (mood == null) stringResource(R.string.music_charts) else stringResource(mood.label),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Цветная плитка настроения или жанра, как в Spotify: градиент и название; выбранная — с белой рамкой. */
@Composable
private fun MoodTile(mood: Mood, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, Color.White), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, LumenColors.FocusRing), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = modifier.width(188.dp).height(96.dp),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(mood.from, mood.to))).padding(14.dp)) {
            Text(
                stringResource(mood.label),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun SearchScreen(
    viewModel: BaseSearchViewModel,
    filters: List<SearchFilter>,
    @StringRes hint: Int,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Поле поиска получает фокус первым (в «Музыке» фокус на плитках настроений, чтобы не вылезала клавиатура). */
    fieldFirst: Boolean = true,
    idleContent: @Composable () -> Unit = {},
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
            modifier = Modifier.fillMaxWidth().padding(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                hint = hint,
                value = query,
                onValueChange = viewModel::onQueryChange,
                onSearch = { submit(query) },
                modifier = Modifier.weight(1f).rememberFocus(viewModel.focusMemory, "field", isFirst = fieldFirst),
            )
            VoiceButton(onResult = ::submit)
        }

        Row(
            modifier = Modifier.padding(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { f ->
                LumenChip(text = stringResource(f.label()), selected = f == filter, onClick = { viewModel.setFilter(f) })
            }
        }

        val showSuggestions = suggestions.isNotEmpty() && query != submitted
        when {
            showSuggestions -> LazyColumn(
                modifier = Modifier.padding(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal),
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
            results == null -> idleContent()
            else -> {
                val r = results!!
                when {
                    r.isLoading -> LoadingState()
                    r.error != null -> ErrorState(r.error!!, onRetry = viewModel::retry)
                    r.items.isEmpty() -> EmptyState()
                    else -> PagedGrid(
                        items = r.items,
                        key = ::searchItemKey,
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
    viewModel: BaseSearchViewModel,
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
private fun SearchField(@StringRes hint: Int, value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.border.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .border(BorderStroke(if (focused) 3.dp else 1.dp, border), RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(stringResource(hint), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    SearchFilter.MUSIC_SONGS -> R.string.music_filter_songs
    SearchFilter.MUSIC_VIDEOS -> R.string.music_filter_videos
    SearchFilter.MUSIC_ALBUMS -> R.string.music_filter_albums
    SearchFilter.MUSIC_ARTISTS -> R.string.music_filter_artists
    SearchFilter.MUSIC_PLAYLISTS -> R.string.music_filter_playlists
}
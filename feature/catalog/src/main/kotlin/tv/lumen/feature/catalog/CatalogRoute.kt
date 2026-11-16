package tv.lumen.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
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
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.Video
import java.time.Year

/** «Кино»: чипы «Фильмы / Сериалы» сверху, под ними подборки, жанр и год выбранного вида. */
@Composable
fun CinemaRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    movies: MoviesViewModel = hiltViewModel(),
    series: SeriesViewModel = hiltViewModel(),
) {
    var kind by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(CatalogKind.MOVIES) }
    CatalogScreen(
        kind = kind,
        onKindChange = { kind = it },
        viewModel = if (kind == CatalogKind.MOVIES) movies else series,
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        modifier = modifier,
    )
}

/** Открытый диалог выбора: жанр или год. */
private enum class FilterDialog { GENRE, YEAR }

@Composable
private fun CatalogScreen(
    kind: CatalogKind,
    onKindChange: (CatalogKind) -> Unit,
    viewModel: CatalogViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier,
) {
    val categories = remember(kind) { Categories.of(kind) }
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val dub by viewModel.dub.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    var dialog by remember { mutableStateOf<FilterDialog?>(null) }
    // Выбранная подборка; пока ничего не выбирали — первая
    var chosen by remember(kind) { mutableStateOf(categories.firstOrNull { it.id == selectedId } ?: categories.first()) }
    FocusMemoryScope(viewModel.focusMemory)

    val uiLang = when (LocalConfiguration.current.locales[0].language) {
        "ru" -> QueryLang.RU
        "tr" -> QueryLang.TR
        "az" -> QueryLang.AZ
        else -> QueryLang.EN
    }
    // Новый запрос — при смене подборки, фильтров, языка из настроек и языка интерфейса
    LaunchedEffect(chosen.id, filters, dub, uiLang) { viewModel.show(chosen, filters, dub, uiLang) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = LumenDimens.ContentStart, top = LumenDimens.SafeVertical),
        ) {
            LumenChip(text = stringResource(R.string.catalog_movies_title), selected = kind == CatalogKind.MOVIES, onClick = { onKindChange(CatalogKind.MOVIES) })
            LumenChip(text = stringResource(R.string.catalog_series_title), selected = kind == CatalogKind.SERIES, onClick = { onKindChange(CatalogKind.SERIES) })
        }
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
        // Фильтры: подсвечены, если выбрано не «Любой»; у трейлеров фильтров нет
        if (!chosen.trailers) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = LumenDimens.ContentStart, top = 8.dp, bottom = 4.dp),
            ) {
                LumenChip(
                    text = stringResource(R.string.filter_value, stringResource(R.string.filter_genre), stringResource(filters.genre.label)),
                    selected = filters.genre != Genre.ANY,
                    onClick = { dialog = FilterDialog.GENRE },
                )
                LumenChip(
                    text = stringResource(
                        R.string.filter_value,
                        stringResource(R.string.filter_year),
                        filters.year?.toString() ?: stringResource(R.string.filter_any),
                    ),
                    selected = filters.year != null,
                    onClick = { dialog = FilterDialog.YEAR },
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

    when (dialog) {
        FilterDialog.GENRE -> ChoiceDialog(
            title = stringResource(R.string.filter_genre),
            options = Genre.entries.map { stringResource(it.label) to it },
            selected = filters.genre,
            onSelect = viewModel::setGenre,
            onDismiss = { dialog = null },
        )
        FilterDialog.YEAR -> {
            val now = Year.now().value
            ChoiceDialog(
                title = stringResource(R.string.filter_year),
                options = listOf(stringResource(R.string.filter_any) to null) + (now downTo now - 9).map { it.toString() to it },
                selected = filters.year,
                onSelect = viewModel::setYear,
                onDismiss = { dialog = null },
            )
        }
        null -> Unit
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

/** Список вариантов фильтра; фокус сразу на выбранном. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(420.dp).background(LumenColors.Surface, RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                itemsIndexed(options) { index, (label, value) ->
                    ListItem(
                        selected = value == selected,
                        onClick = {
                            onDismiss()
                            onSelect(value)
                        },
                        headlineContent = { Text(label) },
                        modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
    }
}

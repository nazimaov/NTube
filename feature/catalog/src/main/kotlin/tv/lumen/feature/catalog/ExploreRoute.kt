package tv.lumen.feature.catalog

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.component.EmptyState
import tv.lumen.core.designsystem.component.ErrorState
import tv.lumen.core.designsystem.component.LoadingState
import tv.lumen.core.designsystem.component.LumenChip
import tv.lumen.core.designsystem.component.PagedGrid
import tv.lumen.core.designsystem.component.VideoCard
import tv.lumen.core.designsystem.component.VideoMenu
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.model.Video

private val TileShape = RoundedCornerShape(16.dp)

/** «Обзор»: плитки тем (первой — «Атмосфера»); выбранная тема открывается с чипами подтем и сеткой видео. */
@Composable
fun ExploreRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    var topicId by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = Topics.byId(topicId)
    if (topic == null) {
        TopicTiles(viewModel, onOpen = { topicId = it.id }, modifier)
    } else {
        BackHandler { topicId = null }
        TopicScreen(topic = topic, viewModel = viewModel, onBack = { topicId = null }, onOpenVideo = onOpenVideo, onOpenChannel = onOpenChannel, modifier = modifier)
    }
}

@Composable
private fun TopicTiles(viewModel: ExploreViewModel, onOpen: (Topic) -> Unit, modifier: Modifier) {
    FocusMemoryScope(viewModel.focusMemory)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal, top = LumenDimens.SafeVertical, bottom = LumenDimens.SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxSize().focusRestorer(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineMedium)
        }
        items(Topics.all, key = { it.id }, span = { GridItemSpan(if (it.ambient) maxLineSpan else 1) }) { topic ->
            TopicTile(
                topic = topic,
                onClick = { onOpen(topic) },
                modifier = Modifier.rememberFocus(viewModel.focusMemory, topic.id, isFirst = topic == Topics.all.first()),
            )
        }
    }
}

@Composable
private fun TopicTile(topic: Topic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LumenColors.Surface,
            contentColor = LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.SurfaceFocused,
            pressedContentColor = LumenColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, LumenColors.FocusRing), shape = TileShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(topic.emoji, fontSize = 36.sp, color = Color.Unspecified)
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(topic.title), style = MaterialTheme.typography.titleMedium, color = Color.Unspecified, maxLines = 2)
                Text(
                    stringResource(topic.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TopicScreen(
    topic: Topic,
    showBack: Boolean = true,
    onWatched: (Video, String?) -> Unit = { _, _ -> },
    viewModel: ExploreViewModel,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier,
) {
    var subId by rememberSaveable(topic.id) { mutableStateOf<String?>(null) }
    val sub = topic.subtopics.firstOrNull { it.id == subId }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val subscribed by viewModel.actions.subscribedIds.collectAsStateWithLifecycle()
    val progress by viewModel.actions.progress.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Video?>(null) }
    FocusMemoryScope(viewModel.focusMemory)

    val region by viewModel.region.collectAsStateWithLifecycle()
    // Язык подборок — по региону или по языку интерфейса (настройка), для всех тем одинаково
    val uiLang = when (tv.lumen.core.datastore.ContentLang.code(region.first, region.second, LocalConfiguration.current.locales[0].language, dubbed = topic.id == "kids")) {
        "ru" -> QueryLang.RU
        "tr" -> QueryLang.TR
        "az" -> QueryLang.AZ
        else -> QueryLang.EN
    }
    // Язык эфира выбирается только в «Прямом эфире»; «Авто» — язык интерфейса
    var langName by rememberSaveable { mutableStateOf<String?>(null) }
    var liveOn by rememberSaveable(topic.id) { mutableStateOf(false) }
    val live = liveOn && topic.live != null
    val lang = if (live) langName?.let { QueryLang.valueOf(it) } ?: uiLang else uiLang
    LaunchedEffect(topic.id, subId, lang, live) { viewModel.show(topic, sub, lang, live) }

    // Вверх из сетки видео фокус идёт на выбранный чип подтемы, а не перескакивает на «Все темы»
    val chipsFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = LumenDimens.ContentStart, top = LumenDimens.SafeVertical),
        ) {
            if (showBack) LumenChip(text = "← " + stringResource(R.string.explore_back), selected = false, onClick = onBack)
            Text(
                "${topic.emoji} ${stringResource(topic.title)}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        // Режим: записи или прямой эфир (только у тем с эфирами)
        if (topic.live != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = LumenDimens.ContentStart, top = 12.dp),
            ) {
                LumenChip(text = stringResource(R.string.explore_mode_videos), selected = !liveOn, onClick = { liveOn = false })
                LumenChip(text = "● " + stringResource(R.string.explore_live), selected = liveOn, onClick = { liveOn = true })
            }
        }
        LazyRow(
            contentPadding = PaddingValues(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            item(key = "all") {
                LumenChip(
                    text = stringResource(if (topic.id == "kids") R.string.kids_for_you else R.string.explore_all), selected = sub == null, onClick = { subId = null },
                    modifier = if (sub == null) Modifier.focusRequester(chipsFocus) else Modifier,
                )
            }
            items(topic.subtopics, key = { it.id }) { s ->
                LumenChip(
                    text = stringResource(s.label), selected = s.id == subId, onClick = { subId = s.id },
                    modifier = if (s.id == subId) Modifier.focusRequester(chipsFocus) else Modifier,
                )
            }
        }
        if (live) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = LumenDimens.ContentStart, top = 8.dp, bottom = 4.dp),
            ) {
                Text(stringResource(R.string.explore_live_language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 4.dp, top = 10.dp))
                LumenChip(text = stringResource(R.string.explore_lang_auto), selected = langName == null, onClick = { langName = null })
                for ((l, label) in listOf(
                    QueryLang.RU to R.string.explore_lang_ru, QueryLang.TR to R.string.explore_lang_tr,
                    QueryLang.AZ to R.string.explore_lang_az, QueryLang.EN to R.string.explore_lang_en,
                )) LumenChip(text = stringResource(label), selected = langName == l.name, onClick = { langName = l.name })
            }
        }
        when {
            results.isLoading -> LoadingState()
            results.error != null -> ErrorState(results.error!!, onRetry = viewModel::retry)
            results.items.isEmpty() && !results.canLoadMore -> if (live) EmptyState(stringResource(R.string.explore_live_empty)) else EmptyState()
            else -> PagedGrid(
                items = results.items,
                key = { it.id },
                focusMemory = viewModel.focusMemory,
                canLoadMore = results.canLoadMore,
                onLoadMore = viewModel::loadMore,
            ) { video, itemModifier ->
                VideoCard(
                    video,
                    onClick = { onWatched(video, subId); onOpenVideo(video.id) },
                    onLongClick = { menuFor = video },
                    progress = progress[video.id],
                    // Первая строка сетки: Вверх идёт на выбранный чип подтемы (в эфире — на ряд языков), а не на «Все темы»
                    modifier = if (!live && results.items.indexOf(video) < 3) itemModifier.focusProperties { up = chipsFocus } else itemModifier,
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

/** «Детское»: отдельный раздел меню — «Для вас» (по тому, что ребёнок уже смотрел, а сначала — популярное детское), мультики, обучение, песни, танцы, сказки. */
@Composable
fun KidsRoute(
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    TopicScreen(
        topic = Topics.kids,
        showBack = false,
        onWatched = viewModel::markKidsWatched,
        viewModel = viewModel,
        onBack = {},
        onOpenVideo = onOpenVideo,
        onOpenChannel = onOpenChannel,
        modifier = modifier,
    )
}

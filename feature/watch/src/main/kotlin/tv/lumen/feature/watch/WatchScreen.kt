package tv.lumen.feature.watch

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusRestorer
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.designsystem.component.CardWidth
import tv.lumen.core.designsystem.component.VideoCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import tv.lumen.core.designsystem.component.PlayerIconButton
import tv.lumen.core.designsystem.format.formatCompact
import tv.lumen.core.model.Reaction
import tv.lumen.core.model.Video
import tv.lumen.core.model.PlaybackOrder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import tv.lumen.core.designsystem.component.LumenIcons
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.player.PlayerController
import tv.lumen.player.PlayerNotice
import tv.lumen.player.PlayerState

private const val PANEL_HIDE_MS = 5_000L
private const val FAST_SEEK_MS = 30_000L

internal enum class WatchDialog { QUALITY, SPEED, SUBTITLES, AUDIO, COMMENTS }

private enum class PanelFocus { PLAY, SEEK, RELATED }

@Composable
fun WatchRoute(
    onBack: () -> Unit,
    /** videoId, playlistId */
    onOpenVideo: (String, String?) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dislikes by viewModel.dislikes.collectAsStateWithLifecycle()
    val reaction by viewModel.reaction.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val matchFrameRate by viewModel.matchFrameRate.collectAsStateWithLifecycle()
    FrameRateMatcher(enabled = matchFrameRate, fps = state.quality?.fps?.takeIf { !state.isLive })
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    WatchScreen(
        recommendations = recommendations,
        onLoadRecommendations = viewModel::loadRecommendations,
        dislikes = dislikes,
        queue = queue,
        onQueueAdd = { viewModel.queueAdd(it) },
        onQueueRemove = viewModel::queueRemove,
        onQueueMove = viewModel::queueMove,
        onQueueClear = viewModel::queueClear,
        reaction = reaction,
        onReact = viewModel::toggleReaction,
        comments = comments,
        onOpenComments = viewModel::openComments,
        onLoadMoreComments = viewModel::loadMoreComments,
        state = state,
        controller = viewModel.controller,
        onBack = onBack,
        onOpenVideo = { id -> onOpenVideo(id, null) },
        onPlayNext = { id -> onOpenVideo(id, viewModel.playlistId) },
        onOpenChannel = onOpenChannel,
        onCheckUpdate = viewModel::checkForUpdates,
        modifier = modifier,
    )
}

@Composable
internal fun WatchScreen(
    state: PlayerState,
    controller: PlayerController,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    dislikes: Long? = null,
    reaction: Reaction? = null,
    onReact: (Reaction) -> Unit = {},
    comments: tv.lumen.core.common.PagedState<tv.lumen.core.model.Comment>? = null,
    queue: List<Video> = emptyList(),
    recommendations: List<Video> = emptyList(),
    onLoadRecommendations: () -> Unit = {},
    onQueueAdd: (Video) -> Unit = {},
    onQueueRemove: (String) -> Unit = {},
    onQueueMove: (String, Int) -> Unit = { _, _ -> },
    onQueueClear: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
) {
    var panelVisible by remember { mutableStateOf(false) }
    // Ряд «Похожие» внизу панели: открывается кнопкой Вниз (SPEC 5.5)
    var queueEdit by remember { mutableStateOf<Video?>(null) }
    var relatedVisible by remember { mutableStateOf(false) }
    // Второй Вниз: вместо «Похожих» показываются рекомендации
    var recsVisible by remember { mutableStateOf(false) }
    var buttonsFocused by remember { mutableStateOf(false) }
    val related = state.details?.related.orEmpty()
    var panelFocus by remember { mutableStateOf(PanelFocus.PLAY) }
    var dialog by remember { mutableStateOf<WatchDialog?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    // Каждый показ панели заново ставит фокус: элемент с фокусом мог исчезнуть (закрытый диалог)
    var focusRequest by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun showPanel(focus: PanelFocus) {
        panelFocus = focus
        relatedVisible = focus == PanelFocus.RELATED || (relatedVisible && panelVisible)
        panelVisible = true
        interaction++
        focusRequest++
    }

    // Back: закрыть диалог → скрыть панель → выйти из плеера (SPEC 5.5).
    // Основная обработка — в onKey; BackHandler — запасной путь, если фокуса нет нигде.
    BackHandler(enabled = dialog != null || panelVisible) {
        if (dialog != null) dialog = null else panelVisible = false
    }

    // Панель скрывается через 5 с без нажатий во время воспроизведения
    LaunchedEffect(panelVisible, interaction, state.isPlaying, dialog) {
        if (panelVisible && state.isPlaying && dialog == null) {
            delay(PANEL_HIDE_MS)
            panelVisible = false
            relatedVisible = false
            recsVisible = false
        }
    }
    LaunchedEffect(panelVisible, dialog) {
        if (!panelVisible && dialog == null) rootFocus.requestFocus()
    }
    // Ошибка или пауза в конце — показать панель с кнопками
    LaunchedEffect(state.isEnded) { if (state.isEnded) showPanel(PanelFocus.PLAY) }

    fun onKey(event: KeyEvent): Boolean {
        // Back перехватываем здесь: иначе Compose на ТВ снимает фокус с элемента и событие не доходит
        // до BackHandler — панель остаётся без фокуса, пульт перестаёт работать.
        if (event.key == Key.Back) {
            if (event.type == KeyEventType.KeyUp) {
                when {
                    dialog != null -> {
                        dialog = null
                        showPanel(PanelFocus.PLAY)
                    }
                    panelVisible -> {
                        panelVisible = false
                        relatedVisible = false
                        recsVisible = false
                    }
                    else -> onBack() // выход из плеера
                }
            }
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false
        interaction++
        // SponsorBlock: OK при скрытой панели — «Вернуть» пропущенное или «Пропустить» сегмент (SPEC 6.1)
        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (isOk && !panelVisible && dialog == null) {
            if (state.skippedSegment != null) { controller.undoSkip(); return true }
            if (state.activeSegment != null) { controller.skipActiveSegment(); return true }
        }
        when (event.key) {
            Key.MediaPlayPause -> { controller.togglePlayPause(); showPanel(PanelFocus.PLAY); return true }
            Key.MediaPlay -> { controller.play(); return true }
            Key.MediaPause -> { controller.pause(); showPanel(PanelFocus.PLAY); return true }
            Key.MediaFastForward -> { controller.seekBy(FAST_SEEK_MS); return true }
            Key.MediaRewind -> { controller.seekBy(-FAST_SEEK_MS); return true }
        }
        // Вниз с ряда кнопок открывает «Похожие»
        if (panelVisible && dialog == null && !relatedVisible && buttonsFocused && event.key == Key.DirectionDown && (related.isNotEmpty() || queue.isNotEmpty())) {
            showPanel(PanelFocus.RELATED)
            return true
        }
        // Вниз во втором ряду: если ниже плиток нет, открываются рекомендации
        if (panelVisible && dialog == null && relatedVisible && !recsVisible && event.key == Key.DirectionDown) {
            val moved = queue.isNotEmpty() && related.isNotEmpty() && focusManager.moveFocus(FocusDirection.Down)
            if (!moved) {
                onLoadRecommendations()
                recsVisible = true
                focusRequest++
            }
            return true
        }
        // Вверх из рекомендаций — обратно к «Похожим»
        if (panelVisible && dialog == null && recsVisible && event.key == Key.DirectionUp) {
            recsVisible = false
            focusRequest++
            return true
        }
        // Вверх из «Похожих»: с одним рядом плиток сразу возвращает интерфейс; с двумя сначала идём между рядами
        if (panelVisible && dialog == null && relatedVisible && event.key == Key.DirectionUp) {
            val moved = queue.isNotEmpty() && related.isNotEmpty() && focusManager.moveFocus(FocusDirection.Up)
            if (!moved) { relatedVisible = false; showPanel(PanelFocus.PLAY) }
            return true
        }
        if (dialog != null || panelVisible || state.error != null) return false
        return when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                controller.togglePlayPause(); showPanel(PanelFocus.PLAY); true
            }
            Key.DirectionLeft -> { controller.seekBy(-seekStep(event)); showPanel(PanelFocus.SEEK); true }
            Key.DirectionRight -> { controller.seekBy(seekStep(event)); showPanel(PanelFocus.SEEK); true }
            Key.DirectionUp -> { showPanel(PanelFocus.PLAY); true }
            Key.DirectionDown -> { showPanel(if (related.isNotEmpty()) PanelFocus.RELATED else PanelFocus.PLAY); true }
            else -> false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent(::onKey)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        VideoSurface(controller = controller, keepScreenOn = state.isPlaying || state.isBuffering)

        if (state.isLoading || (state.isBuffering && !panelVisible)) {
            tv.lumen.core.designsystem.component.LumenLoader(Modifier.align(Alignment.Center))
        }

        state.notice?.let { NoticeBanner(it, Modifier.align(Alignment.TopCenter)) }

        // Плашки SponsorBlock: «Пропущено: спонсор · OK — вернуть», «Пропустить: … · OK»
        val sponsorText = state.skippedSegment?.let { stringResource(R.string.watch_sb_skipped, categoryName(it.category)) }
            ?: state.activeSegment?.let { stringResource(R.string.watch_sb_skip, categoryName(it.category)) }
        if (sponsorText != null && dialog == null) {
            Text(
                text = sponsorText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = LumenDimens.SafeHorizontal, vertical = LumenDimens.SafeVertical)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        // Как в YouTube: название сверху, под ним просмотры и дата, ниже канал
        AnimatedVisibility(
            // Когда открыт список похожих, панель занимает весь экран и название показывается в ней самой
            visible = panelVisible && state.error == null && state.details != null && !relatedVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            state.details?.let { VideoTitleBar(it.video) }
        }

        AnimatedVisibility(
            visible = panelVisible && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlPanel(
                state = state,
                controller = controller,
                initialFocus = panelFocus,
                focusRequest = focusRequest,
                relatedVisible = relatedVisible,
                recsVisible = recsVisible,
                recommendations = recommendations,
                onButtonsFocusChanged = { buttonsFocused = it },
                onInteraction = { interaction++ },
                onOpenDialog = { if (it == WatchDialog.COMMENTS) onOpenComments(); dialog = it },
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                dislikes = dislikes,
                reaction = reaction,
                onReact = onReact,
                queue = queue,
                onQueueEdit = { queueEdit = it },
                onAddToQueue = onQueueAdd,
            )
        }

        queueEdit?.let { edited ->
            val index = queue.indexOfFirst { it.id == edited.id }
            if (index >= 0) {
                QueueItemDialog(
                    video = edited,
                    index = index,
                    size = queue.size,
                    onPlay = { onOpenVideo(edited.id) },
                    onMove = { onQueueMove(edited.id, it) },
                    onRemove = { onQueueRemove(edited.id) },
                    onClear = onQueueClear,
                    onDismiss = { queueEdit = null; showPanel(PanelFocus.RELATED) },
                )
            } else {
                queueEdit = null
            }
        }

        state.nextUp?.let { next ->
            NextUpOverlay(
                title = next.title,
                onPlay = { onPlayNext(next.id) },
                onCancel = controller::cancelNextUp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        state.error?.let { error ->
            ErrorOverlay(error = error, onRetry = controller::retry, onCheckUpdate = onCheckUpdate, modifier = Modifier.align(Alignment.Center))
        }

        if (dialog == WatchDialog.COMMENTS) {
            CommentsPanel(comments, onLoadMoreComments, Modifier.align(Alignment.CenterEnd))
        } else dialog?.let { current ->
            OptionsDialog(
                dialog = current,
                state = state,
                controller = controller,
                onDismiss = { dialog = null; showPanel(PanelFocus.PLAY) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Шаг перемотки: 10 с, при удержании растёт до 60 с (SPEC 5.5). */
private fun seekStep(event: KeyEvent): Long {
    val repeat = event.nativeKeyEvent.repeatCount
    return (10_000L * (1 + repeat / 5)).coerceAtMost(60_000L)
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controller: PlayerController, keepScreenOn: Boolean) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false // своя панель на Compose (SPEC 5)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setShutterBackgroundColor(AndroidColor.BLACK)
                setKeepContentOnPlayerReset(true)
                player = controller.player
            }
        },
        update = { view -> view.keepScreenOn = keepScreenOn },
        onRelease = { view -> view.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Верхняя плашка плеера: название, под ним просмотры и дата, ниже канал. */
@Composable
private fun VideoTitleBar(video: Video) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = LumenDimens.SafeHorizontal, vertical = LumenDimens.SafeVertical),
    ) { VideoTitleText(video) }
}

@Composable
private fun VideoTitleText(video: Video) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val views = tv.lumen.core.designsystem.format.videoMeta(video)
        if (views.isNotBlank()) {
            Text(views, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
        }
        video.channelName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ControlPanel(
    state: PlayerState,
    controller: PlayerController,
    initialFocus: PanelFocus,
    focusRequest: Int,
    relatedVisible: Boolean,
    recsVisible: Boolean,
    recommendations: List<Video>,
    onButtonsFocusChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onOpenDialog: (WatchDialog) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    dislikes: Long?,
    reaction: Reaction?,
    onReact: (Reaction) -> Unit,
    queue: List<Video>,
    onQueueEdit: (Video) -> Unit,
    onAddToQueue: (Video) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val relatedFocus = remember { FocusRequester() }
    val recsFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequest, relatedVisible, recsVisible, recommendations.size) {
        runCatching {
            when (initialFocus) {
                PanelFocus.SEEK -> seekFocus.requestFocus()
                PanelFocus.RELATED -> when {
                    relatedVisible && recsVisible && recommendations.isNotEmpty() -> recsFocus.requestFocus()
                    relatedVisible -> relatedFocus.requestFocus()
                    else -> playFocus.requestFocus()
                }
                PanelFocus.PLAY -> playFocus.requestFocus()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (relatedVisible) 0.92f else 0.85f))))
            .padding(horizontal = LumenDimens.SafeHorizontal, vertical = LumenDimens.SafeVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Со списком «Похожие» интерфейс (кнопки, полоса) прячется, остаются только плитки; вверх — плавно возвращается
        AnimatedVisibility(visible = !relatedVisible, enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isLive) {
                Text(
                    text = stringResource(R.string.watch_live),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                SeekBar(state, controller, onInteraction, Modifier.focusRequester(seekFocus))
                // Слева — прошло и осталось, справа — длительность видео
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val remaining = (state.durationMs - state.positionMs).coerceAtLeast(0)
                    Text(
                        text = "${formatTime(state.positionMs)}  ·  " + stringResource(R.string.watch_time_left, formatTime(remaining)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        text = formatTime(state.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.onFocusChanged { onButtonsFocusChanged(it.hasFocus) },
            ) {
                PlayerIconButton(
                    icon = if (state.playWhenReady) LumenIcons.Pause else Icons.Filled.PlayArrow,
                    label = stringResource(if (state.playWhenReady) R.string.watch_pause else R.string.watch_play),
                    onClick = controller::togglePlayPause,
                    size = 60.dp,
                    modifier = Modifier.focusRequester(playFocus),
                )
                val likes = state.details?.likeCount?.takeIf { it >= 0 }
                PlayerIconButton(
                    icon = Icons.Filled.ThumbUp,
                    label = stringResource(R.string.watch_like),
                    caption = likes?.let { formatCompact(it + if (reaction == Reaction.LIKE) 1 else 0) },
                    onClick = { onReact(Reaction.LIKE) },
                    active = reaction == Reaction.LIKE,
                )
                PlayerIconButton(
                    icon = LumenIcons.ThumbDown,
                    label = stringResource(R.string.watch_dislike),
                    // Оценка RYD — со знаком «≈» (SPEC 6.3)
                    caption = dislikes?.let { "≈ " + formatCompact(it) },
                    onClick = { onReact(Reaction.DISLIKE) },
                    active = reaction == Reaction.DISLIKE,
                )
                if (!state.isLive) {
                    // Одна кнопка: по очереди → повтор → вперемешку
                    val order = state.playbackOrder
                    PlayerIconButton(
                        icon = when (order) {
                            PlaybackOrder.SEQUENTIAL -> LumenIcons.PlaylistPlay
                            PlaybackOrder.REPEAT_ONE -> LumenIcons.RepeatOne
                            PlaybackOrder.SHUFFLE -> LumenIcons.Shuffle
                        },
                        label = stringResource(
                            when (order) {
                                PlaybackOrder.SEQUENTIAL -> R.string.watch_order_sequential
                                PlaybackOrder.REPEAT_ONE -> R.string.watch_order_repeat
                                PlaybackOrder.SHUFFLE -> R.string.watch_order_shuffle
                            },
                        ),
                        onClick = controller::cycleOrder,
                        active = order != PlaybackOrder.SEQUENTIAL,
                    )
                }
                if (state.qualities.isNotEmpty()) {
                    PlayerIconButton(
                        icon = Icons.Filled.Settings,
                        label = "${stringResource(R.string.watch_quality)}: ${state.quality?.label.orEmpty()}",
                        onClick = { onOpenDialog(WatchDialog.QUALITY) },
                    )
                }
                if (!state.isLive) {
                    PlayerIconButton(
                        icon = LumenIcons.Speed,
                        label = "${stringResource(R.string.watch_speed)}: ${formatSpeed(state.speed)}",
                        onClick = { onOpenDialog(WatchDialog.SPEED) },
                        active = state.speed != 1f,
                    )
                }
                if (state.subtitles.isNotEmpty()) {
                    val current = state.subtitles.firstOrNull { it.languageTag == state.subtitle }?.name
                    PlayerIconButton(
                        icon = LumenIcons.Subtitles,
                        label = "${stringResource(R.string.watch_subtitles)}: ${current ?: stringResource(R.string.watch_subtitles_off)}",
                        onClick = { onOpenDialog(WatchDialog.SUBTITLES) },
                        active = current != null,
                    )
                }
                if (state.audioTracks.size > 1) {
                    PlayerIconButton(
                        icon = LumenIcons.MusicNote,
                        label = stringResource(R.string.watch_audio),
                        onClick = { onOpenDialog(WatchDialog.AUDIO) },
                    )
                }
                state.details?.video?.channelId?.let { channelId ->
                    PlayerIconButton(
                        icon = Icons.Filled.AccountCircle,
                        label = stringResource(R.string.watch_channel),
                        onClick = { onOpenChannel(channelId) },
                    )
                    PlayerIconButton(
                        icon = LumenIcons.Comment,
                        label = stringResource(R.string.watch_comments),
                        onClick = { onOpenDialog(WatchDialog.COMMENTS) },
                    )
                    PlayerIconButton(
                        icon = if (state.isSubscribed) Icons.Filled.Check else Icons.Filled.Add,
                        label = stringResource(if (state.isSubscribed) R.string.watch_subscribed else R.string.watch_subscribe),
                        onClick = controller::toggleSubscribe,
                        active = state.isSubscribed,
                    )
                }
            }
            }
        }

        val related = state.details?.related.orEmpty()
        // Ряды: сначала своя очередь (долгое нажатие — редактировать), ниже — похожие (долгое — в очередь)
        if (relatedVisible && !recsVisible && queue.isNotEmpty()) {
            Text(stringResource(R.string.watch_queue), style = MaterialTheme.typography.titleMedium, color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.focusRestorer()) {
                itemsIndexed(queue, key = { _, v -> "q:" + v.id }) { index, video ->
                    VideoCard(
                        video = video,
                        onClick = { onOpenVideo(video.id) },
                        onLongClick = { onQueueEdit(video) },
                        modifier = Modifier
                            .width(CardWidth)
                            .then(if (index == 0) Modifier.focusRequester(relatedFocus) else Modifier),
                    )
                }
            }
        }
        if (relatedVisible && !recsVisible && related.isNotEmpty()) {
            Text(stringResource(R.string.watch_related), style = MaterialTheme.typography.titleMedium, color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.focusRestorer()) {
                itemsIndexed(related, key = { _, v -> v.id }) { index, video ->
                    VideoCard(
                        video = video,
                        onClick = { onOpenVideo(video.id) },
                        onLongClick = {
                            if (!queue.any { it.id == video.id }) {
                                onAddToQueue(video)
                                android.widget.Toast.makeText(context, R.string.queue_added, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .width(CardWidth)
                            .then(if (index == 0 && queue.isEmpty()) Modifier.focusRequester(relatedFocus) else Modifier),
                    )
                }
            }
        }
        if (relatedVisible && recsVisible) {
            Text(stringResource(R.string.watch_recommendations), style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (recommendations.isEmpty()) {
                tv.lumen.core.designsystem.component.LoadingState(Modifier.fillMaxWidth().height(200.dp))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.focusRestorer()) {
                    itemsIndexed(recommendations, key = { _, v -> "r:" + v.id }) { index, video ->
                        VideoCard(
                            video = video,
                            onClick = { onOpenVideo(video.id) },
                            onLongClick = {
                                if (!queue.any { it.id == video.id }) {
                                    onAddToQueue(video)
                                    android.widget.Toast.makeText(context, R.string.queue_added, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.width(CardWidth).then(if (index == 0) Modifier.focusRequester(recsFocus) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/** Полоса прогресса: в фокусе Влево/Вправо перематывают, OK — пауза. */
@Composable
private fun SeekBar(state: PlayerState, controller: PlayerController, onInteraction: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1)
    val played = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (state.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val height = if (focused) 10.dp else 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onInteraction()
                when (event.key) {
                    Key.DirectionLeft -> { controller.seekBy(-seekStep(event)); true }
                    Key.DirectionRight -> { controller.seekBy(seekStep(event)); true }
                    Key.DirectionCenter, Key.Enter -> { controller.togglePlayPause(); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val shape = RoundedCornerShape(50)
        Box(Modifier.fillMaxWidth().height(height).clip(shape).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(buffered).height(height).clip(shape).background(Color.White.copy(alpha = 0.45f)))
        // Сегменты SponsorBlock цветом категории (SPEC 6.1)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(height)) {
            state.segments.forEach { s ->
                val x0 = size.width * (s.startMs.toFloat() / duration).coerceIn(0f, 1f)
                val x1 = size.width * (s.endMs.toFloat() / duration).coerceIn(0f, 1f)
                drawRect(color = categoryColor(s.category), topLeft = androidx.compose.ui.geometry.Offset(x0, 0f), size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(2f), size.height))
            }
        }
        Box(
            Modifier.fillMaxWidth(played).height(height).clip(shape)
                .background(LumenColors.Red),
        )
    }
}

@Composable
private fun NoticeBanner(notice: PlayerNotice, modifier: Modifier) {
    var visible by remember(notice) { mutableStateOf(true) }
    LaunchedEffect(notice) { delay(6_000); visible = false }
    if (!visible) return
    val text = when (notice) {
        PlayerNotice.LOW_QUALITY_ONLY -> stringResource(R.string.watch_low_quality_notice)
    }
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .padding(top = LumenDimens.SafeVertical)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 720.dp),
    )
}

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.1f" else "%.2f").format(speed).trimEnd('0').trimEnd('.', ',') + "×"

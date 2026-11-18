package tv.lumen.player.internal

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.PlaybackOrder
import tv.lumen.core.model.Video
import tv.lumen.core.model.VideoDetails
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.sponsorblock.SponsorBlockRepository
import tv.lumen.core.model.Segment
import tv.lumen.core.model.SegmentMode
import androidx.media3.exoplayer.PlayerMessage
import android.os.Looper
import tv.lumen.data.youtube.YouTubeRepository
import tv.lumen.player.MAX_SPEED
import tv.lumen.player.MIN_SPEED
import tv.lumen.player.PlayerController
import tv.lumen.player.PlayerNotice
import tv.lumen.player.PlayerPreferences
import tv.lumen.player.PlayerState
import tv.lumen.player.QualityOption
import java.util.UUID

/**
 * Реализация [PlayerController] на Media3 ExoPlayer (SPEC 5). Живёт столько же, сколько экран плеера:
 * [scope] — scope его ViewModel (главный поток).
 */
@OptIn(UnstableApi::class)
internal class ExoPlayerController(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: YouTubeRepository,
    private val sources: MediaSourceBuilder,
    private val selector: StreamSelector,
    private val prefs: PlayerPreferences,
    private val library: LibraryRepository,
    /** Переживает экран: последнее сохранение позиции после release(). */
    private val appScope: CoroutineScope,
    private val sponsorBlock: SponsorBlockRepository,
    /** Сохранить выбранный порядок в настройках. */
    private val saveOrder: (PlaybackOrder) -> Unit = {},
) : PlayerController {

    private val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { playWhenReady = true }

    // Медиакнопки пульта и голосовые команды (SPEC 5.6)
    private val session: MediaSession = MediaSession.Builder(context, exo)
        .setId("lumen-" + UUID.randomUUID())
        .build()

    private val _state = MutableStateFlow(PlayerState(speed = prefs.defaultSpeed, playbackOrder = prefs.playbackOrder))
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val player: Player get() = exo

    private var details: VideoDetails? = null
    private var requestedHeight: Int? = null
    private var subtitleLang: String? = prefs.subtitleLanguage
    private var audioTrackId: String? = null
    private var retries = 0
    private var loadJob: Job? = null
    private var tickerJob: Job? = null
    private var saveJob: Job? = null
    private var subscribedJob: Job? = null
    private var playlistId: String? = null

    // SponsorBlock (SPEC 6.1)
    private var segments: List<Segment> = emptyList()
    private val disabledSegments = HashSet<String>()
    private val segmentMessages = ArrayList<PlayerMessage>()
    private var segmentsJob: Job? = null
    private var noticeJob: Job? = null

    init {
        exo.setPlaybackSpeed(prefs.defaultSpeed)
        applyOrder(prefs.playbackOrder)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startTicker()
                    startSaving()
                } else {
                    savePosition() // пауза
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _state.update { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retries = 0
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isEnded = playbackState == Player.STATE_ENDED,
                    )
                }
                updatePosition()
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    override fun load(videoId: String, startPositionMs: Long, playlistId: String?) {
        details = null
        requestedHeight = null
        audioTrackId = null
        retries = 0
        this.playlistId = playlistId
        _state.value = PlayerState(videoId = videoId, isLoading = true, speed = exo.playbackParameters.speed)
        segments = emptyList()
        disabledSegments.clear()
        loadSegments(videoId) // параллельно с загрузкой видео (SPEC 6.1)
        fetchAndPrepare(videoId, startPositionMs, forceRefresh = false, resume = startPositionMs == 0L)
    }

    private fun loadSegments(videoId: String) {
        segmentsJob?.cancel()
        if (!prefs.sponsorBlockEnabled) return
        val categories = prefs.sponsorModes.filterValues { it != SegmentMode.OFF }.keys
        segmentsJob = scope.launch {
            segments = sponsorBlock.segments(videoId, categories)
            _state.update { it.copy(segments = segments) }
            scheduleSegments()
        }
    }

    /** Пропуск через PlayerMessage на позиции начала сегмента; опрос позиции по таймеру не используется. */
    private fun scheduleSegments() {
        segmentMessages.forEach { it.cancel() }
        segmentMessages.clear()
        if (exo.currentMediaItem == null) return
        for (segment in segments) {
            val mode = prefs.sponsorModes[segment.category] ?: SegmentMode.OFF
            if (mode == SegmentMode.OFF) continue
            segmentMessages += exo.createMessage { _, _ -> onSegmentStart(segment, mode) }
                .setLooper(Looper.getMainLooper())
                .setPosition(segment.startMs)
                .setDeleteAfterDelivery(false)
                .send()
            if (mode == SegmentMode.BUTTON) {
                segmentMessages += exo.createMessage { _, _ -> if (_state.value.activeSegment == segment) _state.update { it.copy(activeSegment = null) } }
                    .setLooper(Looper.getMainLooper())
                    .setPosition(segment.endMs)
                    .setDeleteAfterDelivery(false)
                    .send()
            }
        }
    }

    private fun onSegmentStart(segment: Segment, mode: SegmentMode) {
        if (segment.uuid in disabledSegments) return
        when (mode) {
            SegmentMode.SKIP -> {
                exo.seekTo(segment.endMs)
                showSkipped(segment)
            }
            SegmentMode.BUTTON -> _state.update { it.copy(activeSegment = segment) }
            SegmentMode.OFF -> Unit
        }
    }

    /** Плашка «Пропущено: …» на 3 с (SPEC 6.1). */
    private fun showSkipped(segment: Segment) {
        _state.update { it.copy(skippedSegment = segment) }
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(SKIPPED_NOTICE_MS)
            _state.update { if (it.skippedSegment == segment) it.copy(skippedSegment = null) else it }
        }
    }

    /** «Вернуть»: к началу сегмента и без пропуска до конца просмотра. */
    override fun undoSkip() {
        val segment = _state.value.skippedSegment ?: return
        disabledSegments += segment.uuid
        exo.seekTo(segment.startMs)
        _state.update { it.copy(skippedSegment = null) }
    }

    override fun skipActiveSegment() {
        val segment = _state.value.activeSegment ?: return
        exo.seekTo(segment.endMs)
        _state.update { it.copy(activeSegment = null) }
    }

    override fun rememberSpeedForChannel() {
        val channelId = details?.video?.channelId ?: return
        val speed = exo.playbackParameters.speed
        appScope.launch { library.setChannelSpeed(channelId, speed) }
    }

    private fun fetchAndPrepare(videoId: String, positionMs: Long, forceRefresh: Boolean, resume: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.videoDetails(videoId, forceRefresh)) {
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                is AppResult.Success -> {
                    details = result.value
                    observeSubscription(result.value)
                    // Скорость, сохранённая для канала (SPEC 6.4)
                    if (!forceRefresh) result.value.video.channelId?.let { library.channelSpeed(it) }?.let(::setSpeed)
                    // Продолжение с сохранённой позиции, если просмотрено 5–95% (SPEC 5.6)
                    val start = if (resume && !result.value.video.isLive) library.resumePosition(videoId) ?: 0 else positionMs
                    prepare(result.value, start)
                }
            }
        }
    }

    private fun observeSubscription(details: VideoDetails) {
        subscribedJob?.cancel()
        val channelId = details.video.channelId ?: return
        subscribedJob = scope.launch {
            library.isSubscribed(channelId).collect { subscribed -> _state.update { it.copy(isSubscribed = subscribed) } }
        }
    }

    override fun toggleSubscribe() {
        val video = details?.video ?: return
        val channelId = video.channelId ?: return
        val avatar = details?.channelAvatarUrl
        appScope.launch {
            if (_state.value.isSubscribed) library.unsubscribe(channelId)
            else library.subscribe(channelId, video.channelName.orEmpty(), avatar)
        }
    }

    /** Позиция в БД каждые 5 с во время воспроизведения (SPEC 5.6). */
    private fun startSaving() {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            while (isActive && exo.isPlaying) {
                delay(SAVE_EVERY_MS)
                savePosition()
            }
        }
    }

    private fun savePosition() {
        val video = details?.video ?: return
        if (video.isLive) return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val position = if (exo.playbackState == Player.STATE_ENDED) duration else exo.currentPosition
        appScope.launch { library.savePosition(video, position, duration) }
    }

    /**
     * Конец видео: по очереди — следующее из плейлиста или первое похожее (SPEC 5.6),
     * вперемешку — случайное из плейлиста или похожих. Повтор сюда не доходит: его делает ExoPlayer.
     */
    private fun onEnded() {
        savePosition()
        if (!prefs.autoplayNext) return
        val current = details ?: return
        val order = _state.value.playbackOrder
        scope.launch {
            // Своя очередь важнее: сначала играет она, и только потом «похожие» и плейлист
            val queued = if (order == PlaybackOrder.SHUFFLE) {
                library.queue.items.value.filter { it.id != current.video.id }.randomOrNull()
            } else {
                library.queue.nextAfter(current.video.id)
            }
            val next = queued ?: when (order) {
                PlaybackOrder.SHUFFLE -> PlaybackQueue.shuffleNext(
                    playlist = playlistId?.let { playlistItems(it) }.orEmpty(),
                    related = current.related,
                    currentId = current.video.id,
                )
                else -> playlistId?.let { nextInPlaylist(it, current.video.id) } ?: current.related.firstOrNull()
            }
            _state.update { it.copy(nextUp = next) }
        }
    }

    override fun cycleOrder() {
        val order = _state.value.playbackOrder.next()
        applyOrder(order)
        _state.update { it.copy(playbackOrder = order) }
        saveOrder(order)
    }

    /** Повтор одного видео — средствами ExoPlayer, без паузы между кругами. */
    private fun applyOrder(order: PlaybackOrder) {
        exo.repeatMode = if (order == PlaybackOrder.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Все видео плейлиста (до [MAX_PLAYLIST_PAGES] страниц) — для случайного выбора. */
    private suspend fun playlistItems(playlistId: String): List<Video> {
        val result = ArrayList<Video>()
        var token: tv.lumen.core.model.PageToken? = null
        repeat(MAX_PLAYLIST_PAGES) {
            val page = (repository.playlist(playlistId, token) as? AppResult.Success)?.value ?: return result
            result += page.items
            token = page.next ?: return result
        }
        return result
    }

    private suspend fun nextInPlaylist(playlistId: String, currentId: String): Video? {
        var token: tv.lumen.core.model.PageToken? = null
        var found = false
        repeat(MAX_PLAYLIST_PAGES) {
            val page = (repository.playlist(playlistId, token) as? AppResult.Success)?.value ?: return null
            for (v in page.items) {
                if (found) return v
                if (v.id == currentId) found = true
            }
            token = page.next ?: return null
        }
        return null
    }

    override fun cancelNextUp() {
        _state.update { it.copy(nextUp = null) }
    }

    /** Выбор потоков и сборка источника; позиция сохраняется (ручная смена качества, повтор). */
    private suspend fun prepare(details: VideoDetails, positionMs: Long) {
        val streams = details.streams
        val subtitle = selector.selectSubtitle(streams.subtitles, subtitleLang)
        val selection = selector.select(streams, prefs, requestedHeight, audioTrackId)

        val source = when (selection) {
            is Selection.Separate -> when (val r = sources.separate(details.video.id, selection.video, selection.audio, subtitle)) {
                is AppResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = r.error) }
                    return
                }
                is AppResult.Success -> r.value
            }
            is Selection.Muxed -> sources.muxed(selection.stream, subtitle)
            is Selection.Live -> sources.live(selection.hlsUrl, selection.dashMpdUrl)
            Selection.Unplayable -> {
                _state.update { it.copy(isLoading = false, error = AppError.ExtractionBroken) }
                return
            }
        }

        if (selection is Selection.Live) exo.setMediaSource(source) else exo.setMediaSource(source, positionMs)
        exo.prepare()
        exo.play()
        scheduleSegments() // новый источник — сообщения ставятся заново

        _state.update {
            it.copy(
                details = details,
                isLoading = false,
                isLive = streams.isLive,
                error = null,
                quality = when (selection) {
                    is Selection.Separate -> QualityOption(selection.video.height, selection.video.fps, selection.video.codec)
                    is Selection.Muxed -> QualityOption(selection.stream.height, selection.stream.fps, selection.stream.codec)
                    else -> null
                },
                qualities = (selection as? Selection.Separate)?.qualities.orEmpty(),
                subtitles = streams.subtitles,
                subtitle = subtitle?.languageTag,
                audioTracks = (selection as? Selection.Separate)?.audioTracks.orEmpty(),
                audioTrack = (selection as? Selection.Separate)?.audio?.trackId,
                notice = if ((selection as? Selection.Muxed)?.lowQualityNotice == true) PlayerNotice.LOW_QUALITY_ONLY else null,
            )
        }
    }

    /** 403/410/обрыв — до 2 повторов с перезапросом ссылок, затем экран ошибки (SPEC 4.4, 5.6). */
    private fun handleError(error: PlaybackException) {
        val videoId = _state.value.videoId ?: return
        val position = exo.currentPosition
        if (retries < MAX_RETRIES && error.isRecoverable()) {
            retries++
            fetchAndPrepare(videoId, position, forceRefresh = true)
        } else {
            _state.update { it.copy(isLoading = false, error = AppError.Network) }
        }
    }

    private fun PlaybackException.isRecoverable(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode == 403 || cause.responseCode == 410
            cause = cause.cause
        }
        return errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
    }

    override fun retry() {
        val videoId = _state.value.videoId ?: return
        retries = 0
        val position = exo.currentPosition.takeIf { details != null } ?: 0
        fetchAndPrepare(videoId, position, forceRefresh = true, resume = details == null)
    }

    override fun play() = exo.play()
    override fun pause() = exo.pause()

    override fun togglePlayPause() {
        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0)
            exo.play()
        } else if (exo.playWhenReady) {
            // Не isPlaying: во время буферизации оно ложно, и «Пауза» превращалась в бесполезный play()
            exo.pause()
        } else {
            exo.play()
        }
    }

    override fun seekTo(ms: Long) {
        val duration = exo.duration
        val target = if (duration != C.TIME_UNSET) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
        exo.seekTo(target)
        updatePosition()
    }

    override fun seekBy(deltaMs: Long) = seekTo(exo.currentPosition + deltaMs)

    override fun setSpeed(speed: Float) {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        exo.setPlaybackSpeed(s)
        _state.update { it.copy(speed = s) }
    }

    override fun setQuality(height: Int) {
        requestedHeight = height
        reprepare()
    }

    override fun setSubtitle(lang: String?) {
        subtitleLang = lang
        reprepare()
    }

    override fun setAudioTrack(id: String) {
        audioTrackId = id
        reprepare()
    }

    /** Ручная смена дорожки пересобирает источник и возвращает позицию (SPEC 5.3). */
    private fun reprepare() {
        val current = details ?: return
        val position = exo.currentPosition
        loadJob?.cancel()
        loadJob = scope.launch { prepare(current, position) }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                updatePosition()
                if (!exo.isPlaying) break
                delay(TICK_MS)
            }
        }
    }

    private fun updatePosition() {
        val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0
        _state.update {
            it.copy(positionMs = exo.currentPosition, durationMs = duration, bufferedMs = exo.bufferedPosition)
        }
    }

    override fun release() {
        savePosition() // при выходе из плеера (SPEC 5.6)
        loadJob?.cancel()
        tickerJob?.cancel()
        saveJob?.cancel()
        subscribedJob?.cancel()
        segmentsJob?.cancel()
        noticeJob?.cancel()
        session.release()
        exo.release()
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val TICK_MS = 500L
        const val SAVE_EVERY_MS = 5_000L
        const val SKIPPED_NOTICE_MS = 3_000L
        const val MAX_PLAYLIST_PAGES = 5
    }
}

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
import tv.lumen.core.model.VideoDetails
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
) : PlayerController {

    private val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { playWhenReady = true }

    // Медиакнопки пульта и голосовые команды (SPEC 5.6)
    private val session: MediaSession = MediaSession.Builder(context, exo)
        .setId("lumen-" + UUID.randomUUID())
        .build()

    private val _state = MutableStateFlow(PlayerState(speed = prefs.defaultSpeed))
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val player: Player get() = exo

    private var details: VideoDetails? = null
    private var requestedHeight: Int? = null
    private var subtitleLang: String? = prefs.subtitleLanguage
    private var audioTrackId: String? = null
    private var retries = 0
    private var loadJob: Job? = null
    private var tickerJob: Job? = null

    init {
        exo.setPlaybackSpeed(prefs.defaultSpeed)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startTicker()
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
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    override fun load(videoId: String, startPositionMs: Long) {
        details = null
        requestedHeight = null
        audioTrackId = null
        retries = 0
        _state.value = PlayerState(videoId = videoId, isLoading = true, speed = exo.playbackParameters.speed)
        fetchAndPrepare(videoId, startPositionMs, forceRefresh = false)
    }

    private fun fetchAndPrepare(videoId: String, positionMs: Long, forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.videoDetails(videoId, forceRefresh)) {
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
                is AppResult.Success -> {
                    details = result.value
                    prepare(result.value, positionMs)
                }
            }
        }
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
        fetchAndPrepare(videoId, position, forceRefresh = true)
    }

    override fun play() = exo.play()
    override fun pause() = exo.pause()

    override fun togglePlayPause() {
        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0)
            exo.play()
        } else if (exo.isPlaying) {
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
        loadJob?.cancel()
        tickerJob?.cancel()
        session.release()
        exo.release()
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val TICK_MS = 500L
    }
}

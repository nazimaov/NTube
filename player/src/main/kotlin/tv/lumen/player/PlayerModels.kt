package tv.lumen.player

import tv.lumen.core.model.AppError
import tv.lumen.core.model.SubtitleTrack
import tv.lumen.core.model.VideoCodec
import tv.lumen.core.model.Video
import tv.lumen.core.model.VideoDetails

/** Предпочтения плеера. До фазы 5 — значения по умолчанию, потом из SettingsRepository (SPEC 9.2). */
data class PlayerPreferences(
    /** 0 — по разрешению экрана. */
    val maxResolution: Int = 0,
    val preferredCodec: CodecPreference = CodecPreference.AUTO,
    /** null — оригинальная дорожка. */
    val audioLanguage: String? = null,
    /** null — субтитры выключены. */
    val subtitleLanguage: String? = null,
    val defaultSpeed: Float = 1f,
    val autoplayNext: Boolean = true,
)

enum class CodecPreference { AUTO, AV1, VP9, AVC }

data class QualityOption(
    val height: Int,
    val fps: Int,
    val codec: VideoCodec,
) {
    val label: String get() = if (fps > 30) "${height}p$fps" else "${height}p"
}

data class AudioTrackOption(
    val id: String,
    val name: String,
    val isOriginal: Boolean,
)

enum class PlayerNotice {
    /** Отдельных потоков нет, играем 360p (SPEC 4.5). */
    LOW_QUALITY_ONLY,
}

data class PlayerState(
    val videoId: String? = null,
    val details: VideoDetails? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    /** Пользователь хочет, чтобы видео играло (true и во время подгрузки после перемотки). */
    val playWhenReady: Boolean = true,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val isLive: Boolean = false,
    val quality: QualityOption? = null,
    val qualities: List<QualityOption> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** languageTag выбранных субтитров, null — выключены. */
    val subtitle: String? = null,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val audioTrack: String? = null,
    val notice: PlayerNotice? = null,
    val error: AppError? = null,
    val isSubscribed: Boolean = false,
    /** Следующее видео после окончания: экран показывает отсчёт 10 с (SPEC 5.6). */
    val nextUp: Video? = null,
)

const val MIN_SPEED = 0.25f
const val MAX_SPEED = 3.0f

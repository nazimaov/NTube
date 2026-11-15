package tv.lumen.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/** Контракт плеера (SPEC 5.4). Все методы вызываются из главного потока. */
interface PlayerController {
    val state: StateFlow<PlayerState>

    /** Поверхность для PlayerView; управление — только через методы контроллера. */
    val player: Player

    fun load(videoId: String, startPositionMs: Long = 0)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(ms: Long)
    fun seekBy(deltaMs: Long)

    /** 0.25–3.0 */
    fun setSpeed(speed: Float)

    fun setQuality(height: Int)

    /** languageTag дорожки или null — выключить. */
    fun setSubtitle(lang: String?)

    fun setAudioTrack(id: String)

    /** Повторить загрузку после ошибки с той же позиции. */
    fun retry()

    fun release()
}

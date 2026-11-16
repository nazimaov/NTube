package tv.lumen.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/** Контракт плеера (SPEC 5.4). Все методы вызываются из главного потока. */
interface PlayerController {
    val state: StateFlow<PlayerState>

    /** Поверхность для PlayerView; управление — только через методы контроллера. */
    val player: Player

    /** startPositionMs = 0 — продолжить с сохранённой позиции (5–95%) или с начала. */
    fun load(videoId: String, startPositionMs: Long = 0, playlistId: String? = null)
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

    /** Подписаться на канал текущего видео или отписаться. */
    fun toggleSubscribe()

    /** Отменить автопереход к следующему видео. */
    fun cancelNextUp()

    /** Сменить порядок по кругу: по очереди → повтор → вперемешку; выбор запоминается. */
    fun cycleOrder()

    /** «Вернуть» пропущенный сегмент SponsorBlock. */
    fun undoSkip()

    /** Пропустить сегмент в режиме «кнопка пропуска». */
    fun skipActiveSegment()

    /** Запомнить текущую скорость для канала видео (SPEC 6.4). */
    fun rememberSpeedForChannel()

    fun release()
}

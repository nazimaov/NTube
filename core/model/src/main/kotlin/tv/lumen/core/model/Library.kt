package tv.lumen.core.model

import java.time.Instant

/** Запись истории просмотра с позицией (SPEC 7.2). */
data class WatchEntry(
    val video: Video,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Instant,
) {
    /** Доля просмотренного 0..1. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

object ResumePolicy {
    const val MIN_FRACTION = 0.05
    const val MAX_FRACTION = 0.95

    /** С какой позиции продолжить: только если просмотрено от 5% до 95% (SPEC 5.6), иначе с начала. */
    fun resumePosition(positionMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0 || positionMs <= 0) return null
        val fraction = positionMs.toDouble() / durationMs
        return if (fraction in MIN_FRACTION..MAX_FRACTION) positionMs else null
    }
}

data class FeedRefreshResult(
    val channels: Int,
    val failed: Int,
    val tookMs: Long,
    /** false — обновление пропущено: с прошлого раза прошло меньше 30 минут. */
    val refreshed: Boolean,
)
/** Своя отметка видео. Хранится локально: отправить её в YouTube можно только со входом в аккаунт. */
enum class Reaction { LIKE, DISLIKE }

/** Локальный профиль («виртуальный аккаунт») на устройстве; пустое [name] — профиль по умолчанию. */
data class Profile(val id: Long, val name: String)

/** Что играет после видео: по очереди, повтор текущего или случайное. */
enum class PlaybackOrder {
    SEQUENTIAL, REPEAT_ONE, SHUFFLE;

    /** Следующий режим для кнопки, меняющейся по кругу. */
    fun next(): PlaybackOrder = entries[(ordinal + 1) % entries.size]
}

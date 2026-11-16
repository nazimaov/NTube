package tv.lumen.player.internal

import tv.lumen.core.model.Video
import kotlin.random.Random

/** Выбор следующего видео в режиме «вперемешку». */
internal object PlaybackQueue {
    /** Сколько первых похожих видео участвуют в случайном выборе: дальше они слишком далеки по теме. */
    const val RELATED_POOL = 10

    /** Случайное из плейлиста (если он есть), иначе из первых похожих; текущее и эфиры не выбираются. */
    fun shuffleNext(playlist: List<Video>, related: List<Video>, currentId: String, random: Random = Random.Default): Video? {
        val pool = playlist.ifEmpty { related.take(RELATED_POOL) }.filter { it.id != currentId && !it.isLive }
        return if (pool.isEmpty()) null else pool[random.nextInt(pool.size)]
    }
}

package tv.lumen.data.library

import kotlinx.coroutines.flow.Flow
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.FeedRefreshResult
import tv.lumen.core.model.Video
import tv.lumen.core.model.WatchEntry

/** Локальные подписки, лента, история и позиции просмотра (SPEC 7.1, 7.2). */
interface LibraryRepository {
    val subscriptions: Flow<List<ChannelSummary>>
    fun isSubscribed(channelId: ChannelId): Flow<Boolean>

    /** Подписка: данные канала уточняются через источник; при ошибке сохраняются переданные. */
    suspend fun subscribe(channelId: ChannelId, name: String, avatarUrl: String?): AppResult<Unit>
    suspend fun unsubscribe(channelId: ChannelId)

    /** Лента подписок, новые сверху. */
    val feed: Flow<List<Video>>

    /**
     * Обновить ленту: не больше 6 запросов одновременно. Без [force] пропускается,
     * если с прошлого обновления прошло меньше 30 минут (SPEC 7.1).
     */
    suspend fun refreshFeed(force: Boolean = false): AppResult<FeedRefreshResult>

    /** Последние 500 просмотров. */
    val history: Flow<List<WatchEntry>>

    /** Недосмотренные (5–95%) — ряд «Продолжить просмотр». */
    val continueWatching: Flow<List<WatchEntry>>

    /** Доля просмотра по id видео — полоса прогресса на карточках. */
    val progress: Flow<Map<String, Float>>

    suspend fun savePosition(video: Video, positionMs: Long, durationMs: Long)

    /** Позиция для продолжения или null — смотреть с начала. */
    suspend fun resumePosition(videoId: String): Long?

    suspend fun removeFromHistory(videoId: String)
    suspend fun clearHistory()

    /** Скорость, сохранённая для канала (только для подписок, SPEC 9.1). */
    suspend fun channelSpeed(channelId: ChannelId): Float?
    suspend fun setChannelSpeed(channelId: ChannelId, speed: Float?)

    val blockedChannelList: Flow<List<ChannelSummary>>

    val blockedChannels: Flow<Set<ChannelId>>
    suspend fun blockChannel(channelId: ChannelId, name: String)
    suspend fun unblockChannel(channelId: ChannelId)
}
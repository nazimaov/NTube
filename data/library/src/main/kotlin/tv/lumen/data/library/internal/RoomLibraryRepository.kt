package tv.lumen.data.library.internal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.database.BlockedChannelDao
import tv.lumen.core.database.BlockedChannelEntity
import tv.lumen.core.database.FeedDao
import tv.lumen.core.database.FeedItemEntity
import tv.lumen.core.database.SubscriptionDao
import tv.lumen.core.database.SubscriptionEntity
import tv.lumen.core.database.WatchHistoryDao
import tv.lumen.core.database.WatchHistoryEntity
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.FeedRefreshResult
import tv.lumen.core.model.ResumePolicy
import tv.lumen.core.model.Video
import tv.lumen.core.model.WatchEntry
import tv.lumen.data.library.ChannelSource
import tv.lumen.data.library.LibraryRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomLibraryRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val feedDao: FeedDao,
    private val historyDao: WatchHistoryDao,
    private val blockedDao: BlockedChannelDao,
    private val source: ChannelSource,
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LibraryRepository {

    // Время последнего обновления ленты. DataStore появится в фазе 5 вместе с настройками.
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    internal var clock: () -> Long = System::currentTimeMillis

    override val subscriptions: Flow<List<ChannelSummary>> =
        subscriptionDao.observeAll().map { list -> list.map { it.toSummary() } }

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> =
        subscriptionDao.observeIsSubscribed(channelId).distinctUntilChanged()

    override suspend fun subscribe(channelId: ChannelId, name: String, avatarUrl: String?): AppResult<Unit> = withContext(io) {
        val resolved = (source.channel(channelId) as? AppResult.Success)?.value
        val entity = SubscriptionEntity(
            channelId = resolved?.id ?: channelId,
            name = resolved?.name?.takeIf { it.isNotBlank() } ?: name,
            avatarUrl = resolved?.avatarUrl ?: avatarUrl,
            subscribedAt = clock(),
            playbackSpeed = subscriptionDao.get(resolved?.id ?: channelId)?.playbackSpeed,
        )
        subscriptionDao.upsert(entity)
        refreshChannel(entity.channelId)
        AppResult.Success(Unit)
    }

    override suspend fun unsubscribe(channelId: ChannelId) = withContext(io) {
        subscriptionDao.delete(channelId) // лента канала удаляется каскадом
    }

    override val feed: Flow<List<Video>> =
        combine(feedDao.observeLatest(FEED_LIMIT), subscriptionDao.observeAll()) { items, subs ->
            val names = subs.associate { it.channelId to it.name }
            items.map { it.toVideo(names[it.channelId]) }
        }

    override suspend fun refreshFeed(force: Boolean): AppResult<FeedRefreshResult> = withContext(io) {
        val start = clock()
        val last = prefs.getLong(KEY_FEED_REFRESHED_AT, 0)
        val subs = subscriptionDao.getAll()
        if (!force && start - last < FEED_STALE_MS) {
            return@withContext AppResult.Success(FeedRefreshResult(subs.size, 0, 0, refreshed = false))
        }
        val semaphore = Semaphore(MAX_PARALLEL_FEEDS)
        val results = coroutineScope {
            subs.map { sub -> async { semaphore.withPermit { refreshChannel(sub.channelId) } } }.awaitAll()
        }
        val failed = results.count { !it }
        if (subs.isNotEmpty() && failed == subs.size) return@withContext AppResult.Failure(AppError.Network)
        prefs.edit().putLong(KEY_FEED_REFRESHED_AT, clock()).apply()
        AppResult.Success(FeedRefreshResult(subs.size, failed, clock() - start, refreshed = true))
    }

    /** true — фид канала получен и сохранён. */
    private suspend fun refreshChannel(channelId: ChannelId): Boolean =
        when (val result = source.channelFeed(channelId)) {
            is AppResult.Success -> {
                feedDao.replaceForChannel(channelId, result.value.map { it.toFeedItem(channelId) })
                true
            }
            is AppResult.Failure -> false
        }

    override val history: Flow<List<WatchEntry>> =
        historyDao.observeRecent(HISTORY_LIMIT).map { list -> list.map { it.toEntry() } }

    override val continueWatching: Flow<List<WatchEntry>> =
        historyDao.observeInProgress(CONTINUE_LIMIT).map { list -> list.map { it.toEntry() } }

    override val progress: Flow<Map<String, Float>> =
        historyDao.observeAllProgress().map { rows ->
            rows.filter { it.durationMs > 0 }.associate { it.videoId to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        }

    override suspend fun savePosition(video: Video, positionMs: Long, durationMs: Long) = withContext(io) {
        if (video.isLive || durationMs <= 0) return@withContext
        historyDao.upsert(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                channelId = video.channelId,
                channelName = video.channelName,
                thumbnailUrl = video.thumbnailUrl,
                durationMs = durationMs,
                positionMs = positionMs.coerceIn(0, durationMs),
                watchedAt = clock(),
            ),
        )
        historyDao.trim(HISTORY_LIMIT)
    }

    override suspend fun resumePosition(videoId: String): Long? = withContext(io) {
        historyDao.get(videoId)?.let { ResumePolicy.resumePosition(it.positionMs, it.durationMs) }
    }

    override suspend fun removeFromHistory(videoId: String) = withContext(io) { historyDao.delete(videoId) }

    override suspend fun clearHistory() = withContext(io) { historyDao.clear() }

    override val blockedChannels: Flow<Set<ChannelId>> =
        blockedDao.observeAll().map { list -> list.mapTo(HashSet()) { it.channelId } }

    override suspend fun blockChannel(channelId: ChannelId, name: String) = withContext(io) {
        blockedDao.upsert(BlockedChannelEntity(channelId, name, clock()))
    }

    override suspend fun unblockChannel(channelId: ChannelId) = withContext(io) { blockedDao.delete(channelId) }

    companion object {
        const val MAX_PARALLEL_FEEDS = 6
        const val FEED_STALE_MS = 30 * 60 * 1000L
        const val HISTORY_LIMIT = 500
        const val CONTINUE_LIMIT = 20
        const val FEED_LIMIT = 300
        private const val KEY_FEED_REFRESHED_AT = "feed_refreshed_at"
    }
}

private fun SubscriptionEntity.toSummary() = ChannelSummary(
    id = channelId,
    name = name,
    avatarUrl = avatarUrl,
    subscriberCount = -1,
    isVerified = false,
)

internal fun Video.toFeedItem(channelId: ChannelId) = FeedItemEntity(
    videoId = id,
    channelId = channelId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    durationSec = durationSec,
    publishedAt = publishedAt?.toEpochMilli() ?: 0,
    isLive = isLive,
    isShort = isShort,
)

internal fun FeedItemEntity.toVideo(channelName: String?) = Video(
    id = videoId,
    title = title,
    channelId = channelId,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationSec = durationSec,
    viewCount = -1,
    publishedAt = publishedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    publishedText = null,
    isLive = isLive,
    isShort = isShort,
)

internal fun WatchHistoryEntity.toEntry() = WatchEntry(
    video = Video(
        id = videoId,
        title = title,
        channelId = channelId,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        durationSec = durationMs / 1000,
        viewCount = -1,
        publishedAt = null,
        publishedText = null,
        isLive = false,
        isShort = false,
    ),
    positionMs = positionMs,
    durationMs = durationMs,
    watchedAt = Instant.ofEpochMilli(watchedAt),
)
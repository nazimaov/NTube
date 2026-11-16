package tv.lumen.data.library.internal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
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
import tv.lumen.core.datastore.SettingsRepository
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
import tv.lumen.data.library.PlayQueue
import tv.lumen.data.library.ProfileRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подписки, лента и история текущего локального профиля ([ProfileRepository]).
 * Чёрный список каналов и настройки — общие для устройства.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomLibraryRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val feedDao: FeedDao,
    private val historyDao: WatchHistoryDao,
    private val blockedDao: BlockedChannelDao,
    private val profiles: ProfileRepository,
    private val source: ChannelSource,
    override val queue: PlayQueue,
    private val settings: SettingsRepository,
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LibraryRepository {

    // Время последнего обновления ленты — своё у каждого профиля (подписки разные)
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    internal var clock: () -> Long = System::currentTimeMillis

    private fun <T> perProfile(block: (Long) -> Flow<T>): Flow<T> = profiles.currentId.flatMapLatest(block)

    override val subscriptions: Flow<List<ChannelSummary>> =
        perProfile { subscriptionDao.observeAll(it) }.map { list -> list.map { it.toSummary() } }

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> =
        perProfile { subscriptionDao.observeIsSubscribed(it, channelId) }.distinctUntilChanged()

    override suspend fun subscribe(channelId: ChannelId, name: String, avatarUrl: String?): AppResult<Unit> = withContext(io) {
        val profileId = profiles.currentId()
        val resolved = (source.channel(channelId) as? AppResult.Success)?.value
        val id = resolved?.id ?: channelId
        val entity = SubscriptionEntity(
            profileId = profileId,
            channelId = id,
            name = resolved?.name?.takeIf { it.isNotBlank() } ?: name,
            avatarUrl = resolved?.avatarUrl ?: avatarUrl,
            subscribedAt = clock(),
            playbackSpeed = subscriptionDao.get(profileId, id)?.playbackSpeed,
        )
        subscriptionDao.upsert(entity)
        refreshChannel(entity.channelId)
        AppResult.Success(Unit)
    }

    override suspend fun unsubscribe(channelId: ChannelId) = withContext(io) {
        subscriptionDao.delete(profiles.currentId(), channelId)
        feedDao.deleteOrphans() // кэш ленты удаляется, только если канал больше не нужен ни одному профилю
    }

    override val feed: Flow<List<Video>> = perProfile { profileId ->
        combine(feedDao.observeLatest(profileId, FEED_LIMIT), subscriptionDao.observeAll(profileId)) { items, subs ->
            val byId = subs.associateBy { it.channelId }
            items.map { item -> byId[item.channelId].let { item.toVideo(it?.name, it?.avatarUrl) } }
        }
    }

    override suspend fun refreshFeed(force: Boolean): AppResult<FeedRefreshResult> = withContext(io) {
        val profileId = profiles.currentId()
        val key = "$KEY_FEED_REFRESHED_AT.$profileId"
        val start = clock()
        val last = prefs.getLong(key, 0)
        val subs = subscriptionDao.getAll(profileId)
        if (!force && start - last < FEED_STALE_MS) {
            return@withContext AppResult.Success(FeedRefreshResult(subs.size, 0, 0, refreshed = false))
        }
        val semaphore = Semaphore(MAX_PARALLEL_FEEDS)
        val results = coroutineScope {
            subs.map { sub -> async { semaphore.withPermit { refreshChannel(sub.channelId) } } }.awaitAll()
        }
        val failed = results.count { !it }
        if (subs.isNotEmpty() && failed == subs.size) return@withContext AppResult.Failure(AppError.Network)
        prefs.edit().putLong(key, clock()).apply()
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
        perProfile { historyDao.observeRecent(it, HISTORY_LIMIT) }.map { list -> list.map { it.toEntry() } }

    override val continueWatching: Flow<List<WatchEntry>> =
        perProfile { historyDao.observeInProgress(it, CONTINUE_LIMIT) }.map { list -> list.map { it.toEntry() } }

    override val progress: Flow<Map<String, Float>> =
        perProfile { historyDao.observeAllProgress(it) }.map { rows ->
            rows.filter { it.durationMs > 0 }.associate { it.videoId to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        }

    override suspend fun savePosition(video: Video, positionMs: Long, durationMs: Long) = withContext(io) {
        // «Не вести историю» (SPEC 7.2)
        if (video.isLive || durationMs <= 0 || !settings.current.value.historyEnabled) return@withContext
        val profileId = profiles.currentId()
        historyDao.upsert(
            WatchHistoryEntity(
                profileId = profileId,
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
        historyDao.trim(profileId, HISTORY_LIMIT)
    }

    override suspend fun resumePosition(videoId: String): Long? = withContext(io) {
        historyDao.get(profiles.currentId(), videoId)?.let { ResumePolicy.resumePosition(it.positionMs, it.durationMs) }
    }

    override suspend fun removeFromHistory(videoId: String) = withContext(io) { historyDao.delete(profiles.currentId(), videoId) }

    override suspend fun clearHistory() = withContext(io) { historyDao.clear(profiles.currentId()) }

    override suspend fun channelSpeed(channelId: ChannelId): Float? = withContext(io) {
        subscriptionDao.get(profiles.currentId(), channelId)?.playbackSpeed
    }

    override suspend fun setChannelSpeed(channelId: ChannelId, speed: Float?) = withContext(io) {
        subscriptionDao.get(profiles.currentId(), channelId)?.let { subscriptionDao.upsert(it.copy(playbackSpeed = speed)) } ?: Unit
    }

    override val blockedChannelList: Flow<List<ChannelSummary>> =
        blockedDao.observeAll().map { list -> list.map { ChannelSummary(it.channelId, it.name, null, -1, false) } }

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

internal fun FeedItemEntity.toVideo(channelName: String?, channelAvatarUrl: String? = null) = Video(
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
    channelAvatarUrl = channelAvatarUrl,
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

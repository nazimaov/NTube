package tv.lumen.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscription")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM subscription WHERE channel_id = :channelId)")
    fun observeIsSubscribed(channelId: String): Flow<Boolean>

    @Query("SELECT * FROM subscription WHERE channel_id = :channelId")
    suspend fun get(channelId: String): SubscriptionEntity?

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscription WHERE channel_id = :channelId")
    suspend fun delete(channelId: String)
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_item ORDER BY published_at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<FeedItemEntity>>

    @Query("DELETE FROM feed_item WHERE channel_id = :channelId")
    suspend fun deleteForChannel(channelId: String)

    @Upsert
    suspend fun upsertAll(items: List<FeedItemEntity>)

    /** Лента канала заменяется целиком: RSS отдаёт последние ~15 видео. */
    @Transaction
    suspend fun replaceForChannel(channelId: String, items: List<FeedItemEntity>) {
        deleteForChannel(channelId)
        upsertAll(items)
    }
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watched_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchHistoryEntity>>

    /** Недосмотренные: от 5% до 95% (SPEC 5.6). */
    @Query(
        """
        SELECT * FROM watch_history
        WHERE duration_ms > 0 AND position_ms * 100 >= duration_ms * 5 AND position_ms * 100 <= duration_ms * 95
        ORDER BY watched_at DESC LIMIT :limit
        """,
    )
    fun observeInProgress(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY watched_at DESC")
    suspend fun getAll(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE video_id = :videoId")
    suspend fun get(videoId: String): WatchHistoryEntity?

    @Query("SELECT video_id, position_ms, duration_ms FROM watch_history WHERE video_id IN (:ids)")
    suspend fun progressFor(ids: List<String>): List<VideoProgressRow>

    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE video_id = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    /** История хранит не больше [keep] последних записей. */
    @Query("DELETE FROM watch_history WHERE video_id NOT IN (SELECT video_id FROM watch_history ORDER BY watched_at DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("SELECT video_id, position_ms, duration_ms FROM watch_history")
    fun observeAllProgress(): Flow<List<VideoProgressRow>>
}

data class VideoProgressRow(
    @androidx.room.ColumnInfo(name = "video_id") val videoId: String,
    @androidx.room.ColumnInfo(name = "position_ms") val positionMs: Long,
    @androidx.room.ColumnInfo(name = "duration_ms") val durationMs: Long,
)

@Dao
interface BlockedChannelDao {
    @Query("SELECT * FROM blocked_channel ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BlockedChannelEntity>>

    @Upsert
    suspend fun upsert(entry: BlockedChannelEntity)

    @Query("DELETE FROM blocked_channel WHERE channel_id = :channelId")
    suspend fun delete(channelId: String)
}

@Dao
interface BrandingCacheDao {
    @Query("SELECT * FROM branding_cache WHERE video_id = :videoId")
    suspend fun get(videoId: String): BrandingCacheEntity?

    @Upsert
    suspend fun upsert(entry: BrandingCacheEntity)

    @Query("DELETE FROM branding_cache WHERE fetched_at < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile ORDER BY id")
    suspend fun getAll(): List<ProfileEntity>

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profile SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VideoReactionDao {
    @Query("SELECT reaction FROM video_reaction WHERE profile_id = :profileId AND video_id = :videoId")
    fun observe(profileId: Long, videoId: String): Flow<Int?>

    @Upsert
    suspend fun upsert(entry: VideoReactionEntity)

    @Query("DELETE FROM video_reaction WHERE profile_id = :profileId AND video_id = :videoId")
    suspend fun delete(profileId: Long, videoId: String)
}

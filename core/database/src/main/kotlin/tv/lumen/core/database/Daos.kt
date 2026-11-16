package tv.lumen.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Подписки и история — всегда в рамках профиля (profile_id).

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription WHERE profile_id = :profileId ORDER BY name COLLATE NOCASE")
    fun observeAll(profileId: Long): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscription WHERE profile_id = :profileId")
    suspend fun getAll(profileId: Long): List<SubscriptionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM subscription WHERE profile_id = :profileId AND channel_id = :channelId)")
    fun observeIsSubscribed(profileId: Long, channelId: String): Flow<Boolean>

    @Query("SELECT * FROM subscription WHERE profile_id = :profileId AND channel_id = :channelId")
    suspend fun get(profileId: Long, channelId: String): SubscriptionEntity?

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscription WHERE profile_id = :profileId AND channel_id = :channelId")
    suspend fun delete(profileId: Long, channelId: String)
}

@Dao
interface FeedDao {
    /** Лента профиля: видео только тех каналов, на которые он подписан. */
    @Query(
        """
        SELECT f.* FROM feed_item f
        INNER JOIN subscription s ON s.channel_id = f.channel_id AND s.profile_id = :profileId
        ORDER BY f.published_at DESC LIMIT :limit
        """,
    )
    fun observeLatest(profileId: Long, limit: Int): Flow<List<FeedItemEntity>>

    @Query("DELETE FROM feed_item WHERE channel_id = :channelId")
    suspend fun deleteForChannel(channelId: String)

    /** Кэш каналов, на которые больше не подписан ни один профиль. */
    @Query("DELETE FROM feed_item WHERE channel_id NOT IN (SELECT channel_id FROM subscription)")
    suspend fun deleteOrphans()

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
    @Query("SELECT * FROM watch_history WHERE profile_id = :profileId ORDER BY watched_at DESC LIMIT :limit")
    fun observeRecent(profileId: Long, limit: Int): Flow<List<WatchHistoryEntity>>

    /** Недосмотренные: от 5% до 95% (SPEC 5.6). */
    @Query(
        """
        SELECT * FROM watch_history
        WHERE profile_id = :profileId
          AND duration_ms > 0 AND position_ms * 100 >= duration_ms * 5 AND position_ms * 100 <= duration_ms * 95
        ORDER BY watched_at DESC LIMIT :limit
        """,
    )
    fun observeInProgress(profileId: Long, limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profile_id = :profileId ORDER BY watched_at DESC")
    suspend fun getAll(profileId: Long): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE profile_id = :profileId AND video_id = :videoId")
    suspend fun get(profileId: Long, videoId: String): WatchHistoryEntity?

    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE profile_id = :profileId AND video_id = :videoId")
    suspend fun delete(profileId: Long, videoId: String)

    @Query("DELETE FROM watch_history WHERE profile_id = :profileId")
    suspend fun clear(profileId: Long)

    /** История профиля хранит не больше [keep] последних записей. */
    @Query(
        """
        DELETE FROM watch_history WHERE profile_id = :profileId AND video_id NOT IN
        (SELECT video_id FROM watch_history WHERE profile_id = :profileId ORDER BY watched_at DESC LIMIT :keep)
        """,
    )
    suspend fun trim(profileId: Long, keep: Int)

    @Query("SELECT video_id, position_ms, duration_ms FROM watch_history WHERE profile_id = :profileId")
    fun observeAllProgress(profileId: Long): Flow<List<VideoProgressRow>>
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

    /** Видео с отметкой [reaction] (1 — нравится, -1 — нет), сначала свежие. */
    @Query("SELECT video_id FROM video_reaction WHERE profile_id = :profileId AND reaction = :reaction ORDER BY updated_at DESC LIMIT :limit")
    fun observeIds(profileId: Long, reaction: Int, limit: Int): Flow<List<String>>
}

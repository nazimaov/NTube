package tv.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Схема БД — SPEC 9.1. Время хранится в миллисекундах эпохи.

@Entity(tableName = "subscription")
data class SubscriptionEntity(
    @PrimaryKey @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "subscribed_at") val subscribedAt: Long,
    /** Скорость для канала (SPEC 6.4); null — общая. */
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Float?,
)

@Entity(
    tableName = "feed_item",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["channel_id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("published_at"), Index("channel_id")],
)
data class FeedItemEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "duration_s") val durationSec: Long,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "is_live") val isLive: Boolean,
    @ColumnInfo(name = "is_short") val isShort: Boolean,
)

@Entity(tableName = "watch_history", indices = [Index("watched_at")])
data class WatchHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "channel_id") val channelId: String?,
    @ColumnInfo(name = "channel_name") val channelName: String?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "watched_at") val watchedAt: Long,
)

@Entity(tableName = "blocked_channel")
data class BlockedChannelEntity(
    @PrimaryKey @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "blocked_at") val blockedAt: Long,
)

/** Кэш DeArrow на 24 часа (SPEC 6.2). */
@Entity(tableName = "branding_cache")
data class BrandingCacheEntity(
    @PrimaryKey @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "thumbnail_time") val thumbnailTime: Double?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
/** Локальный профиль («виртуальный аккаунт») на устройстве. Пустое имя — профиль по умолчанию. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Отметка профиля «нравится» (1) / «не нравится» (-1). Хранится только на устройстве:
 * без входа в аккаунт Google YouTube её не получает.
 */
@Entity(
    tableName = "video_reaction",
    primaryKeys = ["profile_id", "video_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VideoReactionEntity(
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "reaction") val reaction: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

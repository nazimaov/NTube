package tv.lumen.core.model

import java.time.Instant

/**
 * Идентификатор канала в форме экстрактора без схемы: `UCxxxxxxxxxxxxxxxxxxxxxx`, `@handle`, `user/name` или `c/name`.
 * Ссылка строится через [channelUrl].
 */
typealias ChannelId = String

fun channelUrl(channelId: ChannelId): String {
    val path = if (channelId.startsWith("UC") && channelId.length == 24) "channel/$channelId" else channelId
    return "https://www.youtube.com/$path"
}

fun videoUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

fun playlistUrl(playlistId: String): String = "https://www.youtube.com/playlist?list=$playlistId"

/** Карточка видео в списках (лента, поиск, канал, плейлист, похожие). */
data class Video(
    val id: String,
    val title: String,
    val channelId: ChannelId?,
    val channelName: String?,
    val thumbnailUrl: String?,
    /** Длительность в секундах; -1, если неизвестна (прямой эфир, RSS-фид). */
    val durationSec: Long,
    /** Число просмотров; -1, если неизвестно. */
    val viewCount: Long,
    val publishedAt: Instant?,
    /** Дата как её показывает YouTube («2 дня назад»), если точной нет. */
    val publishedText: String?,
    val isLive: Boolean,
    val isShort: Boolean,
    /** Аватар канала для карточки; null — показывается буква. */
    val channelAvatarUrl: String? = null,
)

data class ChannelSummary(
    val id: ChannelId,
    val name: String,
    val avatarUrl: String?,
    /** -1, если неизвестно. */
    val subscriberCount: Long,
    val isVerified: Boolean,
)

data class PlaylistSummary(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val channelName: String?,
    /** -1, если неизвестно. */
    val videoCount: Long,
)

sealed interface SearchItem {
    data class VideoItem(val video: Video) : SearchItem
    data class ChannelItem(val channel: ChannelSummary) : SearchItem
    data class PlaylistItem(val playlist: PlaylistSummary) : SearchItem
}

enum class SearchFilter { ALL, VIDEOS, CHANNELS, PLAYLISTS }

enum class ChannelTab { VIDEOS, LIVE, SHORTS, PLAYLISTS }

data class ChannelDetails(
    val channel: ChannelSummary,
    val bannerUrl: String?,
    val description: String,
    /** Вкладки, которые есть у канала. */
    val tabs: Set<ChannelTab>,
)

data class Comment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    /** -1, если неизвестно. */
    val likeCount: Int,
    val publishedText: String?,
    val replyCount: Int,
    val isPinned: Boolean,
    val isHeartedByUploader: Boolean,
)

/** Непрозрачный токен следующей страницы; содержимое понимает только модуль-источник. */
@JvmInline
value class PageToken(val value: String)

data class Page<out T>(
    val items: List<T>,
    val next: PageToken?,
)

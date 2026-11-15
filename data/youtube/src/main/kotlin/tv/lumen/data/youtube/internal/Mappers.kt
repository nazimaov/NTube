package tv.lumen.data.youtube.internal

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import tv.lumen.core.model.AudioCodec
import tv.lumen.core.model.AudioStream
import tv.lumen.core.model.ChannelDetails
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.Comment
import tv.lumen.core.model.DeliveryMethod
import tv.lumen.core.model.PlaylistSummary
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.StreamSet
import tv.lumen.core.model.SubtitleTrack
import tv.lumen.core.model.Video
import tv.lumen.core.model.VideoCodec
import tv.lumen.core.model.VideoDetails
import tv.lumen.core.model.VideoStream
import java.time.Instant
import org.schabi.newpipe.extractor.stream.AudioStream as NpAudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod as NpDeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream as NpVideoStream

/** Преобразования классов NewPipeExtractor в собственные модели (SPEC 4.3, 4.4). */
internal object Mappers {

    private val youtube get() = ServiceList.YouTube

    // ---------- идентификаторы ----------

    fun videoIdFromUrl(url: String?): String? =
        url?.let { runCatching { youtube.streamLHFactory.getId(it) }.getOrNull() }

    fun playlistIdFromUrl(url: String?): String? =
        url?.let { runCatching { youtube.playlistLHFactory.getId(it) }.getOrNull() }

    fun channelIdFromUrl(url: String?): ChannelId? =
        url?.let { runCatching { youtube.channelLHFactory.getId(it) }.getOrNull() }?.let(::normalizeChannelId)

    /** `channel/UCxxx` → `UCxxx`; ручки (`@name`), `user/…` и `c/…` остаются как есть. */
    fun normalizeChannelId(raw: String): ChannelId = raw.removePrefix("channel/").trimEnd('/')

    /** Обратное к [normalizeChannelId]: путь, который понимают фабрики ссылок экстрактора. */
    fun channelPath(id: ChannelId): String =
        if (id.startsWith("UC") && id.length == 24) "channel/$id" else id

    // ---------- картинки ----------

    /**
     * Обложка для карточки: самая большая не выше [maxHeight], иначе самая маленькая из остальных.
     * Если высота неизвестна, оценивается по уровню разрешения.
     */
    fun bestImageUrl(images: List<Image>?, maxHeight: Int = 720): String? {
        if (images.isNullOrEmpty()) return null
        val scored = images.map { it to estimatedHeight(it) }
        val fitting = scored.filter { it.second in 1..maxHeight }
        val chosen = fitting.maxByOrNull { it.second }
            ?: scored.filter { it.second > maxHeight }.minByOrNull { it.second }
            ?: scored.first()
        return chosen.first.url
    }

    private fun estimatedHeight(image: Image): Int {
        if (image.height > 0) return image.height
        return when (image.estimatedResolutionLevel) {
            Image.ResolutionLevel.HIGH -> 720
            Image.ResolutionLevel.MEDIUM -> 360
            Image.ResolutionLevel.LOW -> 120
            else -> 0
        }
    }

    // ---------- элементы списков ----------

    fun video(item: StreamInfoItem): Video? {
        val id = videoIdFromUrl(item.url) ?: return null
        return Video(
            id = id,
            title = item.name.orEmpty(),
            channelId = channelIdFromUrl(item.uploaderUrl),
            channelName = item.uploaderName,
            thumbnailUrl = bestImageUrl(item.thumbnails),
            durationSec = item.duration.takeIf { it > 0 } ?: -1,
            viewCount = item.viewCount.takeIf { it >= 0 } ?: -1,
            publishedAt = item.uploadDate?.instant,
            publishedText = item.textualUploadDate,
            isLive = item.streamType.isLive(),
            isShort = item.isShortFormContent,
        )
    }

    fun channel(item: ChannelInfoItem): ChannelSummary? {
        val id = channelIdFromUrl(item.url) ?: return null
        return ChannelSummary(
            id = id,
            name = item.name.orEmpty(),
            avatarUrl = bestImageUrl(item.thumbnails, maxHeight = 240),
            subscriberCount = item.subscriberCount.takeIf { it >= 0 } ?: -1,
            isVerified = item.isVerified,
        )
    }

    fun playlist(item: PlaylistInfoItem): PlaylistSummary? {
        val id = playlistIdFromUrl(item.url) ?: return null
        return PlaylistSummary(
            id = id,
            title = item.name.orEmpty(),
            thumbnailUrl = bestImageUrl(item.thumbnails),
            channelName = item.uploaderName,
            videoCount = item.streamCount.takeIf { it >= 0 } ?: -1,
        )
    }

    fun searchItem(item: InfoItem): SearchItem? = when (item) {
        is StreamInfoItem -> video(item)?.let(SearchItem::VideoItem)
        is ChannelInfoItem -> channel(item)?.let(SearchItem::ChannelItem)
        is PlaylistInfoItem -> playlist(item)?.let(SearchItem::PlaylistItem)
        else -> null
    }

    fun videos(items: List<InfoItem>?): List<Video> =
        items.orEmpty().filterIsInstance<StreamInfoItem>().mapNotNull(::video)

    fun comment(item: CommentsInfoItem): Comment = Comment(
        id = item.commentId.orEmpty(),
        authorName = item.uploaderName.orEmpty(),
        authorAvatarUrl = bestImageUrl(item.uploaderAvatars, maxHeight = 240),
        text = plainText(item.commentText),
        likeCount = item.likeCount,
        publishedText = item.textualUploadDate,
        replyCount = item.replyCount.coerceAtLeast(0),
        isPinned = item.isPinned,
        isHeartedByUploader = item.isHeartedByUploader,
    )

    // ---------- канал ----------

    fun channelDetails(info: ChannelInfo): ChannelDetails = ChannelDetails(
        channel = ChannelSummary(
            id = normalizeChannelId(info.id),
            name = info.name.orEmpty(),
            avatarUrl = bestImageUrl(info.avatars, maxHeight = 240),
            subscriberCount = info.subscriberCount.takeIf { it >= 0 } ?: -1,
            isVerified = info.isVerified,
        ),
        bannerUrl = bestImageUrl(info.banners, maxHeight = 1080),
        description = info.description.orEmpty(),
        tabs = info.tabs.mapNotNull { handler -> handler.contentFilters.firstOrNull()?.let(::channelTab) }.toSet(),
    )

    fun channelTab(name: String): ChannelTab? = when (name) {
        ChannelTabs.VIDEOS -> ChannelTab.VIDEOS
        ChannelTabs.LIVESTREAMS -> ChannelTab.LIVE
        ChannelTabs.SHORTS -> ChannelTab.SHORTS
        ChannelTabs.PLAYLISTS -> ChannelTab.PLAYLISTS
        else -> null
    }

    fun channelTabName(tab: ChannelTab): String = when (tab) {
        ChannelTab.VIDEOS -> ChannelTabs.VIDEOS
        ChannelTab.LIVE -> ChannelTabs.LIVESTREAMS
        ChannelTab.SHORTS -> ChannelTabs.SHORTS
        ChannelTab.PLAYLISTS -> ChannelTabs.PLAYLISTS
    }

    // ---------- видео и потоки ----------

    fun videoDetails(videoId: String, info: StreamInfo, now: Instant): VideoDetails {
        val isLive = info.streamType.isLive()
        return VideoDetails(
            video = Video(
                id = videoId,
                title = info.name.orEmpty(),
                channelId = channelIdFromUrl(info.uploaderUrl),
                channelName = info.uploaderName,
                thumbnailUrl = bestImageUrl(info.thumbnails, maxHeight = 1080),
                durationSec = info.duration.takeIf { it > 0 } ?: -1,
                viewCount = info.viewCount.takeIf { it >= 0 } ?: -1,
                publishedAt = info.uploadDate?.instant,
                publishedText = info.textualUploadDate,
                isLive = isLive,
                isShort = info.isShortFormContent,
            ),
            description = plainText(info.description),
            likeCount = info.likeCount.takeIf { it >= 0 } ?: -1,
            channelAvatarUrl = bestImageUrl(info.uploaderAvatars, maxHeight = 240),
            channelSubscriberCount = info.uploaderSubscriberCount.takeIf { it >= 0 } ?: -1,
            ageLimit = info.ageLimit,
            streams = streamSet(info),
            related = videos(info.relatedItems),
            fetchedAt = now,
        )
    }

    fun streamSet(info: StreamInfo): StreamSet = StreamSet(
        videoOnly = info.videoOnlyStreams.orEmpty().mapNotNull(::videoStream),
        audio = info.audioStreams.orEmpty().mapNotNull(::audioStream),
        muxed = info.videoStreams.orEmpty().mapNotNull(::videoStream),
        // Плеер подключает субтитры в формате VTT (SPEC 5.2)
        subtitles = info.subtitles.orEmpty().filter { it.format == MediaFormat.VTT }.mapNotNull(::subtitle),
        hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
        dashMpdUrl = info.dashMpdUrl?.takeIf { it.isNotBlank() },
        isLive = info.streamType.isLive(),
        durationSec = info.duration,
    )

    fun videoStream(stream: NpVideoStream): VideoStream? {
        if (!stream.isUrl) return null
        // getResolution(), а не поле resolution: публичное поле помечено @Deprecated
        val label: String? = stream.getResolution()
        val height = stream.height.takeIf { it > 0 } ?: parseHeight(label)
        return VideoStream(
            id = stream.id,
            url = stream.content,
            delivery = delivery(stream.deliveryMethod),
            mimeType = stream.format?.mimeType,
            codec = videoCodec(stream.codec),
            codecString = stream.codec,
            width = stream.width.coerceAtLeast(0),
            height = height,
            fps = stream.fps.coerceAtLeast(0),
            bitrate = stream.bitrate.coerceAtLeast(0),
            isHdr = label?.contains("HDR", ignoreCase = true) == true,
            qualityLabel = label,
        )
    }

    fun audioStream(stream: NpAudioStream): AudioStream? {
        if (!stream.isUrl) return null
        val type = stream.audioTrackType
        return AudioStream(
            id = stream.id,
            url = stream.content,
            delivery = delivery(stream.deliveryMethod),
            mimeType = stream.format?.mimeType,
            codec = audioCodec(stream.codec, stream.format),
            codecString = stream.codec,
            bitrate = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate.coerceAtLeast(0),
            trackId = stream.audioTrackId,
            trackName = stream.audioTrackName,
            language = stream.audioLocale?.toLanguageTag(),
            isOriginal = type == null || type == AudioTrackType.ORIGINAL,
        )
    }

    private fun subtitle(stream: SubtitlesStream): SubtitleTrack? {
        if (!stream.isUrl) return null
        return SubtitleTrack(
            url = stream.content,
            languageTag = stream.languageTag.orEmpty(),
            name = stream.displayLanguageName ?: stream.languageTag.orEmpty(),
            isAutoGenerated = stream.isAutoGenerated,
            mimeType = stream.format?.mimeType,
        )
    }

    fun delivery(method: NpDeliveryMethod?): DeliveryMethod = when (method) {
        NpDeliveryMethod.PROGRESSIVE_HTTP -> DeliveryMethod.PROGRESSIVE_HTTP
        NpDeliveryMethod.DASH -> DeliveryMethod.DASH
        NpDeliveryMethod.HLS -> DeliveryMethod.HLS
        else -> DeliveryMethod.OTHER
    }

    fun videoCodec(codec: String?): VideoCodec {
        val c = codec?.lowercase() ?: return VideoCodec.OTHER
        return when {
            c.startsWith("av01") -> VideoCodec.AV1
            c.startsWith("vp9") || c.startsWith("vp09") -> VideoCodec.VP9
            c.startsWith("avc1") || c.startsWith("avc3") -> VideoCodec.AVC
            else -> VideoCodec.OTHER
        }
    }

    fun audioCodec(codec: String?, format: MediaFormat?): AudioCodec {
        val c = codec?.lowercase()
        return when {
            c != null && c.startsWith("opus") -> AudioCodec.OPUS
            c != null && c.startsWith("mp4a") -> AudioCodec.AAC
            format == MediaFormat.WEBMA_OPUS || format == MediaFormat.OPUS -> AudioCodec.OPUS
            format == MediaFormat.M4A -> AudioCodec.AAC
            else -> AudioCodec.OTHER
        }
    }

    /** Текст описания или комментария: HTML (YouTube отдаёт `<br>`, ссылки, сущности) → обычный текст. */
    fun plainText(description: Description?): String {
        description ?: return ""
        if (description.type != Description.Type.HTML) return description.content
        return description.content
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .trim()
    }

    /** `1080p60 HDR` → 1080. */
    fun parseHeight(label: String?): Int =
        label?.let { Regex("""(\d{3,4})p""").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

    private fun StreamType?.isLive() =
        this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM
}

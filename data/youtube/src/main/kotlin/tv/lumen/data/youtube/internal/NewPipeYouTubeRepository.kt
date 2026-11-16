package tv.lumen.data.youtube.internal

import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubePostLiveStreamDvrDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.YoutubeTrendingExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelDetails
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.Comment
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken
import tv.lumen.core.model.PlaylistSummary
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.Video
import tv.lumen.core.model.VideoDetails
import tv.lumen.core.model.channelUrl
import tv.lumen.core.model.playlistUrl
import tv.lumen.core.model.videoUrl
import tv.lumen.data.youtube.YouTubeInitializer
import tv.lumen.data.youtube.YouTubeRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NewPipeYouTubeRepository @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : YouTubeRepository {

    private val youtube get() = ServiceList.YouTube

    /** Ссылки на потоки живут несколько часов; держим не дольше часа (SPEC 4.4). */
    private val details = TimedCache<String, CachedVideo>(ttlMillis = 60 * 60 * 1000L, maxSize = 20)

    private class CachedVideo(val details: VideoDetails, val raw: StreamInfo)

    private suspend fun <T> call(block: () -> T): AppResult<T> = extract(io) {
        YouTubeInitializer.ensureInitialized(client)
        block()
    }

    override suspend fun trending(): AppResult<List<Video>> = call {
        // Страница «В тренде» у YouTube может пропадать; тогда берём киоск по умолчанию.
        val kiosks = youtube.kioskList
        val ids = listOf(YoutubeTrendingExtractor.KIOSK_ID, kiosks.defaultKioskId).distinct()
        var lastError: Exception? = null
        for (id in ids) {
            try {
                val url = kiosks.getListLinkHandlerFactoryByType(id).fromId(id).url
                val videos = Mappers.videos(KioskInfo.getInfo(youtube, url).relatedItems)
                if (videos.isNotEmpty()) return@call videos
            } catch (e: Exception) {
                lastError = e
            }
        }
        lastError?.let { throw it }
        emptyList()
    }

    /** Чарты YouTube «Музыка в тренде» (киоск trending_music); не во всех странах. */
    override suspend fun musicTrending(): AppResult<List<Video>> = call {
        val kiosks = youtube.kioskList
        val url = kiosks.getListLinkHandlerFactoryByType(MUSIC_KIOSK_ID).fromId(MUSIC_KIOSK_ID).url
        Mappers.videos(KioskInfo.getInfo(youtube, url).relatedItems)
    }

    override suspend fun search(query: String, filter: SearchFilter, page: PageToken?) = call {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(searchFilter(filter)), "")
        if (page == null) {
            val info = SearchInfo.getInfo(youtube, handler)
            Page(info.relatedItems.mapNotNull(Mappers::searchItem), PageTokens.encode(info.nextPage))
        } else {
            SearchInfo.getMoreItems(youtube, handler, PageTokens.decode(page)).toPage(Mappers::searchItem)
        }
    }

    override suspend fun searchSuggestions(query: String) = call {
        if (query.isBlank()) emptyList() else youtube.suggestionExtractor.suggestionList(query).orEmpty()
    }

    override suspend fun videoDetails(videoId: String, forceRefresh: Boolean): AppResult<VideoDetails> {
        if (forceRefresh) details.remove(videoId) else details.get(videoId)?.let { return AppResult.Success(it.details) }
        return call {
            val info = StreamInfo.getInfo(youtube, videoUrl(videoId))
            val mapped = Mappers.videoDetails(videoId, info, Instant.now())
            details.put(videoId, CachedVideo(mapped, info))
            mapped
        }
    }

    override suspend fun dashManifest(videoId: String, streamId: String): AppResult<String> {
        val cached = details.get(videoId) ?: when (val fresh = videoDetails(videoId, forceRefresh = true)) {
            is AppResult.Failure -> return fresh
            is AppResult.Success -> details.get(videoId)
        } ?: return AppResult.Failure(AppError.ExtractionBroken)

        return call {
            val raw = cached.raw
            val stream: Stream = (raw.videoOnlyStreams.orEmpty() + raw.audioStreams.orEmpty() + raw.videoStreams.orEmpty())
                .firstOrNull { it.id == streamId && it.deliveryMethod == DeliveryMethod.DASH }
                ?: throw IllegalArgumentException("DASH-поток $streamId не найден у видео $videoId")
            val itag = stream.itagItem ?: throw IllegalStateException("У потока $streamId нет ItagItem")
            if (raw.streamType == StreamType.POST_LIVE_STREAM) {
                YoutubePostLiveStreamDvrDashManifestCreator.fromPostLiveStreamDvrStreamingUrl(
                    stream.content, itag, itag.targetDurationSec, raw.duration,
                )
            } else {
                YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itag, raw.duration)
            }
        }
    }

    override suspend fun channel(channelId: ChannelId): AppResult<ChannelDetails> = call {
        Mappers.channelDetails(ChannelInfo.getInfo(youtube, channelUrl(channelId)))
    }

    override suspend fun channelVideos(channelId: ChannelId, tab: ChannelTab, page: PageToken?): AppResult<Page<Video>> {
        require(tab != ChannelTab.PLAYLISTS) { "Для вкладки плейлистов есть channelPlaylists()" }
        return call { channelTabPage(channelId, tab, page) { (it as? org.schabi.newpipe.extractor.stream.StreamInfoItem)?.let(Mappers::video) } }
    }

    override suspend fun channelPlaylists(channelId: ChannelId, page: PageToken?): AppResult<Page<PlaylistSummary>> = call {
        channelTabPage(channelId, ChannelTab.PLAYLISTS, page) { (it as? PlaylistInfoItem)?.let(Mappers::playlist) }
    }

    private fun <T : Any> channelTabPage(
        channelId: ChannelId,
        tab: ChannelTab,
        page: PageToken?,
        map: (InfoItem) -> T?,
    ): Page<T> {
        val handler = youtube.channelTabLHFactory.fromQuery(
            Mappers.channelPath(channelId),
            listOf(Mappers.channelTabName(tab)),
            "",
        )
        return if (page == null) {
            val info = ChannelTabInfo.getInfo(youtube, handler)
            Page(info.relatedItems.mapNotNull(map), PageTokens.encode(info.nextPage))
        } else {
            ChannelTabInfo.getMoreItems(youtube, handler, PageTokens.decode(page)).toPage(map)
        }
    }

    override suspend fun channelFeed(channelId: ChannelId): AppResult<List<Video>> = call {
        Mappers.videos(FeedInfo.getInfo(youtube, channelUrl(channelId)).relatedItems)
    }

    override suspend fun playlist(playlistId: String, page: PageToken?): AppResult<Page<Video>> = call {
        val url = playlistUrl(playlistId)
        if (page == null) {
            val info = PlaylistInfo.getInfo(youtube, url)
            Page(Mappers.videos(info.relatedItems), PageTokens.encode(info.nextPage))
        } else {
            PlaylistInfo.getMoreItems(youtube, url, PageTokens.decode(page)).toPage(Mappers::video)
        }
    }

    override suspend fun comments(videoId: String, page: PageToken?): AppResult<Page<Comment>> = call {
        val url = videoUrl(videoId)
        if (page == null) {
            val info = CommentsInfo.getInfo(youtube, url)
            Page(info.relatedItems.map(Mappers::comment), PageTokens.encode(info.nextPage))
        } else {
            CommentsInfo.getMoreItems(youtube, url, PageTokens.decode(page)).toPage(Mappers::comment)
        }
    }

    private fun <I : InfoItem, T : Any> InfoItemsPage<I>.toPage(map: (I) -> T?): Page<T> =
        Page(items.mapNotNull(map), PageTokens.encode(nextPage))

    private fun searchFilter(filter: SearchFilter): String = when (filter) {
        SearchFilter.ALL -> YoutubeSearchQueryHandlerFactory.ALL
        SearchFilter.VIDEOS -> YoutubeSearchQueryHandlerFactory.VIDEOS
        SearchFilter.CHANNELS -> YoutubeSearchQueryHandlerFactory.CHANNELS
        SearchFilter.PLAYLISTS -> YoutubeSearchQueryHandlerFactory.PLAYLISTS
        SearchFilter.MUSIC_SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
        SearchFilter.MUSIC_VIDEOS -> YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
        SearchFilter.MUSIC_ALBUMS -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
        SearchFilter.MUSIC_ARTISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
        SearchFilter.MUSIC_PLAYLISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
    }

    private companion object {
        // Идентификатор киоска из YoutubeTrendingMusicLinkHandlerFactory (NewPipeExtractor v0.27.1)
        const val MUSIC_KIOSK_ID = "trending_music"
    }
}

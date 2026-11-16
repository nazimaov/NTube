package tv.lumen.data.youtube

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

/**
 * Единственный вход к данным YouTube (SPEC 4.3). Классы org.schabi.newpipe.* за пределы модуля не выходят.
 * Все методы безопасно вызывать из главного потока: работа идёт в Dispatchers.IO.
 */
interface YouTubeRepository {
    suspend fun trending(): AppResult<List<Video>>

    /** Музыкальные чарты YouTube («Музыка в тренде»). */
    suspend fun musicTrending(): AppResult<List<Video>>

    suspend fun search(query: String, filter: SearchFilter, page: PageToken?): AppResult<Page<SearchItem>>

    suspend fun searchSuggestions(query: String): AppResult<List<String>>

    /**
     * Метаданные, потоки и похожие видео. Кэшируется в памяти на 1 час (SPEC 4.4);
     * [forceRefresh] = true после ответа 403/410 от сервера потока.
     */
    suspend fun videoDetails(videoId: String, forceRefresh: Boolean = false): AppResult<VideoDetails>

    /**
     * DASH-манифест для потока с [tv.lumen.core.model.DeliveryMethod.DASH] из последнего [videoDetails].
     * Собирается классами экстрактора (SPEC 4.4), свой генератор не пишем.
     */
    suspend fun dashManifest(videoId: String, streamId: String): AppResult<String>

    suspend fun channel(channelId: ChannelId): AppResult<ChannelDetails>

    /** Вкладки VIDEOS, LIVE, SHORTS. Для PLAYLISTS — [channelPlaylists]. */
    suspend fun channelVideos(channelId: ChannelId, tab: ChannelTab, page: PageToken?): AppResult<Page<Video>>

    suspend fun channelPlaylists(channelId: ChannelId, page: PageToken?): AppResult<Page<PlaylistSummary>>

    /** Быстрый RSS-фид канала для ленты подписок: без длительности, последние ~15 видео. */
    suspend fun channelFeed(channelId: ChannelId): AppResult<List<Video>>

    suspend fun playlist(playlistId: String, page: PageToken?): AppResult<Page<Video>>

    suspend fun comments(videoId: String, page: PageToken?): AppResult<Page<Comment>>
}

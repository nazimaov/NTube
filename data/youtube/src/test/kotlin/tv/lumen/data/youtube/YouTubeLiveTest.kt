package tv.lumen.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.DeliveryMethod
import tv.lumen.core.model.SearchFilter
import tv.lumen.core.model.SearchItem
import tv.lumen.core.network.LumenHttp
import tv.lumen.data.youtube.internal.NewPipeYouTubeRepository

/**
 * Живые запросы к YouTube — критерий готовности фазы 1 (SPEC, раздел 11).
 * Запуск: ./gradlew :data:youtube:liveTest. В обычном `test` пропускаются.
 */
class YouTubeLiveTest {

    companion object {
        /** Big Buck Bunny 60fps 4K, официальный канал Blender. */
        const val TEST_VIDEO = "aqz-KE-bpKQ"
        const val BLENDER_CHANNEL = "UCSMOQeBJ2RAnuFungnQOxLg"

        private lateinit var repo: YouTubeRepository

        @BeforeClass
        @JvmStatic
        fun setUp() {
            assumeTrue("Живые тесты: ./gradlew :data:youtube:liveTest", System.getProperty("lumen.liveTest") == "true")
            repo = NewPipeYouTubeRepository(LumenHttp.baseClientBuilder().build(), Dispatchers.IO)
        }
    }

    private fun <T> AppResult<T>.ok(what: String): T = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> throw AssertionError("$what: $error")
    }

    @Test
    fun trending() = runBlocking {
        val videos = repo.trending().ok("trending")
        println("trending: ${videos.size} видео, первое «${videos.firstOrNull()?.title}»")
        assertTrue("лента трендов пуста", videos.isNotEmpty())
    }

    @Test
    fun searchWithPagination() = runBlocking {
        val first = repo.search("blender open movie", SearchFilter.ALL, null).ok("search")
        println("search: ${first.items.size} результатов, next=${first.next != null}")
        assertTrue(first.items.any { it is SearchItem.VideoItem })
        val next = requireNotNull(first.next) { "нет следующей страницы" }
        val second = repo.search("blender open movie", SearchFilter.ALL, next).ok("search page 2")
        println("search page 2: ${second.items.size}")
        assertTrue(second.items.isNotEmpty())

        val channels = repo.search("blender", SearchFilter.CHANNELS, null).ok("search channels")
        assertTrue(channels.items.all { it is SearchItem.ChannelItem })
    }

    @Test
    fun suggestions() = runBlocking {
        val list = repo.searchSuggestions("blender").ok("suggestions")
        println("suggestions: $list")
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun videoDetailsHasFullHdStreams() = runBlocking {
        val details = repo.videoDetails(TEST_VIDEO).ok("videoDetails")
        val streams = details.streams
        println(
            "video «${details.video.title}»: videoOnly=${streams.videoOnly.size}, audio=${streams.audio.size}, " +
                "muxed=${streams.muxed.size}, subs=${streams.subtitles.size}, max=${streams.maxVideoHeight}p, " +
                "related=${details.related.size}",
        )
        streams.videoOnly.sortedByDescending { it.height }.take(6).forEach {
            println("  ${it.qualityLabel} ${it.codec} ${it.fps}fps ${it.delivery} ${it.mimeType}")
        }
        assertTrue("нет потоков ≥ 1080p", streams.videoOnly.any { it.height >= 1080 })
        assertTrue("нет аудио", streams.audio.isNotEmpty())
        assertTrue(streams.audio.all { it.url.startsWith("http") })
        assertFalse(streams.isLive)

        // Повторный вызов — из кэша, без сети
        val cached = repo.videoDetails(TEST_VIDEO).ok("cached")
        assertTrue(cached.fetchedAt == details.fetchedAt)

        // DASH-манифест, если YouTube отдал потоки с доставкой DASH
        val dash = streams.videoOnly.firstOrNull { it.delivery == DeliveryMethod.DASH }
        if (dash != null) {
            val manifest = repo.dashManifest(TEST_VIDEO, dash.id).ok("dashManifest")
            assertTrue(manifest.contains("<MPD"))
        }
    }

    @Test
    fun channelVideosPlaylistsAndFeed() = runBlocking {
        val channel = repo.channel(BLENDER_CHANNEL).ok("channel")
        println("channel ${channel.channel.name}: tabs=${channel.tabs}, subs=${channel.channel.subscriberCount}")
        assertTrue(channel.tabs.contains(ChannelTab.VIDEOS))

        val videos = repo.channelVideos(BLENDER_CHANNEL, ChannelTab.VIDEOS, null).ok("channelVideos")
        println("channel videos: ${videos.items.size}, next=${videos.next != null}")
        assertTrue(videos.items.isNotEmpty())
        videos.next?.let { assertTrue(repo.channelVideos(BLENDER_CHANNEL, ChannelTab.VIDEOS, it).ok("page 2").items.isNotEmpty()) }

        val feed = repo.channelFeed(BLENDER_CHANNEL).ok("channelFeed")
        println("feed: ${feed.size}, первое опубликовано ${feed.firstOrNull()?.publishedAt}")
        assertTrue(feed.isNotEmpty())

        val playlists = repo.channelPlaylists(BLENDER_CHANNEL, null).ok("channelPlaylists")
        println("playlists: ${playlists.items.size}")
        val first = playlists.items.firstOrNull()
        if (first != null) {
            val playlist = repo.playlist(first.id, null).ok("playlist ${first.id}")
            println("playlist «${first.title}»: ${playlist.items.size} видео")
            assertTrue(playlist.items.isNotEmpty())
        }
    }

    @Test
    fun comments() = runBlocking {
        val page = repo.comments(TEST_VIDEO, null).ok("comments")
        println("comments: ${page.items.size}, next=${page.next != null}")
        assertTrue(page.items.isNotEmpty())
    }
}

package tv.lumen.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
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
    fun musicTrending() = runBlocking {
        val videos = repo.musicTrending().ok("musicTrending")
        println("musicTrending: ${videos.size} видео, первое «${videos.firstOrNull()?.title}»")
        assertTrue("музыкальные чарты пусты", videos.isNotEmpty())
    }

    @Test
    fun regionalPopular() = runBlocking {
        val init = YouTubeInitializer(LumenHttp.baseClientBuilder().build())
        for ((lang, country) in listOf("ru" to "RU", "az" to "AZ")) {
            init.setContentLocale(lang, country)
            val videos = repo.regionalPopular().ok("regionalPopular $country")
            println("regional $country: ${videos.size} видео; " + videos.take(4).joinToString(" | ") { it.title })
            assertTrue("пусто для $country", videos.size >= 10)
        }
        init.setContentLocale("en", "US")
    }

    @Test
    fun trailers() = runBlocking {
        val videos = repo.trailers().ok("trailers")
        println("trailers: ${videos.size} видео, первое «${videos.firstOrNull()?.title}»")
        assertTrue("трейлеры пусты", videos.isNotEmpty())
    }

    @Test
    fun catalogOriginalTurkishSeries() = runBlocking {
        for (q in listOf("Türk dizi tüm bölümler", "турецкий сериал все серии")) {
            val videos = repo.search(q, SearchFilter.VIDEOS, null).ok(q).items.filterIsInstance<SearchItem.VideoItem>().map { it.video }
            println("[$q] " + videos.take(5).joinToString(" | ") { it.title })
            assertTrue(videos.isNotEmpty())
        }
    }

    @Test
    fun catalogSearchFullMovies() = runBlocking {
        val page = repo.search("боевик фильм полностью", SearchFilter.VIDEOS, null).ok("catalog").items
        val videos = page.filterIsInstance<SearchItem.VideoItem>().map { it.video }
        val long = videos.count { it.durationSec >= 2400 }
        println("catalog: ${videos.size} видео, длиннее 40 минут: $long; первое «${videos.firstOrNull()?.title}»")
        assertTrue("нет видео", videos.isNotEmpty())
    }

    @Test
    fun musicSearch() = runBlocking {
        val songs = repo.search("daft punk", SearchFilter.MUSIC_SONGS, null).ok("music songs").items
        val albums = repo.search("daft punk", SearchFilter.MUSIC_ALBUMS, null).ok("music albums").items
        val artists = repo.search("daft punk", SearchFilter.MUSIC_ARTISTS, null).ok("music artists").items
        println("music: песни ${songs.size}, альбомы ${albums.size}, исполнители ${artists.size}; первая песня «${(songs.firstOrNull() as? SearchItem.VideoItem)?.video?.title}»")
        assertTrue("нет песен", songs.any { it is SearchItem.VideoItem })
        assertTrue("нет альбомов", albums.any { it is SearchItem.PlaylistItem })
        assertTrue("нет исполнителей", artists.any { it is SearchItem.ChannelItem })
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

    /** Критерий фазы 4: лента 20 каналов быстрее 10 с при не более 6 запросах одновременно (SPEC 7.1, 11). */
    @Test
    fun feedOf20ChannelsUnder10s() = runBlocking {
        val ids = LinkedHashSet<String>()
        for (q in listOf("music", "news", "science", "gaming", "cooking")) {
            val page = repo.search(q, SearchFilter.CHANNELS, null).ok("search channels $q")
            page.items.filterIsInstance<SearchItem.ChannelItem>().map { it.channel.id }.filter { it.startsWith("UC") }.forEach { ids += it }
            if (ids.size >= 20) break
        }
        val channels = ids.take(20)
        assertTrue("нашлось только ${channels.size} каналов", channels.size == 20)
        val semaphore = kotlinx.coroutines.sync.Semaphore(6)
        val start = System.currentTimeMillis()
        val results = kotlinx.coroutines.coroutineScope {
            channels.map { id -> async { semaphore.withPermit { repo.channelFeed(id) } } }.awaitAll()
        }
        val took = System.currentTimeMillis() - start
        val ok = results.count { it is AppResult.Success }
        println("feed: 20 каналов за $took мс, успешно $ok")
        assertTrue("успешно только $ok из 20", ok >= 18)
        assertTrue("обновление заняло $took мс", took < 10_000)
    }

    @Test
    fun comments() = runBlocking {
        val page = repo.comments(TEST_VIDEO, null).ok("comments")
        println("comments: ${page.items.size}, next=${page.next != null}")
        assertTrue(page.items.isNotEmpty())
    }
}

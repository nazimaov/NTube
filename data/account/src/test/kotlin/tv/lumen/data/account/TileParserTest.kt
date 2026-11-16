package tv.lumen.data.account

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileParserTest {
    private fun videoTile(
        id: String,
        title: String,
        channel: String = "Канал",
        meta: String = "10 тыс. просмотров • 2 дня назад",
        time: String = "10:35",
        style: String = "DEFAULT",
        channelId: String = "UCSMOQeBJ2RAnuFungnQOxLg",
    ) = """
        {"tileRenderer":{"contentType":"TILE_CONTENT_TYPE_VIDEO","style":"TILE_STYLE_YTLR_DEFAULT",
          "header":{"tileHeaderRenderer":{
            "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/small.jpg","width":168},{"url":"https://i.ytimg.com/big.jpg","width":720}]},
            "thumbnailOverlays":[{"thumbnailOverlayTimeStatusRenderer":{"text":{"simpleText":"$time"},"style":"$style"}}]}},
          "metadata":{"tileMetadataRenderer":{"title":{"simpleText":"$title"},
            "lines":[{"lineRenderer":{"items":[{"lineItemRenderer":{"text":{"runs":[{"text":"$channel"}]}}}]}},
                     {"lineRenderer":{"items":[{"lineItemRenderer":{"text":{"simpleText":"$meta"}}}]}}]}},
          "onSelectCommand":{"watchEndpoint":{"videoId":"$id"}},
          "onLongPressCommand":{"showMenuCommand":{"menu":{"menuRenderer":{"items":[
            {"menuNavigationItemRenderer":{"navigationEndpoint":{"browseEndpoint":{"browseId":"$channelId"}}}}]}}}}}}
    """.trimIndent()

    private fun feed(vararg tiles: String, continuation: String? = null): String {
        val cont = continuation?.let { """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$it"}}}}""" }
        return """{"contents":{"tvBrowseRenderer":{"content":{"tvSurfaceContentRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"shelfRenderer":{"content":{"horizontalListRenderer":{"items":[${tiles.joinToString(",")}]}}}}${cont?.let { ",$it" } ?: ""}
        ]}}}}}}}"""
    }

    private fun parse(json: String) = TileParser.parse(Json.parseToJsonElement(json))

    @Test
    fun `плитка видео - все поля`() {
        val result = parse(feed(videoTile("aqz-KE-bpKQ", "Big Buck Bunny", channel = "Blender", time = "1:02:05")))
        val v = result.videos.single()
        assertEquals("aqz-KE-bpKQ", v.id)
        assertEquals("Big Buck Bunny", v.title)
        assertEquals("Blender", v.channelName)
        assertEquals("UCSMOQeBJ2RAnuFungnQOxLg", v.channelId)
        assertEquals("https://i.ytimg.com/big.jpg", v.thumbnailUrl) // самая большая
        assertEquals(3725L, v.durationSec)
        assertEquals("10 тыс. просмотров • 2 дня назад", v.publishedText)
        assertFalse(v.isLive)
        assertFalse(v.isShort)
    }

    @Test
    fun `прямой эфир не получает длительность`() {
        val v = parse(feed(videoTile("live1", "Эфир", time = "LIVE", style = "LIVE"))).videos.single()
        assertTrue(v.isLive)
        assertEquals(-1L, v.durationSec)
    }

    @Test
    fun `каналы и плейлисты пропускаются, повторы схлопываются, порядок сохраняется`() {
        val channel = """{"tileRenderer":{"contentType":"TILE_CONTENT_TYPE_CHANNEL","onSelectCommand":{"browseEndpoint":{"browseId":"UCSMOQeBJ2RAnuFungnQOxLg"}}}}"""
        val playlist = """{"tileRenderer":{"contentType":"TILE_CONTENT_TYPE_PLAYLIST","onSelectCommand":{"watchEndpoint":{"videoId":"pl1","playlistId":"PL1"}}}}"""
        val result = parse(feed(videoTile("b", "Второе"), channel, playlist, videoTile("a", "Первое"), videoTile("b", "Второе")))
        assertEquals(listOf("b", "a"), result.videos.map { it.id })
    }

    @Test
    fun `продолжение ленты - токен следующей страницы`() {
        assertEquals("TOKEN123", parse(feed(videoTile("a", "A"), continuation = "TOKEN123")).next?.value)
        assertNull(parse(feed(videoTile("a", "A"))).next)
        val old = """{"continuations":[{"nextContinuationData":{"continuation":"OLDTOKEN"}}],"x":{"tileRenderer":{"onSelectCommand":{"watchEndpoint":{"videoId":"z"}},"metadata":{"tileMetadataRenderer":{"title":{"simpleText":"Z"}}}}}}"""
        assertEquals("OLDTOKEN", parse(old).next?.value)
    }

    @Test
    fun `Shorts помечаются, плитка без названия отбрасывается`() {
        val short = """{"tileRenderer":{"onSelectCommand":{"reelWatchEndpoint":{"videoId":"sh1"}},"metadata":{"tileMetadataRenderer":{"title":{"simpleText":"Короткое"}}}}}"""
        val noTitle = """{"tileRenderer":{"onSelectCommand":{"watchEndpoint":{"videoId":"nt"}}}}"""
        val result = parse(feed(short, noTitle))
        assertEquals(listOf("sh1"), result.videos.map { it.id })
        assertTrue(result.videos.single().isShort)
    }

    @Test
    fun `длительность и пустой ответ`() {
        assertEquals(635L, TileParser.parseDuration("10:35"))
        assertEquals(-1L, TileParser.parseDuration("LIVE"))
        assertEquals(-1L, TileParser.parseDuration(null))
        val empty = parse("""{"responseContext":{}}""")
        assertTrue(empty.videos.isEmpty())
        assertNull(empty.next)
    }

    @Test
    fun `тело запроса - контекст телевизионного клиента и данные`() {
        val feed = BrowseBody.build("\"browseId\":\"default\"", "VIS", java.util.Locale("ru", "RU"), 180)
        assertTrue(feed.contains("\"clientName\":\"TVHTML5\""))
        assertTrue(feed.contains("\"visitorData\":\"VIS\""))
        assertTrue(feed.contains("\"tvAppInfo\""))
        assertTrue(feed.contains("\"acceptLanguage\":\"ru\",\"acceptRegion\":\"RU\"") && feed.contains("\"utcOffsetMinutes\":\"180\""))
        assertTrue(feed.endsWith("\"browseId\":\"default\"}"))
        // Тело — корректный JSON
        Json.parseToJsonElement(feed)
    }
}


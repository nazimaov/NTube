package tv.lumen.data.sponsorblock.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.lumen.core.model.SponsorCategory
import tv.lumen.core.network.LumenHttp
import java.util.concurrent.TimeUnit

class SponsorBlockClientTest {
    private val server = MockWebServer()
    private lateinit var client: SponsorBlockClient
    private val all = SponsorCategory.entries.toSet()

    @Before
    fun setUp() {
        server.start()
        client = SponsorBlockClient(OkHttpClient(), LumenHttp.json, server.url("/").toString().trimEnd('/'), Dispatchers.IO)
    }

    @After
    fun tearDown() = server.close()

    private val body = """
        [
          {"videoID":"other","segments":[{"segment":[1.0,9.0],"UUID":"x","category":"sponsor","actionType":"skip"}]},
          {"videoID":"pY6biEFAKD4","segments":[
            {"segment":[51.261,67.925],"UUID":"a","category":"sponsor","actionType":"skip","locked":1,"votes":4,"videoDuration":1653.6,"description":""},
            {"segment":[10.0,10.5],"UUID":"short","category":"sponsor","actionType":"skip"},
            {"segment":[5.0,20.0],"UUID":"b","category":"selfpromo","actionType":"skip"},
            {"segment":[100.0,120.0],"UUID":"c","category":"unknown_new","actionType":"skip"}
          ]}
        ]
    """.trimIndent()

    @Test
    fun `префикс хэша - первые 4 символа sha256`() {
        assertEquals("671e", SponsorBlockClient.hashPrefix("aqz-KE-bpKQ"))
    }

    @Test
    fun `разбор ответа - своё видео, без коротких и неизвестных, по порядку`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body).build())
        val segments = client.segments("pY6biEFAKD4", all)
        assertEquals(listOf("b", "a"), segments.map { it.uuid })
        assertEquals(51_261L, segments[1].startMs)
        assertEquals(67_925L, segments[1].endMs)
        val request = server.takeRequest()
        val path = request.target
        assertTrue(path, path.startsWith("/api/skipSegments/${SponsorBlockClient.hashPrefix("pY6biEFAKD4")}?"))
        assertTrue(path, path.contains("actionTypes"))
    }

    @Test
    fun `выключенные категории отбрасываются`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(body).build())
        val segments = client.segments("pY6biEFAKD4", setOf(SponsorCategory.SPONSOR))
        assertEquals(listOf("a"), segments.map { it.uuid })
    }

    @Test
    fun `404 - сегментов нет, это не ошибка`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).body("Not Found").build())
        assertEquals(emptyList<Any>(), client.segments("pY6biEFAKD4", all))
    }

    @Test
    fun `таймаут и мусор в ответе - тихий отказ`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("not json").build())
        assertEquals(emptyList<Any>(), client.segments("pY6biEFAKD4", all))
        server.enqueue(MockResponse.Builder().body(body).headersDelay(5, TimeUnit.SECONDS).build())
        val start = System.currentTimeMillis()
        assertEquals(emptyList<Any>(), client.segments("pY6biEFAKD4", all))
        assertTrue(System.currentTimeMillis() - start < 4_500)
    }
}
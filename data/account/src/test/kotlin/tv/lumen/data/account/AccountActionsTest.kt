package tv.lumen.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.lumen.core.model.Reaction

private class TokenStore(var token: String?) : AccountStore {
    override fun refreshToken() = token
    override fun saveRefreshToken(token: String) { this.token = token }
    override fun clear() { token = null }
    override fun deviceId() = "dev"
}

private const val NEXT_RESPONSE = """{"contents":{"x":{"videoMetadataRenderer":{"likeButton":{"likeButtonRenderer":{"likeStatus":"LIKE"}},"owner":{"subscribeButton":{"subscribeButtonRenderer":{"subscribed":true,"channelId":"UCSMOQeBJ2RAnuFungnQOxLg"}}}}}}}"""

private const val PLAYER_RESPONSE = """{"playabilityStatus":{"status":"OK"},"playbackTracking":{"videostatsWatchtimeUrl":{"baseUrl":"https://s.youtube.com/api/stats/watchtime?ns=yt&el=leanback&ei=EVT&vm=VMON&of=OFV"}}}"""

class AccountActionsTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var likeCode = 200
    private val job = SupervisorJob()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                return when {
                    path == "/tv" -> MockResponse.Builder().body("""<script id="base-js" src="/s/_/k/m=base"></script> "visitorData":"VIS"""").build()
                    path.startsWith("/s/_/k") -> MockResponse.Builder().body("""x={clientId:"abc.apps.googleusercontent.com",s:"SEC"};""").build()
                    path == "/o/oauth2/token" -> MockResponse.Builder().body("""{"access_token":"ACC","expires_in":3600,"token_type":"Bearer"}""").build()
                    path.startsWith("/yt/like/") || path.startsWith("/yt/subscription/") -> MockResponse.Builder().code(likeCode).body("{}").build()
                    path == "/yt/player" -> MockResponse.Builder().body(PLAYER_RESPONSE).build()
                    path.startsWith("/stats/") -> MockResponse.Builder().body("").build()
                    path == "/yt/next" -> MockResponse.Builder().body(NEXT_RESPONSE).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        job.cancel()
        server.close()
    }

    private fun actions(token: String?): AccountActions {
        val client = OkHttpClient()
        val json = Json { ignoreUnknownKeys = true }
        val account = GoogleAccount(
            client, json, TokenStore(token), CoroutineScope(job), Dispatchers.IO,
            tvPageUrl = server.url("/tv").toString(),
            oauthBase = server.url("/o/oauth2").toString().trimEnd('/'),
            revokeUrl = server.url("/revoke").toString(),
        )
        val feed = AccountFeed(client, json, account, Dispatchers.IO, browseUrl = server.url("/yt/browse").toString())
        return AccountActions(client, account, feed, Dispatchers.IO, baseUrl = server.url("/yt").toString().trimEnd('/'))
    }

    private fun likeRequests() = requests.filter { it.url.encodedPath.startsWith("/yt/like/") }

    @Test
    fun `подписка и отписка уходят с идентификатором канала`() = runBlocking {
        val actions = actions("REF")
        assertTrue(actions.setSubscribed("UCSMOQeBJ2RAnuFungnQOxLg", true))
        assertTrue(actions.setSubscribed("UCSMOQeBJ2RAnuFungnQOxLg", false))
        assertTrue(actions.setSubscribed("@blender", true)) // ручка без UC — не отправляется
        val paths = requests.filter { it.url.encodedPath.startsWith("/yt/subscription/") }
        assertEquals(listOf("/yt/subscription/subscribe", "/yt/subscription/unsubscribe"), paths.map { it.url.encodedPath })
        assertTrue(paths.first().body!!.utf8().contains("\"channelIds\":[\"UCSMOQeBJ2RAnuFungnQOxLg\"]"))
    }

    @Test
    fun `отметка и подписка читаются из ответа next`() = runBlocking {
        val state = actions("REF").videoState("vid1")
        assertEquals(Reaction.LIKE, state?.reaction)
        assertEquals(true, state?.subscribed)
        assertTrue(requests.last { it.url.encodedPath == "/yt/next" }.body!!.utf8().contains("\"videoId\":\"vid1\""))
    }

    @Test
    fun `без входа состояние видео не запрашивается`() = runBlocking {
        assertEquals(null, actions(null).videoState("vid1"))
        assertTrue(requests.none { it.url.encodedPath == "/yt/next" })
    }

    @Test
    fun `лайк и дизлайк уходят нужными запросами с токеном`() = runBlocking {
        val actions = actions("REF")
        assertTrue(actions.react("vid1", before = null, now = Reaction.LIKE))
        assertTrue(actions.react("vid1", before = Reaction.LIKE, now = Reaction.DISLIKE))
        assertEquals(listOf("/yt/like/like", "/yt/like/dislike"), likeRequests().map { it.url.encodedPath })
        val first = likeRequests().first()
        assertEquals("Bearer ACC", first.headers["Authorization"])
        assertEquals("VIS", first.headers["X-Goog-Visitor-Id"])
        val body = first.body!!.utf8()
        assertTrue(body.contains("\"target\":{\"videoId\":\"vid1\"}"))
        assertTrue(body.contains("\"clientName\":\"TVHTML5\""))
    }

    @Test
    fun `снятие отметки выбирает обратный запрос по прежнему значению`() = runBlocking {
        val actions = actions("REF")
        assertTrue(actions.react("v", before = Reaction.LIKE, now = null))
        assertTrue(actions.react("v", before = Reaction.DISLIKE, now = null))
        assertTrue(actions.react("v", before = null, now = null)) // нечего снимать
        assertEquals(listOf("/yt/like/removelike", "/yt/like/removedislike"), likeRequests().map { it.url.encodedPath })
    }

    @Test
    fun `без входа ничего не отправляется`() = runBlocking {
        val actions = actions(null)
        assertTrue(actions.react("v", before = null, now = Reaction.LIKE))
        assertTrue(likeRequests().isEmpty())
    }

    @Test
    fun `ошибка сервера - false, приложение не падает`() = runBlocking {
        likeCode = 500
        assertFalse(actions("REF").react("v", before = null, now = Reaction.LIKE))
    }

    private fun reporter(token: String?): WatchReporter {
        val a = actions(token)
        val client = OkHttpClient()
        val json = Json { ignoreUnknownKeys = true }
        val account = GoogleAccount(
            client, json, TokenStore(token), CoroutineScope(job), Dispatchers.IO,
            tvPageUrl = server.url("/tv").toString(),
            oauthBase = server.url("/o/oauth2").toString().trimEnd('/'),
            revokeUrl = server.url("/revoke").toString(),
        )
        return WatchReporter(client, account, a, Dispatchers.IO, server.url("/stats").toString().trimEnd('/'))
    }

    private fun statsRequests() = requests.filter { it.url.encodedPath.startsWith("/stats/") }

    @Test
    fun `просмотр - запись и обновление времени с параметрами плеера`() = runBlocking {
        val r = reporter("REF")
        var now = 1_000_000L
        r.clock = { now }
        r.report("vid1", 5f, 600f) // слишком рано
        assertTrue(statsRequests().isEmpty())
        r.report("vid1", 20f, 600f)
        assertEquals(listOf("/stats/playback", "/stats/watchtime"), statsRequests().map { it.url.encodedPath })
        val first = statsRequests().first().url
        assertEquals("vid1", first.queryParameter("docid"))
        assertEquals("EVT", first.queryParameter("ei"))
        assertEquals("VMON", first.queryParameter("vm"))
        assertEquals("OFV", first.queryParameter("of"))
        assertEquals(16, first.queryParameter("cpn")!!.length)
        r.report("vid1", 30f, 600f) // раньше интервала
        assertEquals(2, statsRequests().size)
        now += 31_000
        r.report("vid1", 51f, 600f)
        assertEquals(3, statsRequests().size)
        val update = statsRequests().last().url
        assertEquals("/stats/watchtime", update.encodedPath)
        assertEquals("20.000", update.queryParameter("st"))
        assertEquals("51.000", update.queryParameter("et"))
        assertEquals(first.queryParameter("cpn"), update.queryParameter("cpn"))
    }

    @Test
    fun `досмотренное видео - финальный отчёт один раз`() = runBlocking {
        val r = reporter("REF")
        r.report("vid1", 580f, 600f)
        r.report("vid1", 590f, 600f)
        val finals = statsRequests().filter { it.url.queryParameter("final") == "1" }
        assertEquals(2, finals.size) // playback + watchtime
    }

    @Test
    fun `без входа просмотр не отправляется`() = runBlocking {
        reporter(null).report("vid1", 100f, 600f)
        assertTrue(requests.none { it.url.encodedPath.startsWith("/stats/") || it.url.encodedPath == "/yt/player" })
    }
}

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
                    path.startsWith("/yt/like/") -> MockResponse.Builder().code(likeCode).body("{}").build()
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
}

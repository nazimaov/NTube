package tv.lumen.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeStore : AccountStore {
    var token: String? = null
    override fun refreshToken() = token
    override fun saveRefreshToken(token: String) { this.token = token }
    override fun clear() { token = null }
    override fun deviceId() = "device123"
}

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleAccountTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var tokenAnswers = ArrayDeque<String>()
    private val store = FakeStore()
    private val scopeJob = SupervisorJob()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                return when {
                    path == "/tv" -> MockResponse.Builder().body("""<script id="base-js" src="/s/_/kabuki/m=base"></script>""").build()
                    path.startsWith("/s/_/kabuki") -> MockResponse.Builder().body("""x={clientId:"abc-123.apps.googleusercontent.com",
secret:"S3cr3t"};y={clientId:"other.apps.googleusercontent.com",secret:"wrong"}""").build()
                    path == "/o/oauth2/device/code" -> MockResponse.Builder().body(
                        """{"device_code":"DEV","user_code":"ABC-DEF-GHI","verification_url":"https://www.youtube.com/activate","expires_in":300,"interval":0}""",
                    ).build()
                    path == "/o/oauth2/token" -> MockResponse.Builder().code(if (tokenAnswers.isEmpty()) 400 else 200)
                        .body(tokenAnswers.removeFirstOrNull() ?: """{"error":"authorization_pending"}""").build()
                    path == "/revoke" -> MockResponse.Builder().code(200).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
        server.close()
    }

    private fun account() = GoogleAccount(
        client = OkHttpClient(),
        json = Json { ignoreUnknownKeys = true },
        store = store,
        scope = CoroutineScope(scopeJob),
        io = kotlinx.coroutines.Dispatchers.IO,
        tvPageUrl = server.url("/tv").toString(),
        oauthBase = server.url("/o/oauth2").toString().trimEnd('/'),
        revokeUrl = server.url("/revoke").toString(),
    ).also { it.minPollMs = 10 }

    private suspend fun GoogleAccount.waitFor(predicate: (AccountState) -> Boolean) =
        withTimeout(10_000) { state.first(predicate) }

    @Test
    fun `клиент берётся из первого совпадения в скрипте YouTube TV`() {
        val script = """a={clientId:"first-1.apps.googleusercontent.com",k:"AAA"};b={clientId:"second.apps.googleusercontent.com",k:"BBB"}"""
        assertEquals(ClientData("first-1.apps.googleusercontent.com", "AAA"), ClientDataParser.clientData(script))
        assertEquals("https://www.youtube.com/s/a/m=base", ClientDataParser.scriptUrl("""<script id="base-js" src="\/s\/a\/m\x3dbase">"""))
        assertEquals("/exm=base/ed=1/x/m=main", ClientDataParser.legacyScriptUrl("/dg=0/x/m=base"))
    }

    @Test
    fun `вход по коду - ждёт подтверждения и сохраняет токен`() = runBlocking {
        tokenAnswers += """{"error":"authorization_pending"}"""
        tokenAnswers += """{"access_token":"ACC","refresh_token":"REF","expires_in":3600,"token_type":"Bearer"}"""
        val account = account()
        account.startSignIn()
        val waiting = account.waitFor { it is AccountState.WaitingForCode } as AccountState.WaitingForCode
        assertEquals("ABC-DEF-GHI", waiting.userCode)
        account.waitFor { it == AccountState.SignedIn }
        assertEquals("REF", store.token)
        assertEquals("Bearer ACC", account.authorizationHeader())

        val codeRequest = requests.first { it.url.encodedPath.endsWith("/device/code") }
        val body = codeRequest.body!!.utf8()
        assertTrue(body.contains("\"client_id\":\"abc-123.apps.googleusercontent.com\""))
        assertTrue(body.contains("ytlr::") && body.contains("youtube-paid-content") && body.contains("device123"))
        assertTrue(codeRequest.headers["User-Agent"]!!.contains("Cobalt"))
        // Вторым запросом идёт опрос с секретом из первого совпадения
        assertTrue(requests.last { it.url.encodedPath.endsWith("/token") }.body!!.utf8().contains("\"client_secret\":\"S3cr3t\""))
    }

    @Test
    fun `отказ пользователя - ошибка и токена нет`() = runBlocking {
        tokenAnswers += """{"error":"access_denied"}"""
        val account = account()
        account.startSignIn()
        val failed = account.waitFor { it is AccountState.Failed } as AccountState.Failed
        assertEquals(AccountState.Reason.DENIED, failed.reason)
        assertNull(store.token)
    }

    @Test
    fun `заголовок обновляется по refresh-токену и кешируется`() = runBlocking {
        store.token = "OLD_REF"
        tokenAnswers += """{"access_token":"NEW","expires_in":3600,"token_type":"Bearer"}"""
        val account = account()
        assertEquals(AccountState.SignedIn, account.state.value)
        assertEquals("Bearer NEW", account.authorizationHeader())
        assertEquals("Bearer NEW", account.authorizationHeader())
        assertEquals(1, requests.count { it.url.encodedPath.endsWith("/token") })
        assertTrue(requests.last { it.url.encodedPath.endsWith("/token") }.body!!.utf8().contains("\"grant_type\":\"refresh_token\""))
    }

    @Test
    fun `доступ отозван в Google - токен удаляется, нужен новый вход`() = runBlocking {
        store.token = "REF"
        tokenAnswers += """{"error":"invalid_grant"}"""
        val account = account()
        assertNull(account.authorizationHeader())
        assertNull(store.token)
        assertEquals(AccountState.SignedOut, account.state.value)
    }

    @Test
    fun `выход - токен удалён и отозван у Google`() = runBlocking {
        store.token = "REF"
        val account = account()
        account.signOut()
        assertNull(store.token)
        assertEquals(AccountState.SignedOut, account.state.value)
        val deadline = System.currentTimeMillis() + 5000
        while (requests.none { it.url.encodedPath == "/revoke" } && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val revoke = requests.firstOrNull { it.url.encodedPath == "/revoke" }
        assertNotNull(revoke)
        assertEquals("REF", revoke!!.url.queryParameter("token"))
    }
}

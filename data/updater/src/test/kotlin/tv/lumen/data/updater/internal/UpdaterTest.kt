package tv.lumen.data.updater.internal

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.lumen.core.network.LumenHttp
import tv.lumen.data.updater.UpdateConfig
import java.lang.reflect.Proxy

class UpdaterTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    /** Context с SharedPreferences в памяти — Android SDK в JVM-тестах не нужен. */
    private fun fakeContext(): Context {
        val store = HashMap<String, Any?>()
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, m, args ->
            when (m.name) {
                "putLong" -> { store[args!![0] as String] = args[1]; proxy }
                "apply" -> null
                "commit" -> true
                else -> proxy
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            when (m.name) {
                "getLong" -> (store[args!![0] as String] as? Long) ?: args[1]
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
        return object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int) = prefs
        }
    }

    private fun manager(version: String, owner: String = "owner") = GitHubUpdateManager(
        fakeContext(),
        OkHttpClient(),
        LumenHttp.json,
        UpdateConfig(owner, "lumen", version),
        server.url("/").toString().trimEnd('/'),
        Dispatchers.IO,
    )

    private val release = """
        {"tag_name":"v1.0.1","body":"Обновлён NewPipeExtractor","draft":false,"prerelease":false,
         "assets":[{"name":"notes.txt","size":10,"browser_download_url":"https://x/notes.txt"},
                   {"name":"lumen-1.0.1.apk","size":12345,"browser_download_url":"https://x/lumen-1.0.1.apk"}]}
    """.trimIndent()

    @Test
    fun `сравнение версий`() {
        assertTrue(Versions.isNewer("v1.0.1", "1.0.0"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("2.0", "1.99.99"))
        assertFalse(Versions.isNewer("v1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("1.0.0", "1.0.1"))
        assertFalse(Versions.isNewer("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun `новая версия - первый apk из assets`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(release).build())
        val m = manager("1.0.0")
        val latest = m.fetchLatest()!!
        val info = m.toInfo(latest)!!
        assertEquals("1.0.1", info.version)
        assertEquals("https://x/lumen-1.0.1.apk", info.apkUrl)
        assertEquals(12345L, info.sizeBytes)
        assertEquals("/repos/owner/lumen/releases/latest", server.takeRequest().target)
    }

    @Test
    fun `проверка не чаще раза в 3 часа без force`() = runBlocking {
        val m = manager("1.0.0")
        var now = 1_800_000_000_000L // реальное время эпохи: дальше трёх часов от «никогда»
        m.clock = { now }
        server.enqueue(MockResponse.Builder().body(release).build())
        m.check(force = false)
        assertEquals(1, server.requestCount)
        now += 60 * 60 * 1000L
        m.check(force = false)
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse.Builder().body(release).build())
        m.check(force = true)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `ошибки сети - null без исключения`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertNull(manager("1.0.0").fetchLatest())
    }

    @Test
    fun `владелец не задан - проверка не идёт`() = runBlocking {
        manager("1.0.0", owner = "CHANGE_ME").check(force = true, userInitiated = true)
        assertEquals(0, server.requestCount)
    }
}
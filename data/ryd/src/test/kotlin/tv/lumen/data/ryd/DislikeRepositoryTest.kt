package tv.lumen.data.ryd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.lumen.core.network.LumenHttp
import java.util.concurrent.TimeUnit

class DislikeRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: DislikeRepository

    @Before
    fun setUp() {
        server.start()
        repo = DislikeRepository(OkHttpClient(), LumenHttp.json, server.url("/").toString().trimEnd('/'), Dispatchers.IO)
    }

    @After fun tearDown() = server.close()

    @Test
    fun `число дизлайков из ответа`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"id":"aqz-KE-bpKQ","likes":110433,"dislikes":18674,"rating":4.42,"viewCount":23543287,"deleted":false}""").build())
        assertEquals(18674L, repo.dislikes("aqz-KE-bpKQ"))
        assertEquals("/votes?videoId=aqz-KE-bpKQ", server.takeRequest().target)
    }

    @Test
    fun `404 400 и таймаут - null без исключения`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertNull(repo.dislikes("x"))
        server.enqueue(MockResponse.Builder().code(400).build())
        assertNull(repo.dislikes("x"))
        server.enqueue(MockResponse.Builder().body("{}").headersDelay(5, TimeUnit.SECONDS).build())
        val start = System.currentTimeMillis()
        assertNull(repo.dislikes("x"))
        assertTrue(System.currentTimeMillis() - start < 4_500)
    }
}
package tv.lumen.data.youtube.internal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.Page

class PageTokensAndCacheTest {

    @Test
    fun `страница экстрактора переживает токен`() {
        val page = Page("https://www.youtube.com/youtubei/v1/search", "id-1", byteArrayOf(1, 2, 3))
        val restored = PageTokens.decode(requireNotNull(PageTokens.encode(page)))
        assertEquals(page.url, restored.url)
        assertEquals(page.id, restored.id)
        assertArrayEquals(page.body, restored.body)
        assertNull(PageTokens.encode(null))
    }

    @Test
    fun `кэш забывает запись после ttl`() {
        var now = 0L
        val cache = TimedCache<String, String>(ttlMillis = 1000, maxSize = 10) { now }
        cache.put("a", "1")
        now = 999
        assertEquals("1", cache.get("a"))
        now = 1000
        assertNull(cache.get("a"))
    }

    @Test
    fun `кэш вытесняет давно не используемые`() {
        val cache = TimedCache<String, String>(ttlMillis = 1000, maxSize = 2) { 0 }
        cache.put("a", "1")
        cache.put("b", "2")
        cache.get("a") // a свежее b
        cache.put("c", "3")
        assertEquals("1", cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("3", cache.get("c"))
    }
}

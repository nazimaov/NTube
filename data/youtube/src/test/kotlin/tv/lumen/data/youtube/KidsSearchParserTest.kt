package tv.lumen.data.youtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.lumen.data.youtube.internal.KidsSearchParser

class KidsSearchParserTest {
    @Test
    fun parsesVideosFromKidsSearch() {
        val text = javaClass.classLoader!!.getResource("kids_search.json")!!.readText()
        val videos = KidsSearchParser.parse(JSONObject(text))
        assertEquals(3, videos.size)
        val first = videos.first()
        assertEquals("18j6GMU8mz4", first.id)
        assertTrue(first.title.startsWith("Nastya ve Stacy"))
        assertEquals(273L, first.durationSec)
        assertEquals("Like Nastya TR", first.channelName)
        assertEquals("UCYN6BOm9iRBCNlzrI-IX7mA", first.channelId)
        assertTrue(first.thumbnailUrl!!.contains("sddefault"))
    }
}

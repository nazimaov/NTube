package tv.lumen.data.dearrow.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.lumen.core.network.LumenHttp

class DeArrowTest {
    private val body = """
        {"pY6biEFAKD4":{"titles":[{"title":"POV of my >son building a PC","original":false,"votes":1,"locked":false,"UUID":"a"}],
                        "thumbnails":[{"timestamp":235.34,"original":false,"votes":0,"locked":false,"UUID":"b"}],
                        "randomTime":0.71,"videoDuration":1653.6},
         "other":{"titles":[],"thumbnails":[]}}
    """.trimIndent()

    @Test
    fun `заголовок и кадр из первого элемента, без символов больше`() {
        val dto = LumenHttp.json.decodeFromString<Map<String, BrandingDto>>(body).getValue("pY6biEFAKD4")
        assertEquals("POV of my son building a PC", DeArrowClient.pickTitle(dto.titles))
        assertEquals(235.34, DeArrowClient.pickThumbnail(dto.thumbnails)!!, 0.001)
    }

    @Test
    fun `отрицательные голоса без блокировки и original - оставить оригинал`() {
        assertNull(DeArrowClient.pickTitle(listOf(TitleDto("x", votes = -1))))
        assertEquals("x", DeArrowClient.pickTitle(listOf(TitleDto("x", votes = -1, locked = true))))
        assertNull(DeArrowClient.pickTitle(listOf(TitleDto("orig", original = true, votes = 5))))
        assertNull(DeArrowClient.pickThumbnail(listOf(ThumbnailDto(10.0, original = true))))
        assertNull(DeArrowClient.pickTitle(emptyList()))
    }

    @Test
    fun `символ больше убирается только перед словом`() {
        assertEquals("a > b and word", DeArrowClient.cleanTitle("a > b and >word"))
    }
}
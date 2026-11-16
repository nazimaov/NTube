package tv.lumen.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.lumen.core.model.Video

class ChannelsOfTest {
    private fun v(id: String, ch: String?, name: String?) = Video(id, id, ch, name, null, 60, -1, null, null, false, false)

    @Test
    fun `каналы из ленты без повторов в порядке появления, без неизвестных`() {
        val result = channelsOf(
            listOf(v("1", "UCa", "A"), v("2", "UCb", "B"), v("3", "UCa", "A"), v("4", null, "X"), v("5", "UCc", "")),
        )
        assertEquals(listOf("UCa", "UCb"), result.map { it.id })
        assertEquals(listOf("A", "B"), result.map { it.name })
    }
}

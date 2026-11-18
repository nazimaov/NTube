package tv.lumen.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.lumen.core.model.VideoCodec

class QualityBadgeTest {
    private fun q(h: Int) = QualityOption(h, 30, VideoCodec.AVC)

    @Test
    fun badgeFollowsYouTubeWeb() {
        assertNull(q(480).badge)
        assertEquals("HD", q(720).badge)
        assertEquals("HD", q(1080).badge)
        assertEquals("2K", q(1440).badge)
        assertEquals("4K", q(2160).badge)
        assertEquals("8K", q(4320).badge)
    }
}

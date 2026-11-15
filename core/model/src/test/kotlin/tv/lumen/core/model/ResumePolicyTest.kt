package tv.lumen.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePolicyTest {
    @Test
    fun `продолжение только между 5 и 95 процентами`() {
        assertNull(ResumePolicy.resumePosition(4_000, 100_000))
        assertEquals(5_000L, ResumePolicy.resumePosition(5_000, 100_000))
        assertEquals(50_000L, ResumePolicy.resumePosition(50_000, 100_000))
        assertEquals(95_000L, ResumePolicy.resumePosition(95_000, 100_000))
        assertNull(ResumePolicy.resumePosition(96_000, 100_000))
        assertNull(ResumePolicy.resumePosition(10_000, 0))
        assertNull(ResumePolicy.resumePosition(0, 100_000))
    }
}
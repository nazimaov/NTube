package tv.lumen.player.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.lumen.core.model.PlaybackOrder
import tv.lumen.core.model.Video
import kotlin.random.Random

class PlaybackQueueTest {
    private fun v(id: String, live: Boolean = false) = Video(id, id, "C", "C", null, 60, 1, null, null, live, false)

    @Test
    fun `кнопка меняет режим по кругу`() {
        assertEquals(PlaybackOrder.REPEAT_ONE, PlaybackOrder.SEQUENTIAL.next())
        assertEquals(PlaybackOrder.SHUFFLE, PlaybackOrder.REPEAT_ONE.next())
        assertEquals(PlaybackOrder.SEQUENTIAL, PlaybackOrder.SHUFFLE.next())
    }

    @Test
    fun `вперемешку - из плейлиста, без текущего`() {
        val playlist = listOf(v("a"), v("b"), v("c"))
        repeat(50) { seed ->
            val next = PlaybackQueue.shuffleNext(playlist, listOf(v("r")), currentId = "b", random = Random(seed))
            assertNotEquals("b", next?.id)
            assertTrue(next?.id in setOf("a", "c"))
        }
    }

    @Test
    fun `без плейлиста - из первых похожих, без эфиров`() {
        val related = listOf(v("live", live = true)) + (1..20).map { v("r$it") }
        repeat(50) { seed ->
            val next = PlaybackQueue.shuffleNext(emptyList(), related, currentId = "x", random = Random(seed))
            val index = related.indexOf(next)
            assertTrue("из первых ${PlaybackQueue.RELATED_POOL}", index in 1 until PlaybackQueue.RELATED_POOL)
        }
        assertNull(PlaybackQueue.shuffleNext(emptyList(), emptyList(), currentId = "x"))
    }
}

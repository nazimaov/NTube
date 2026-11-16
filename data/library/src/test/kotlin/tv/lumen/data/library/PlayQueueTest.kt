package tv.lumen.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.lumen.core.model.Video

class PlayQueueTest {
    private fun v(id: String) = Video(id, id, "C", "C", null, 60, 1, null, null, false, false)
    private fun PlayQueue.ids() = items.value.map { it.id }

    private fun queue(vararg ids: String) = PlayQueue().apply { ids.forEach { add(v(it)) } }

    @Test
    fun `добавление в конец, без повторов`() {
        val q = queue("a", "b")
        assertTrue(q.add(v("c")))
        assertFalse(q.add(v("a")))
        assertEquals(listOf("a", "b", "c"), q.ids())
        assertTrue(q.contains("b"))
    }

    @Test
    fun `удаление и очистка`() {
        val q = queue("a", "b", "c")
        q.remove("b")
        assertEquals(listOf("a", "c"), q.ids())
        q.clear()
        assertTrue(q.ids().isEmpty())
    }

    @Test
    fun `перемещение вдоль списка, за границы не выходит`() {
        val q = queue("a", "b", "c")
        q.move("c", -1)
        assertEquals(listOf("a", "c", "b"), q.ids())
        q.move("a", -1)
        assertEquals(listOf("a", "c", "b"), q.ids())
        q.move("a", +1)
        assertEquals(listOf("c", "a", "b"), q.ids())
        q.move("b", +1)
        assertEquals(listOf("c", "a", "b"), q.ids())
        q.move("нет", 1)
        assertEquals(listOf("c", "a", "b"), q.ids())
    }

    @Test
    fun `следующее после текущего, первое если текущего нет, конец очереди - null`() {
        val q = queue("a", "b", "c")
        assertEquals("b", q.nextAfter("a")?.id)
        assertEquals("a", q.nextAfter("вне очереди")?.id)
        assertEquals("a", q.nextAfter(null)?.id)
        assertNull(q.nextAfter("c"))
        assertNull(PlayQueue().nextAfter("a"))
    }
}

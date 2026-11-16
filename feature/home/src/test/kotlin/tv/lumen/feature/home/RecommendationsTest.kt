package tv.lumen.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.lumen.core.model.Video

class RecommendationsTest {
    private fun v(id: String, ch: String = "C$id", live: Boolean = false, short: Boolean = false) =
        Video(id, "T $id", ch, "Ch", null, 60, 1, null, null, live, short)

    @Test
    fun `источники - сначала лайки, потом история, без дизлайков и повторов`() {
        val seeds = Recommendations.seeds(
            liked = listOf("l1", "l2", "l3", "l4"),
            recent = listOf("h1", "l1", "d", "h2"),
            disliked = setOf("d"),
            max = 6,
        )
        assertEquals(listOf("l1", "l2", "l3", "h1", "h2", "l4"), seeds)
    }

    @Test
    fun `смешивание по кругу без повторов и исключённых`() {
        val a = listOf(v("a1"), v("shared"), v("a2"))
        val b = listOf(v("watched"), v("b1"), v("shared"), v("b2"))
        val mixed = Recommendations.mix(listOf(a, b), exclude = setOf("watched"))
        assertEquals(listOf("a1", "b1", "shared", "b2", "a2"), mixed.map { it.id })
    }

    @Test
    fun `не больше трёх видео одного канала, без эфиров и Shorts`() {
        val one = (1..5).map { v("x$it", ch = "same") } + v("live", live = true) + v("short", short = true) + v("other")
        val mixed = Recommendations.mix(listOf(one), exclude = emptySet(), maxPerChannel = 3)
        assertEquals(listOf("x1", "x2", "x3", "other"), mixed.map { it.id })
    }

    @Test
    fun `лимит соблюдается`() {
        val many = (1..100).map { v("v$it") }
        assertEquals(10, Recommendations.mix(listOf(many), exclude = emptySet(), limit = 10).size)
    }
}

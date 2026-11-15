package tv.lumen.data.library

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.lumen.core.model.Video

class FilterRulesTest {
    private fun v(id: String, title: String = id, dur: Long = 600, short: Boolean = false, ch: String = "UC1") =
        Video(id, title, ch, "Ch", null, dur, 0, null, null, isLive = false, isShort = short)

    private val list = listOf(
        v("long"),
        v("shortFlag", short = true),
        v("shortDur", dur = 45),
        v("unknownDur", dur = -1),
        v("watched"),
        v("blockedCh", ch = "UCbad"),
        v("clickbait", title = "You WON'T believe this"),
    )

    @Test
    fun `без правил всё видно`() {
        assertEquals(list.size, FilterRules().videos(list).size)
    }

    @Test
    fun `скрыть shorts по признаку и длительности до 60 с`() {
        val ids = FilterRules(hideShorts = true).videos(list).map { it.id }
        assertEquals(listOf("long", "unknownDur", "watched", "blockedCh", "clickbait"), ids)
    }

    @Test
    fun `скрыть просмотренные от 90 процентов`() {
        val rules = FilterRules(hideWatched = true, progress = mapOf("watched" to 0.92f, "long" to 0.5f))
        assertEquals(false, rules.allows(list[4]))
        assertEquals(true, rules.allows(list[0]))
    }

    @Test
    fun `чёрный список каналов и слов без учёта регистра`() {
        val rules = FilterRules(blockedChannels = setOf("UCbad"), blockedWords = setOf("won't BELIEVE"))
        assertEquals(listOf("long", "shortFlag", "shortDur", "unknownDur", "watched"), rules.videos(list).map { it.id })
    }
}
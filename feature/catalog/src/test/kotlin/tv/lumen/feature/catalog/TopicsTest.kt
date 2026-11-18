package tv.lumen.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {
    @Test
    fun atmosphereIsFirstAndAmbient() {
        assertEquals("atmosphere", Topics.all.first().id)
        assertTrue(Topics.all.first().ambient)
    }

    @Test
    fun everyTopicHasQueriesInEveryLanguage() {
        for (topic in Topics.all) {
            assertTrue(topic.subtopics.isNotEmpty())
            for (query in listOf(topic.query) + topic.subtopics.map { it.query }) {
                for (lang in QueryLang.entries) assertTrue("${topic.id} $lang", query.at(lang).isNotBlank())
            }
        }
    }

    @Test
    fun idsAreUnique() {
        assertEquals(Topics.all.size, Topics.all.map { it.id }.toSet().size)
        for (topic in Topics.all) assertEquals(topic.subtopics.size, topic.subtopics.map { it.id }.toSet().size)
    }
}

class LiveChannelsTest {
    private val sports = Topics.byId("sports")!!

    @Test
    fun everyLanguageHasLiveChannels() {
        val live = sports.live!!
        for (lang in QueryLang.entries) assertTrue("$lang", live.channels[lang].orEmpty().size >= 4)
    }

    @Test
    fun footballAndBoxingHaveLiveKeywords() {
        for (id in listOf("football", "basketball", "fight", "motor")) {
            assertTrue(id, sports.subtopics.first { it.id == id }.liveKeywords.isNotEmpty())
        }
    }
}

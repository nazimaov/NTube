package tv.lumen.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.lumen.core.model.Video
import tv.lumen.data.youtube.internal.regionalQueries
import tv.lumen.data.youtube.internal.titleFitsRegion
import tv.lumen.data.youtube.internal.trailerQueries
import tv.lumen.data.youtube.internal.trendScore
import java.time.Duration
import java.time.Instant

class TrendScoreTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    private fun video(views: Long, daysAgo: Long?) = Video(
        id = "v$views$daysAgo", title = "t", channelId = null, channelName = null, thumbnailUrl = null,
        durationSec = 200, viewCount = views, publishedAt = daysAgo?.let { now.minus(Duration.ofDays(it)) },
        publishedText = null, isLive = false, isShort = false,
    )

    @Test
    fun moreViewsRankHigherAtSameAge() {
        assertTrue(trendScore(video(2_000_000, 10), now) > trendScore(video(1_000_000, 10), now))
    }

    @Test
    fun fresherRanksHigherAtSameViews() {
        assertTrue(trendScore(video(1_000_000, 3), now) > trendScore(video(1_000_000, 300), now))
    }

    @Test
    fun unknownViewsGoLast() {
        assertEquals(0.0, trendScore(video(-1, 1), now), 0.0)
        assertTrue(trendScore(video(1_000, 100), now) > 0.0)
    }

    @Test
    fun regionalQueriesUseRegionLanguageAndCountryName() {
        val az = regionalQueries("AZ")
        assertTrue(az.any { it.contains("populyar") })
        assertTrue(az.any { it.contains("Azərbaycan") })
        val tr = regionalQueries("TR")
        assertTrue(tr.any { it.contains("popüler") })
        assertTrue(regionalQueries("RU").any { it.contains("популярн") })
    }

    @Test
    fun titleScriptFilter() {
        assertTrue(titleFitsRegion("Yeni mahnı 2026", "az"))
        assertTrue(titleFitsRegion("Новые песни 2026", "az"))
        assertTrue(!titleFitsRegion("Новые песни 2026", "tr"))
        assertTrue(titleFitsRegion("Новые песни 2026", "ru"))
        assertTrue(!titleFitsRegion("Best songs 2026", "ru"))
    }

    @Test
    fun trailerQueriesFollowRegionLanguage() {
        assertTrue(trailerQueries("az").any { it.contains("treyler") })
        // В Азербайджане нужны и азербайджанские, и турецкие трейлеры
        assertTrue(trailerQueries("az").any { it.contains("fragman") })
        assertTrue(trailerQueries("tr").any { it.contains("fragman") })
        assertTrue(trailerQueries("ru").any { it.contains("трейлер") })
        assertTrue(trailerQueries("de").any { it.contains("trailer") })
    }
}

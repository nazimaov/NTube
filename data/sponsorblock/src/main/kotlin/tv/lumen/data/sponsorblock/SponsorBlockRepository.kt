package tv.lumen.data.sponsorblock

import tv.lumen.core.model.Segment
import tv.lumen.core.model.SponsorCategory

/** Сегменты SponsorBlock (SPEC 6.1). Сбой мода не мешает воспроизведению: при любой ошибке — пустой список. */
interface SponsorBlockRepository {
    suspend fun segments(videoId: String, categories: Set<SponsorCategory>): List<Segment>
}
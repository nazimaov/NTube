package tv.lumen.feature.home

import tv.lumen.core.model.Video

/**
 * Рекомендации без аккаунта Google: собираются на устройстве из «похожих видео»
 * к тому, что профиль лайкнул и недавно смотрел.
 */
object Recommendations {
    /** Сколько видео-источников берётся: сначала лайкнутые, потом недавние из истории. */
    const val MAX_SEEDS = 6
    const val MAX_PER_CHANNEL = 3
    const val LIMIT = 40

    /** Источники: свежие лайки, затем недавняя история, без повторов и дизлайков. */
    fun seeds(liked: List<String>, recent: List<String>, disliked: Set<String>, max: Int = MAX_SEEDS): List<String> =
        (liked.take(max / 2) + recent + liked.drop(max / 2))
            .distinct()
            .filter { it !in disliked }
            .take(max)

    /**
     * Смешивает списки похожих видео по кругу (по одному из каждого источника), чтобы
     * рекомендации не состояли из одной темы. Пропускает [exclude] (просмотренные, дизлайкнутые,
     * сами источники), повторы и больше [maxPerChannel] видео одного канала.
     */
    fun mix(
        related: List<List<Video>>,
        exclude: Set<String>,
        limit: Int = LIMIT,
        maxPerChannel: Int = MAX_PER_CHANNEL,
    ): List<Video> {
        val result = ArrayList<Video>(limit)
        val seen = HashSet<String>(exclude)
        val perChannel = HashMap<String, Int>()
        val iterators = related.map { it.iterator() }
        var progressed = true
        while (result.size < limit && progressed) {
            progressed = false
            for (it in iterators) {
                // Из каждого источника — следующее подходящее видео
                while (it.hasNext()) {
                    val video = it.next()
                    progressed = true
                    if (video.isLive || video.isShort || !seen.add(video.id)) continue
                    val channel = video.channelId
                    if (channel != null && (perChannel[channel] ?: 0) >= maxPerChannel) continue
                    if (channel != null) perChannel[channel] = (perChannel[channel] ?: 0) + 1
                    result += video
                    break
                }
                if (result.size >= limit) break
            }
        }
        return result
    }

    /** Каждое [every]-е место в рекомендациях — популярное в регионе из настроек. */
    const val REGIONAL_EVERY = 4

    /**
     * Подмешивает [regional] в [personal]: после каждых `every - 1` личных — одно региональное.
     * Региональные без [exclude] и повторов; если личных нет (новый профиль) — только региональные.
     */
    fun withRegional(
        personal: List<Video>,
        regional: List<Video>,
        exclude: Set<String>,
        every: Int = REGIONAL_EVERY,
        limit: Int = LIMIT,
    ): List<Video> {
        val seen = HashSet<String>(exclude).apply { personal.forEach { add(it.id) } }
        val extra = regional.filter { !it.isLive && !it.isShort && seen.add(it.id) }.iterator()
        val result = ArrayList<Video>(limit)
        for (video in personal) {
            if (result.size >= limit) break
            if (result.size % every == every - 1 && extra.hasNext()) result += extra.next()
            if (result.size < limit) result += video
        }
        while (result.size < limit && extra.hasNext() && (personal.isEmpty() || result.size % every == every - 1)) {
            result += extra.next()
        }
        return result
    }
}

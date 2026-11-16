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
}

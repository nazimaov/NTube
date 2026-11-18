package tv.lumen.data.youtube.internal

import org.json.JSONArray
import org.json.JSONObject
import tv.lumen.core.model.Video

/**
 * Разбор поиска клиента YouTube Kids (`ANDROID_KIDS`): видео лежат в `videoWithContextData` на любой глубине.
 * Каналы и прочие элементы пропускаются. Поиск YouTube Kids отдаёт только одобренные для детей видео — они
 * воспроизводятся через тот же клиент (см. `kidsFallback`).
 */
internal object KidsSearchParser {

    fun parse(root: JSONObject): List<Video> {
        val out = LinkedHashMap<String, Video>()
        walk(root) { key, value ->
            if (key == "videoWithContextData" && value is JSONObject) video(value)?.let { out.putIfAbsent(it.id, it) }
        }
        return out.values.toList()
    }

    private fun video(d: JSONObject): Video? {
        val id = d.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.length == 11 } ?: return null
        val videoData = d.optJSONObject("videoData") ?: return null
        val title = videoData.optJSONObject("metadata")?.optString("title")?.takeIf { it.isNotBlank() } ?: return null
        val sources = videoData.optJSONObject("thumbnail")?.optJSONObject("image")?.optJSONArray("sources")
        val thumbnail = (0 until (sources?.length() ?: 0)).mapNotNull { sources?.optJSONObject(it) }
            .maxByOrNull { it.optInt("width") }?.optString("url")
        val duration = TileParser.parseDuration(videoData.optJSONObject("thumbnail")?.optString("timestampText"))
        val channelId = videoData.optJSONObject("avatar")?.optJSONObject("endpoint")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.startsWith("UC") }
        // «Название - 4:33 - Kanala git - Like Nastya TR - 3,4 milyon görüntüleme - 4 yıl önce - videoyu oynat»
        val parts = d.optString("accessibilityText").split(" - ")
        val channelName = parts.getOrNull(parts.size - 4)?.takeIf { parts.size >= 6 && it.isNotBlank() }
        val published = parts.getOrNull(parts.size - 2)?.takeIf { parts.size >= 6 }
        return Video(
            id = id,
            title = title,
            channelId = channelId,
            channelName = channelName,
            thumbnailUrl = thumbnail,
            durationSec = duration,
            viewCount = -1,
            publishedAt = null,
            publishedText = published,
            isLive = duration < 0 && videoData.optJSONObject("thumbnail")?.optString("timestampStyle") == "LIVE",
            isShort = false,
        )
    }

    private fun walk(element: Any?, visit: (String, Any?) -> Unit) {
        when (element) {
            is JSONObject -> element.keys().forEach { key ->
                val value = element.opt(key)
                visit(key, value)
                walk(value, visit)
            }
            is JSONArray -> for (i in 0 until element.length()) walk(element.opt(i), visit)
        }
    }
}

/** «1:02:05» → 3725; не время → −1. */
private object TileParser {
    fun parseDuration(text: String?): Long {
        val parts = text?.trim()?.split(':') ?: return -1
        if (parts.size !in 2..3 || parts.any { it.toIntOrNull() == null }) return -1
        return parts.map { it.toLong() }.fold(0L) { acc, p -> acc * 60 + p }
    }
}

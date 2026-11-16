package tv.lumen.data.account

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tv.lumen.core.model.PageToken
import tv.lumen.core.model.Video

/** Видео и токен следующей страницы из ответа телевизионного `browse`. */
internal data class ParsedFeed(val videos: List<Video>, val next: PageToken?)

/**
 * Разбор ответа телевизионного клиента YouTube. Видео там лежат «плитками» (`tileRenderer`) на
 * разной глубине (полки главной, сетка подписок, список истории), поэтому плитки ищутся обходом
 * всего дерева, а не по фиксированному пути: вёрстка полок меняется чаще, чем сама плитка.
 * Поля плитки — как у SmartTube (MediaServiceCore, GPLv3).
 */
internal object TileParser {
    private const val TYPE_VIDEO = "TILE_CONTENT_TYPE_VIDEO"

    fun parse(root: JsonElement): ParsedFeed {
        val videos = LinkedHashMap<String, Video>()
        var next: String? = null
        walk(root) { key, value ->
            when {
                key == "tileRenderer" && value is JsonObject -> tile(value)?.let { videos.putIfAbsent(it.id, it) }
                // Последний найденный токен — продолжение всей ленты, а не отдельной полки
                key == "continuationCommand" && value is JsonObject -> value.string("token")?.let { next = it }
                key == "nextContinuationData" && value is JsonObject -> value.string("continuation")?.let { next = it }
            }
        }
        return ParsedFeed(videos.values.toList(), next?.let(::PageToken))
    }

    private fun tile(tile: JsonObject): Video? {
        val contentType = tile.string("contentType")
        if (contentType != null && contentType != TYPE_VIDEO) return null // каналы, плейлисты, чипы
        val select = tile.obj("onSelectCommand")
        val videoId = select?.obj("watchEndpoint")?.string("videoId")
            ?: select?.obj("reelWatchEndpoint")?.string("videoId")
            ?: return null
        val isShort = select?.obj("reelWatchEndpoint") != null || tile.string("style")?.contains("SHORTS") == true

        val header = tile.obj("header")?.obj("tileHeaderRenderer")
        val metadata = tile.obj("metadata")?.obj("tileMetadataRenderer")
            ?: header?.arr("thumbnailOverlays")?.firstNotNullOfOrNull { it.asObj()?.obj("tileMetadataRenderer") }
        val title = metadata?.obj("title")?.text() ?: return null

        val lines = metadata.arr("lines").orEmpty().map { line ->
            line.asObj()?.obj("lineRenderer")?.arr("items").orEmpty()
                .mapNotNull { it.asObj()?.obj("lineItemRenderer")?.obj("text")?.text() }
        }
        val channelName = lines.getOrNull(0)?.joinToString(" ")?.takeIf { it.isNotBlank() }
        val meta = lines.getOrNull(1)?.joinToString(" ")?.takeIf { it.isNotBlank() }

        val overlays = header?.arr("thumbnailOverlays").orEmpty().mapNotNull { it.asObj()?.obj("thumbnailOverlayTimeStatusRenderer") }
        val badge = overlays.firstOrNull()
        val isLive = badge?.string("style")?.uppercase() == "LIVE"
        val duration = if (isLive) -1L else parseDuration(badge?.obj("text")?.text())

        val thumbnail = header?.obj("thumbnail")?.arr("thumbnails").orEmpty()
            .mapNotNull { it.asObj() }
            .maxByOrNull { it.int("width") ?: 0 }
            ?.string("url")

        return Video(
            id = videoId,
            title = title,
            channelId = channelId(tile),
            channelName = channelName,
            thumbnailUrl = thumbnail,
            durationSec = duration,
            viewCount = -1,
            publishedAt = null,
            publishedText = meta,
            isLive = isLive,
            isShort = isShort,
        )
    }

    /** Идентификатор канала берётся из любой ссылки на канал внутри плитки (меню «Перейти на канал»). */
    private fun channelId(tile: JsonObject): String? {
        var found: String? = null
        walk(tile) { key, value ->
            if (found == null && key == "browseId" && value is JsonPrimitive) {
                value.contentOrNull?.takeIf { it.startsWith("UC") && it.length == 24 }?.let { found = it }
            }
        }
        return found
    }

    /** «1:02:05» → 3725, «10:35» → 635; не время («LIVE», «Короткое») → −1. */
    fun parseDuration(text: String?): Long {
        val parts = text?.trim()?.split(':') ?: return -1
        if (parts.size !in 2..3 || parts.any { it.toIntOrNull() == null }) return -1
        return parts.map { it.toLong() }.fold(0L) { acc, p -> acc * 60 + p }
    }

    private fun walk(element: JsonElement, visit: (String, JsonElement) -> Unit) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                visit(key, value)
                walk(value, visit)
            }
            is JsonArray -> element.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    /** Текст YouTube: либо `simpleText`, либо кусочки `runs[].text`. */
    private fun JsonObject.text(): String? =
        string("simpleText") ?: arr("runs")?.joinToString("") { it.asObj()?.string("text").orEmpty() }?.takeIf { it.isNotBlank() }
}

/** Для отладки: названия блоков ответа (…Renderer) и сколько раз встретились; содержимого здесь нет. */
internal fun TileParser.shape(root: JsonElement): String {
    val counts = sortedMapOf<String, Int>()
    fun walk(e: JsonElement) {
        when (e) {
            is JsonObject -> e.forEach { (k, v) ->
                if (k.endsWith("Renderer") || k.endsWith("Command") || k.endsWith("Endpoint")) counts.merge(k, 1, Int::plus)
                walk(v)
            }
            is JsonArray -> e.forEach(::walk)
            else -> Unit
        }
    }
    walk(root)
    return counts.entries.joinToString(", ") { "${it.key}×${it.value}" }
}

package tv.lumen.data.youtube.internal

import org.json.JSONArray
import org.json.JSONObject
import tv.lumen.core.model.AudioStream
import tv.lumen.core.model.DeliveryMethod
import tv.lumen.core.model.StreamSet
import tv.lumen.core.model.Video
import tv.lumen.core.model.VideoDetails
import tv.lumen.core.model.VideoStream
import java.net.URLDecoder
import java.time.Instant

/**
 * Разбор ответа `player` телевизионного клиента (запасной путь для видео «для детей»).
 * Ссылки бывают прямыми или зашифрованными (`signatureCipher`); расшифровка — функциями [decipher] и
 * [throttling] (снаружи их выполняет NewPipeExtractor, здесь они параметры — так разбор можно проверить тестом).
 */
internal object TvPlayerParser {

    /** Статус воспроизводимости, если видео не играет; null — играет. */
    fun unplayableReason(root: JSONObject): String? {
        val ps = root.optJSONObject("playabilityStatus") ?: return "no playabilityStatus"
        val status = ps.optString("status")
        return if (status.equals("OK", ignoreCase = true)) null else "$status: ${ps.optString("reason")}"
    }

    fun details(
        videoId: String,
        root: JSONObject,
        now: Instant,
        decipher: (String) -> String,
        throttling: (String) -> String,
    ): VideoDetails? {
        val sd = root.optJSONObject("streamingData") ?: return null
        val vd = root.optJSONObject("videoDetails") ?: JSONObject()
        val duration = vd.optString("lengthSeconds").toLongOrNull() ?: -1
        val isLive = vd.optBoolean("isLiveContent") && vd.optBoolean("isLive", false)

        val videoOnly = ArrayList<VideoStream>()
        val audio = ArrayList<AudioStream>()
        val muxed = ArrayList<VideoStream>()
        for (f in sd.optJSONArray("adaptiveFormats").objects()) {
            val url = url(f, decipher, throttling) ?: continue
            val mime = f.optString("mimeType")
            if (mime.startsWith("audio/")) audio += audioStream(f, url, mime) else if (mime.startsWith("video/")) videoOnly += videoStream(f, url, mime)
        }
        for (f in sd.optJSONArray("formats").objects()) {
            val url = url(f, decipher, throttling) ?: continue
            muxed += videoStream(f, url, f.optString("mimeType"))
        }
        if (videoOnly.isEmpty() && muxed.isEmpty() && !isLive) return null

        val thumbnail = vd.optJSONObject("thumbnail")?.optJSONArray("thumbnails").objects()
            .maxByOrNull { it.optInt("width") }?.optString("url")
        return VideoDetails(
            video = Video(
                id = videoId,
                title = vd.optString("title"),
                channelId = vd.optString("channelId").takeIf { it.startsWith("UC") },
                channelName = vd.optString("author").takeIf { it.isNotBlank() },
                thumbnailUrl = thumbnail,
                durationSec = duration,
                viewCount = vd.optString("viewCount").toLongOrNull() ?: -1,
                publishedAt = null,
                publishedText = null,
                isLive = isLive,
                isShort = false,
            ),
            description = vd.optString("shortDescription"),
            likeCount = -1,
            channelAvatarUrl = null,
            channelSubscriberCount = -1,
            ageLimit = 0,
            streams = StreamSet(
                videoOnly = videoOnly,
                audio = audio,
                muxed = muxed,
                subtitles = emptyList(),
                hlsUrl = sd.optString("hlsManifestUrl").takeIf { it.isNotBlank() },
                dashMpdUrl = null,
                isLive = isLive,
                durationSec = duration,
            ),
            related = emptyList(),
            fetchedAt = now,
        )
    }

    /** Прямая ссылка или расшифрованная `signatureCipher` (s, sp, url); n-параметр расшифровывается всегда. */
    fun url(f: JSONObject, decipher: (String) -> String, throttling: (String) -> String): String? {
        val direct = f.optString("url").takeIf { it.isNotBlank() }
        val raw = direct ?: run {
            val cipher = f.optString("signatureCipher").ifBlank { f.optString("cipher") }.takeIf { it.isNotBlank() } ?: return null
            val params = cipher.split('&').associate { part ->
                val i = part.indexOf('=')
                if (i < 0) part to "" else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
            }
            val base = params["url"] ?: return null
            val s = params["s"] ?: return base
            base + "&" + (params["sp"] ?: "signature") + "=" + decipher(s)
        }
        return throttling(raw)
    }

    private fun videoStream(f: JSONObject, url: String, mime: String): VideoStream {
        val codec = codecOf(mime)
        val label = f.optString("qualityLabel").takeIf { it.isNotBlank() }
        return VideoStream(
            id = f.optInt("itag").toString(),
            url = url,
            delivery = DeliveryMethod.PROGRESSIVE_HTTP,
            mimeType = mime.substringBefore(';').trim(),
            codec = Mappers.videoCodec(codec),
            codecString = codec,
            width = f.optInt("width"),
            height = f.optInt("height"),
            fps = f.optInt("fps"),
            bitrate = f.optInt("bitrate"),
            isHdr = label?.contains("HDR", ignoreCase = true) == true,
            qualityLabel = label,
        )
    }

    private fun audioStream(f: JSONObject, url: String, mime: String): AudioStream {
        val codec = codecOf(mime)
        val track = f.optJSONObject("audioTrack")
        return AudioStream(
            id = f.optInt("itag").toString() + (track?.optString("id")?.let { "-$it" } ?: ""),
            url = url,
            delivery = DeliveryMethod.PROGRESSIVE_HTTP,
            mimeType = mime.substringBefore(';').trim(),
            codec = when {
                codec?.startsWith("opus") == true -> tv.lumen.core.model.AudioCodec.OPUS
                codec?.startsWith("mp4a") == true -> tv.lumen.core.model.AudioCodec.AAC
                else -> tv.lumen.core.model.AudioCodec.OTHER
            },
            codecString = codec,
            bitrate = f.optInt("averageBitrate").takeIf { it > 0 } ?: f.optInt("bitrate"),
            trackId = track?.optString("id"),
            trackName = track?.optString("displayName"),
            language = track?.optString("id")?.substringBefore('.'),
            isOriginal = track == null || track.optBoolean("audioIsDefault", true),
        )
    }

    /** `video/mp4; codecs="avc1.4d401f"` → `avc1.4d401f`. */
    fun codecOf(mime: String): String? = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1)

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

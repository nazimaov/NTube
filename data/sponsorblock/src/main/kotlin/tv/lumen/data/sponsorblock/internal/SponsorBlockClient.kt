package tv.lumen.data.sponsorblock.internal

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.model.Segment
import tv.lumen.core.model.SponsorCategory
import tv.lumen.data.sponsorblock.SponsorBlockRepository
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Serializable
internal data class VideoSegmentsDto(
    @SerialName("videoID") val videoId: String,
    val segments: List<SegmentDto> = emptyList(),
)

@Serializable
internal data class SegmentDto(
    val segment: List<Double>,
    @SerialName("UUID") val uuid: String = "",
    val category: String,
    val actionType: String = "skip",
)

/**
 * Приватный запрос по префиксу хэша: сервер не узнаёт, какое видео смотрят (SPEC 6.1).
 * Таймаут 3 с (SPEC 6), ответ 404 — сегментов нет.
 */
@Singleton
internal class SponsorBlockClient @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
    @Named(BASE_URL_NAME) private val baseUrl: String,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SponsorBlockRepository {

    private val http = client.newBuilder().callTimeout(TIMEOUT_S, TimeUnit.SECONDS).build()

    override suspend fun segments(videoId: String, categories: Set<SponsorCategory>): List<Segment> {
        if (categories.isEmpty()) return emptyList()
        return withContext(io) {
            try {
                val request = Request.Builder().url(url(videoId, categories)).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    parse(response.body.string(), videoId, categories)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "SponsorBlock недоступен: ${e.message}")
                emptyList()
            }
        }
    }

    internal fun url(videoId: String, categories: Set<SponsorCategory>): HttpUrl =
        "$baseUrl/api/skipSegments/${hashPrefix(videoId)}".toHttpUrl().newBuilder()
            .addQueryParameter("categories", categories.joinToString(",", "[", "]") { "\"${it.apiName}\"" })
            .addQueryParameter("actionTypes", "[\"skip\"]")
            .build()

    internal fun parse(body: String, videoId: String, categories: Set<SponsorCategory>): List<Segment> =
        json.decodeFromString<List<VideoSegmentsDto>>(body)
            .firstOrNull { it.videoId == videoId } // в ответе по префиксу — несколько видео
            ?.segments.orEmpty()
            .mapNotNull { dto ->
                val category = SponsorCategory.fromApi(dto.category) ?: return@mapNotNull null
                if (category !in categories || dto.actionType != "skip" || dto.segment.size < 2) return@mapNotNull null
                val start = (dto.segment[0] * 1000).toLong()
                val end = (dto.segment[1] * 1000).toLong()
                if (end - start < MIN_SEGMENT_MS) return@mapNotNull null // короче 1 с — отбросить (SPEC 6.1)
                Segment(start, end, category, dto.uuid)
            }
            .sortedBy { it.startMs }

    companion object {
        const val BASE_URL_NAME = "sponsorblock.baseUrl"
        const val DEFAULT_BASE_URL = "https://sponsor.ajay.app"
        private const val TIMEOUT_S = 3L
        private const val MIN_SEGMENT_MS = 1_000L
        private const val TAG = "Lumen/SponsorBlock"

        /** Первые 4 символа hex(SHA-256(videoId)). */
        fun hashPrefix(videoId: String): String =
            MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(4)
    }
}
package tv.lumen.data.dearrow.internal

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.database.BrandingCacheDao
import tv.lumen.core.database.BrandingCacheEntity
import tv.lumen.data.dearrow.Branding
import tv.lumen.data.dearrow.DeArrowRepository
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Serializable
internal data class BrandingDto(
    val titles: List<TitleDto> = emptyList(),
    val thumbnails: List<ThumbnailDto> = emptyList(),
)

@Serializable
internal data class TitleDto(val title: String, val original: Boolean = false, val votes: Int = 0, val locked: Boolean = false)

@Serializable
internal data class ThumbnailDto(val timestamp: Double? = null, val original: Boolean = false, val votes: Int = 0, val locked: Boolean = false)

@Singleton
internal class DeArrowClient @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
    private val cache: BrandingCacheDao,
    @Named(API_URL) private val apiUrl: String,
    @Named(THUMB_URL) private val thumbUrl: String,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DeArrowRepository {

    private val http = client.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
    internal var clock: () -> Long = System::currentTimeMillis

    override suspend fun branding(videoId: String): Branding? = withContext(io) {
        val now = clock()
        cache.get(videoId)?.takeIf { now - it.fetchedAt < CACHE_TTL_MS }?.let { return@withContext it.toBranding() }
        val fresh = fetch(videoId) ?: return@withContext null // сеть недоступна — не кэшируем
        cache.upsert(BrandingCacheEntity(videoId, fresh.title, fresh.thumbnailTime, now))
        fresh.toBrandingOrNull()
    }

    /** GET /api/branding/{prefix}: приватный запрос по префиксу хэша, как в SponsorBlock (SPEC 6.2). */
    internal fun fetch(videoId: String): Branding? = try {
        val request = Request.Builder().url("$apiUrl/api/branding/${hashPrefix(videoId)}").build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> Branding(null, null)
                !response.isSuccessful -> null
                else -> parse(response.body.string(), videoId)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Lumen/DeArrow", "DeArrow недоступен: ${e.message}")
        null
    }

    internal fun parse(body: String, videoId: String): Branding {
        val all = json.decodeFromString<Map<String, BrandingDto>>(body)
        val dto = all[videoId] ?: return Branding(null, null)
        return Branding(title = pickTitle(dto.titles), thumbnailTime = pickThumbnail(dto.thumbnails))
    }

    override fun thumbnailUrl(videoId: String, time: Double): String =
        "$thumbUrl/api/v1/getThumbnail?videoID=$videoId&time=$time"

    companion object {
        const val API_URL = "dearrow.apiUrl"
        const val THUMB_URL = "dearrow.thumbUrl"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        /**
         * Первый элемент (сервер сортирует по качеству), только если он locked или votes >= 0;
         * original = «оставить оригинал»; символы `>` перед словами убираются (SPEC 6.2).
         */
        fun pickTitle(titles: List<TitleDto>): String? {
            val first = titles.firstOrNull() ?: return null
            if (!(first.locked || first.votes >= 0) || first.original) return null
            return cleanTitle(first.title).takeIf { it.isNotBlank() }
        }

        fun pickThumbnail(thumbs: List<ThumbnailDto>): Double? {
            val first = thumbs.firstOrNull() ?: return null
            if (!(first.locked || first.votes >= 0) || first.original) return null
            return first.timestamp
        }

        fun cleanTitle(title: String): String = title.replace(Regex(""">(\S)"""), "$1").trim()

        fun hashPrefix(videoId: String): String =
            MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray()).joinToString("") { "%02x".format(it) }.take(4)
    }
}

private fun BrandingCacheEntity.toBranding(): Branding? = Branding(title, thumbnailTime).toBrandingOrNull()

/** Пустой брендинг — то же, что его отсутствие. */
private fun Branding.toBrandingOrNull(): Branding? = takeIf { title != null || thumbnailTime != null }
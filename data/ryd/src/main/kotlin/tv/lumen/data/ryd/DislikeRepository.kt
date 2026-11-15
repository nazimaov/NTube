package tv.lumen.data.ryd

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.lumen.core.common.IoDispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Serializable
internal data class VotesDto(val id: String = "", val likes: Long = -1, val dislikes: Long = -1, val deleted: Boolean = false)

/**
 * Return YouTube Dislike (SPEC 6.3). Число дизлайков — оценка, интерфейс показывает «≈».
 * 404/400/таймаут 3 с — null, воспроизведение от этого не зависит.
 */
@Singleton
class DislikeRepository @Inject internal constructor(
    client: OkHttpClient,
    private val json: Json,
    @Named(BASE_URL) private val baseUrl: String,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val http = client.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()

    suspend fun dislikes(videoId: String): Long? = withContext(io) {
        try {
            val url = "$baseUrl/votes".toHttpUrl().newBuilder().addQueryParameter("videoId", videoId).build()
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                json.decodeFromString<VotesDto>(response.body.string()).dislikes.takeIf { it >= 0 }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Lumen/RYD", "RYD недоступен: ${e.message}")
            null
        }
    }

    companion object {
        const val BASE_URL = "ryd.baseUrl"
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal object RydModule {
    @dagger.Provides
    @Named(DislikeRepository.BASE_URL)
    fun baseUrl(): String = "https://returnyoutubedislikeapi.com"
}
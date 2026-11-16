package tv.lumen.data.account

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.model.Reaction
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Действия в аккаунте Google: лайк и дизлайк. Запросы `like/like`, `like/dislike` и обратные к ним —
 * как у телевизионного клиента YouTube и SmartTube (MediaServiceCore, GPLv3). Без входа ничего не делает.
 */
@Singleton
class AccountActions @Inject internal constructor(
    client: OkHttpClient,
    private val account: GoogleAccount,
    private val feed: AccountFeed,
    @IoDispatcher private val io: CoroutineDispatcher,
    @Named(BASE_URL) private val baseUrl: String,
) {
    private val http = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Приводит отметку видео в аккаунте к [now], если до этого она была [before]:
     * лайк и дизлайк ставятся, снятие отмены выбирает обратный запрос по прежнему значению.
     * true — запрос принят или входа нет (делать нечего), false — не удалось.
     */
    suspend fun react(videoId: String, before: Reaction?, now: Reaction?): Boolean {
        if (!account.isSignedIn) return true
        val path = when {
            now == Reaction.LIKE -> "like/like"
            now == Reaction.DISLIKE -> "like/dislike"
            before == Reaction.LIKE -> "like/removelike"
            before == Reaction.DISLIKE -> "like/removedislike"
            else -> return true
        }
        return post(path, "\"target\":{\"videoId\":\"$videoId\"}")
    }

    /** Подписка или отписка на канал в аккаунте; без входа или без канала вида UC… ничего не делает. */
    suspend fun setSubscribed(channelId: String, subscribed: Boolean): Boolean {
        if (!account.isSignedIn || !channelId.startsWith("UC")) return true
        val path = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        return post(path, "\"channelIds\":[\"$channelId\"],\"params\":\"\"")
    }

    /** Отметка видео и подписка на его канал в аккаунте; null — входа нет или запрос не удался. */
    suspend fun videoState(videoId: String): AccountVideoState? {
        if (!account.isSignedIn) return null
        val text = request("next", "\"videoId\":\"$videoId\"") ?: return null
        return runCatching { NextParser.parse(json.parseToJsonElement(text)) }.getOrNull()
    }

    private suspend fun post(path: String, data: String): Boolean = request(path, data) != null

    /** Тело ответа при успехе, иначе null. */
    private suspend fun request(path: String, data: String): String? = withContext(io) {
        try {
            val auth = account.authorizationHeader() ?: return@withContext null
            val visitor = account.visitorData()
            val request = Request.Builder()
                .url("$baseUrl/$path?prettyPrint=false")
                .header("Authorization", auth)
                .header("User-Agent", TvClient.USER_AGENT)
                .header("Referer", TvClient.REFERER)
                .apply { visitor?.let { header("X-Goog-Visitor-Id", it) } }
                .post(BrowseBody.build(data, visitor, feed.locale).toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Запрос в аккаунт не удался: ${e.javaClass.simpleName}")
            null
        }
    }

    companion object {
        const val BASE_URL = "account.actionsBaseUrl"
        private const val TAG = "Lumen/AccountActions"
        private val JSON = "application/json".toMediaType()
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal object AccountActionsModule {
    @dagger.Provides
    @Named(AccountActions.BASE_URL)
    fun actionsBaseUrl(): String = "https://www.youtube.com/youtubei/v1"
}

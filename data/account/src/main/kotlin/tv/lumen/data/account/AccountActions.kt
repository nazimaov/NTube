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
    private val poTokens: PoTokenSource,
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

    /**
     * Параметры отчёта о просмотре видео: ответ `player` аккаунта, как у телевизионного клиента.
     * null — входа нет, видео не воспроизводится аккаунтом или YouTube не отдал параметры.
     */
    suspend fun trackingParams(videoId: String): TrackingParams? {
        if (!account.isSignedIn) return null
        // Без токена проверки телевизионный клиент отвечает UNPLAYABLE и параметров отчёта нет
        val visitor = account.visitorData() ?: return null
        val token = poTokens.mint(visitor) ?: return null
        val data = "\"videoId\":\"$videoId\",\"serviceIntegrityDimensions\":{\"poToken\":\"$token\"}," +
            "\"playbackContext\":{\"contentPlaybackContext\":{\"html5Preference\":\"HTML5_PREF_WANTS\"" +
            (signatureTimestamp()?.let { ",\"signatureTimestamp\":$it" } ?: "") + "}}"
        val text = request("player", data) ?: return null
        val (status, params) = runCatching { PlayerParser.parse(json.parseToJsonElement(text)) }.getOrNull() ?: return null
        if (params == null) Log.w(TAG, "player: параметров просмотра нет, статус $status")
        return params
    }

    private var timestamp: Pair<Int, Long>? = null

    /**
     * signatureTimestamp телевизионного плеера: без него `player` отвечает UNPLAYABLE. Берётся из скрипта плеера,
     * адрес которого указан в iframe_api; кэшируется на сутки, при сбое null (запрос уйдёт без него).
     */
    private suspend fun signatureTimestamp(): Int? = withContext(io) {
        timestamp?.takeIf { System.currentTimeMillis() - it.second < DAY_MS }?.let { return@withContext it.first }
        try {
            val api = get("https://www.youtube.com/iframe_api") ?: return@withContext null
            val id = Regex("""player\\?/([a-z0-9]{8})""").find(api)?.groupValues?.get(1) ?: return@withContext null
            val script = get("https://www.youtube.com/s/player/$id/tv-player-ias.vflset/tv-player-ias.js") ?: return@withContext null
            val value = Regex("""signatureTimestamp:(\d+)""").find(script)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
            timestamp = value to System.currentTimeMillis()
            value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? =
        http.newCall(Request.Builder().url(url).header("User-Agent", TvClient.USER_AGENT).build()).execute().use {
            if (it.isSuccessful) it.body.string() else null
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
            http.newCall(request).execute().use {
                if (it.isSuccessful) it.body.string() else {
                    Log.w(TAG, "$path: HTTP ${it.code}")
                    null
                }
            }
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
        private const val DAY_MS = 24 * 60 * 60 * 1000L
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

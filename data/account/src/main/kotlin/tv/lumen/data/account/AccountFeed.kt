package tv.lumen.data.account

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.Page
import tv.lumen.core.model.PageToken
import tv.lumen.core.model.Video
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Лента аккаунта Google: главная с рекомендациями, подписки и история. Только чтение. */
@Singleton
class AccountFeed @Inject internal constructor(
    client: OkHttpClient,
    private val json: Json,
    private val account: GoogleAccount,
    @IoDispatcher private val io: CoroutineDispatcher,
    @Named(BROWSE_URL) private val browseUrl: String,
) {
    private val http = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()

    /** Язык и регион запросов; задаются из настроек контента. */
    @Volatile
    var locale: Locale = Locale.getDefault()

    suspend fun home(page: PageToken?) = browse("\"browseId\":\"default\"", page)

    suspend fun subscriptions(page: PageToken?) = browse("\"browseId\":\"FEsubscriptions\"", page)

    suspend fun history(page: PageToken?) = browse("\"browseId\":\"FEhistory\"", page)

    /** Плейлист «Понравившиеся» аккаунта. */
    suspend fun liked(page: PageToken?) = browse("\"browseId\":\"VLLL\"", page)

    private suspend fun browse(target: String, page: PageToken?): AppResult<Page<Video>> = withContext(io) {
        try {
            val auth = account.authorizationHeader() ?: return@withContext AppResult.Failure(AppError.Unknown("not signed in"))
            val data = if (page != null) "\"continuation\":\"${page.value}\"" else target
            val request = Request.Builder()
                .url(browseUrl)
                .header("Authorization", auth)
                .header("User-Agent", TvClient.USER_AGENT)
                .header("Referer", TvClient.REFERER)
                .apply { account.visitorData()?.let { header("X-Goog-Visitor-Id", it) } }
                .post(BrowseBody.build(data, account.visitorData(), locale).toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.code == 401 || response.code == 403) {
                    return@withContext AppResult.Failure(AppError.Unknown("auth ${response.code}"))
                }
                if (!response.isSuccessful) return@withContext AppResult.Failure(AppError.Network)
                val parsed = TileParser.parse(json.parseToJsonElement(text))
                if (parsed.videos.isEmpty() && page == null) {
                    // Ничего не нашли: вёрстка могла измениться — оставляем след без содержимого ленты
                    Log.w(TAG, "Лента пуста; блоки ответа: ${TileParser.shape(json.parseToJsonElement(text))}")
                }
                AppResult.Success(Page(parsed.videos, parsed.next))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            AppResult.Failure(AppError.Network)
        } catch (e: Exception) {
            Log.w(TAG, "Разбор ленты не удался: ${e.javaClass.simpleName}")
            AppResult.Failure(AppError.ExtractionBroken)
        }
    }

    companion object {
        const val BROWSE_URL = "account.browseUrl"
        private const val TAG = "Lumen/AccountFeed"
        private val JSON = "application/json".toMediaType()
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal object AccountFeedModule {
    @dagger.Provides
    @Named(AccountFeed.BROWSE_URL)
    fun browseUrl(): String = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
}

/** Тело запроса телевизионного клиента YouTube: контекст клиента, язык и регион плюс данные запроса. */
internal object BrowseBody {
    fun build(data: String, visitorData: String?, locale: Locale, utcOffsetMinutes: Int = utcOffset(), downgraded: Boolean = false): String =
        "{\"context\":{\"client\":{\"clientName\":\"${TvClient.CLIENT_NAME}\",\"clientVersion\":\"${if (downgraded) TvClient.CLIENT_VERSION_DOWNGRADED else TvClient.CLIENT_VERSION}\"," +
            "\"clientScreen\":\"WATCH\",\"userAgent\":\"${if (downgraded) TvClient.USER_AGENT_DOWNGRADED else TvClient.USER_AGENT}\"," +
            "\"tvAppInfo\":{\"appQuality\":\"TV_APP_QUALITY_FULL_ANIMATION\",\"zylonLeftNav\":true}," +
            "\"webpSupport\":false,\"animatedWebpSupport\":true," +
            "\"acceptLanguage\":\"${locale.language}\",\"acceptRegion\":\"${locale.country.ifBlank { "US" }}\"," +
            "\"utcOffsetMinutes\":\"$utcOffsetMinutes\",\"visitorData\":\"${visitorData.orEmpty()}\"}," +
            "\"user\":{\"enableSafetyMode\":false,\"lockedSafetyMode\":false}}," +
            "\"racyCheckOk\":true,\"contentCheckOk\":true,$data}"

    private fun utcOffset() = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
}

package tv.lumen.data.account

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.lumen.core.common.IoDispatcher
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Запись просмотров в историю аккаунта Google (при входе): те же запросы `api/stats/playback` и
 * `api/stats/watchtime`, что у телевизионного клиента YouTube и SmartTube (MediaServiceCore, GPLv3).
 * Из-за этих отчётов видео попадает в историю и влияет на рекомендации аккаунта.
 *
 * Отчёты редкие: не раньше чем после [MIN_POSITION_SEC] просмотра, затем не чаще раза в [MIN_INTERVAL_MS],
 * и один финальный, когда видео досмотрено.
 */
@Singleton
class WatchReporter @Inject internal constructor(
    client: OkHttpClient,
    private val account: GoogleAccount,
    private val actions: AccountActions,
    @IoDispatcher private val io: CoroutineDispatcher,
    @Named(STATS_BASE_URL) private val statsBase: String,
) {
    private val http = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val lock = Mutex()
    private var session: Session? = null
    private var failedVideo: String? = null
    internal var clock: () -> Long = System::currentTimeMillis

    private class Session(
        val videoId: String,
        val params: TrackingParams,
        val nonce: String,
    ) {
        var recordedAt = 0L
        var lastPosition = 0f
        var lastReportAt = 0L
        var finished = false
    }

    /** Сообщает YouTube, докуда дошёл просмотр. Без входа, для эфиров и коротких отрезков ничего не делает. */
    suspend fun report(videoId: String, positionSec: Float, lengthSec: Float) {
        if (!account.isSignedIn || lengthSec <= 0f || positionSec < MIN_POSITION_SEC) return
        lock.withLock {
            if (failedVideo == videoId) return
            val now = clock()
            val s = session?.takeIf { it.videoId == videoId } ?: newSession(videoId) ?: run { failedVideo = videoId; return }
            if (s.finished) return
            val almostWatched = lengthSec - positionSec < lengthSec * END_FRACTION
            if (!almostWatched && now - s.lastReportAt < MIN_INTERVAL_MS) return
            try {
                send(s, positionSec, lengthSec, almostWatched, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Отчёт о просмотре не отправлен: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun newSession(videoId: String): Session? {
        val params = actions.trackingParams(videoId) ?: return null
        return Session(videoId, params, nonce()).also { session = it }
    }

    private suspend fun send(s: Session, position: Float, length: Float, final: Boolean, now: Long) = withContext(io) {
        if (final) {
            // Досмотрено: запись «просмотрено полностью» и финальное время
            get("playback", s, "final" to "1")
            get("watchtime", s, "final" to "1", "st" to sec(length), "et" to sec(length))
            s.finished = true
        } else {
            val start = if (s.recordedAt != 0L) s.lastPosition else if (position < START_THRESHOLD_SEC) 0f else position
            if (s.recordedAt == 0L || now - s.recordedAt > RECORD_RENEW_MS) {
                get("playback", s, "len" to sec(length), "cmt" to sec(start))
                s.recordedAt = now
            }
            get("watchtime", s, "len" to sec(length), "st" to sec(start), "et" to sec(position), "cmt" to sec(position))
        }
        s.lastPosition = position
        s.lastReportAt = now
    }

    private suspend fun get(kind: String, s: Session, vararg extra: Pair<String, String>) {
        val url = "$statsBase/$kind".toHttpUrl().newBuilder()
            .addQueryParameter("ns", "yt")
            .addQueryParameter("ver", "2")
            .addQueryParameter("docid", s.videoId)
            .addQueryParameter("cpn", s.nonce)
            .addQueryParameter("ei", s.params.eventId)
            .addQueryParameter("vm", s.params.visitorMonitoring)
            .addQueryParameter("of", s.params.of)
            .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val auth = account.authorizationHeader() ?: return
        val request = Request.Builder().url(url)
            .header("Authorization", auth)
            .header("User-Agent", TvClient.USER_AGENT)
            .header("Referer", TvClient.REFERER)
            .get().build()
        http.newCall(request).execute().use { Log.d(TAG, "${url.encodedPath} -> ${it.code}") }
    }

    private fun sec(value: Float) = String.format(Locale.US, "%.3f", value)

    /** Случайный код воспроизведения (16 знаков), одинаковый для всех отчётов о видео. */
    private fun nonce(): String {
        val random = SecureRandom()
        return buildString { repeat(16) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    }

    companion object {
        const val STATS_BASE_URL = "account.statsBaseUrl"
        private const val TAG = "Lumen/WatchReporter"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        const val MIN_POSITION_SEC = 10f
        const val MIN_INTERVAL_MS = 30_000L
        private const val START_THRESHOLD_SEC = 3 * 60f
        private const val RECORD_RENEW_MS = 30 * 60_000L
        private const val END_FRACTION = 0.05f
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal object WatchReporterModule {
    @dagger.Provides
    @Named(WatchReporter.STATS_BASE_URL)
    fun statsBaseUrl(): String = "https://www.youtube.com/api/stats"
}

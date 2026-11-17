package tv.lumen.data.account

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.lumen.core.common.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Источник токена проверки для запросов `player`; в тестах подменяется. */
interface PoTokenSource {
    suspend fun mint(identifier: String): String?
}

/**
 * Токен проверки (poToken) для запросов `player`: тот самый скрипт BotGuard, который YouTube сам отдаёт
 * клиентам, исполняется в скрытом WebView без доступа к сети (приёмы NewPipe, GPLv3, `PoTokenWebView`).
 *
 * Самовосстановление: скрипт и его параметры скачиваются заново при каждом запуске и когда истекает срок;
 * при любой ошибке сессия пересоздаётся и попытка повторяется один раз; после неудачи идёт пауза
 * [COOLDOWN_MS], чтобы не нагружать сеть. Сбой не влияет на воспроизведение: `mint` вернёт null.
 */
@Singleton
class PoTokenMinter @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PoTokenSource {
    private val lock = Mutex()
    private var session: Session? = null
    private var failedAt = 0L
    internal var clock: () -> Long = System::currentTimeMillis

    /** Токен для привязки [identifier] (visitorData); null: не удалось, следующая попытка не раньше паузы. */
    override suspend fun mint(identifier: String): String? = lock.withLock {
        if (failedAt != 0L && clock() - failedAt < COOLDOWN_MS) return null
        repeat(2) { attempt ->
            try {
                val s = session?.takeIf { clock() < it.expiresAt } ?: open().also { session = it }
                val token = s.mint(identifier)
                failedAt = 0L
                return token
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "poToken: попытка ${attempt + 1} не удалась: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
                closeSession()
            }
        }
        failedAt = clock()
        null
    }

    private suspend fun closeSession() {
        val s = session ?: return
        session = null
        withContext(Dispatchers.Main) { s.close() }
    }

    private suspend fun open(): Session {
        val session = withContext(Dispatchers.Main) { Session(context) }
        try {
            withTimeout(INIT_TIMEOUT_MS) {
                val html = withContext(io) { context.assets.open("po_token.html").bufferedReader().use { it.readText() } }
                val challenge = post("Create", "[\"$REQUEST_KEY\"]")
                val data = PoTokenCodec.challengeData(challenge)
                val response = session.runBotGuard(html, data)
                val (token, seconds) = PoTokenCodec.integrityToken(post("GenerateIT", "[\"$REQUEST_KEY\",\"$response\"]"))
                session.setIntegrityToken(token)
                session.expiresAt = clock() + (seconds - 600).coerceAtLeast(60) * 1000
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) { session.close() }
            throw e
        }
        return session
    }

    private suspend fun post(method: String, body: String): String = withContext(io) {
        val request = Request.Builder().url("https://www.youtube.com/api/jnn/v1/$method")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("x-goog-api-key", API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .post(body.toRequestBody("application/json+protobuf".toMediaType()))
            .build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}")
            it.body.string()
        }
    }

    /** Один WebView со своей сессией BotGuard. Обращения к WebView идут из главного потока. */
    @SuppressLint("SetJavaScriptEnabled")
    private class Session(context: Context) {
        var expiresAt = 0L
        private val webView = WebView(context)
        private val botguard = CompletableDeferred<String>()
        private val loaded = CompletableDeferred<Unit>()
        private var minted: CompletableDeferred<String>? = null

        init {
            webView.settings.javaScriptEnabled = true
            webView.settings.userAgentString = USER_AGENT
            webView.settings.blockNetworkLoads = true // странице сеть не нужна
            webView.addJavascriptInterface(this, JS)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.complete(Unit)
                }
            }
        }

        suspend fun runBotGuard(html: String, challenge: String): String {
            withContext(Dispatchers.Main) {
                webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            }
            loaded.await()
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "try { runBotGuard($challenge).then(function (result) {" +
                        " this.webPoSignalOutput = result.webPoSignalOutput; $JS.onBotguard(result.botguardResponse) }," +
                        " function (error) { $JS.onBotguardError(error + ' ' + error.stack) }) }" +
                        " catch (error) { $JS.onBotguardError(error + ' ' + error.stack) }",
                    null,
                )
            }
            return botguard.await()
        }

        suspend fun setIntegrityToken(expression: String) = withContext(Dispatchers.Main) {
            webView.evaluateJavascript("this.integrityToken = $expression", null)
        }

        suspend fun mint(identifier: String): String {
            val result = CompletableDeferred<String>()
            minted = result
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    "try { var poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, ${PoTokenCodec.stringToU8(identifier)});" +
                        " var text = ''; for (var i = 0; i < poTokenU8.length; i++) { if (i != 0) text += ','; text += poTokenU8[i] }" +
                        " $JS.onToken(text) } catch (error) { $JS.onTokenError(error + ' ' + error.stack) }",
                    null,
                )
            }
            return withTimeout(MINT_TIMEOUT_MS) { result.await() }
        }

        @JavascriptInterface
        fun onBotguard(response: String) {
            botguard.complete(response)
        }

        @JavascriptInterface
        fun onBotguardError(error: String) {
            botguard.completeExceptionally(IllegalStateException(error.take(200)))
        }

        @JavascriptInterface
        fun onToken(bytes: String) {
            runCatching { PoTokenCodec.toToken(bytes) }
                .onSuccess { minted?.complete(it) }
                .onFailure { minted?.completeExceptionally(it) }
        }

        @JavascriptInterface
        fun onTokenError(error: String) {
            minted?.completeExceptionally(IllegalStateException(error.take(200)))
        }

        fun close() {
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    companion object {
        private const val TAG = "Lumen/PoToken"
        private const val JS = "PoTokenWebView"

        // Публичные значения из запросов самого YouTube к BotGuard
        private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val INIT_TIMEOUT_MS = 30_000L
        private const val MINT_TIMEOUT_MS = 10_000L
        const val COOLDOWN_MS = 10 * 60_000L
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal abstract class PoTokenModule {
    @dagger.Binds
    abstract fun source(minter: PoTokenMinter): PoTokenSource
}

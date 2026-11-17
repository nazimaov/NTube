package tv.lumen.data.account

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.lumen.core.common.ApplicationScope
import tv.lumen.core.common.IoDispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Состояние входа в аккаунт Google. Пароль приложению не передаётся. */
sealed interface AccountState {
    data object SignedOut : AccountState
    data object Starting : AccountState

    /** Показать [userCode] и адрес [verificationUrl]: пользователь вводит код на телефоне. */
    data class WaitingForCode(val userCode: String, val verificationUrl: String) : AccountState
    data object SignedIn : AccountState
    data class Failed(val reason: Reason) : AccountState

    enum class Reason { NETWORK, UNAVAILABLE, DENIED, EXPIRED }
}

@Serializable
internal data class UserCodeDto(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_url") val verificationUrl: String = "https://www.youtube.com/activate",
    @SerialName("expires_in") val expiresIn: Int = 1800,
    val interval: Int = 5,
)

@Serializable
internal data class TokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 0,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val error: String? = null,
)

/**
 * Вход в аккаунт Google так, как это делает телевизионное приложение YouTube и SmartTube:
 * код на экране, подтверждение на телефоне. Токен хранится зашифрованным ([AccountStore]).
 * При выходе доступ отзывается на стороне Google. Вход необязателен.
 */
@Singleton
class GoogleAccount @Inject internal constructor(
    client: OkHttpClient,
    private val json: Json,
    private val store: AccountStore,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
    @Named(TV_PAGE_URL) private val tvPageUrl: String,
    @Named(OAUTH_BASE_URL) private val oauthBase: String,
    @Named(REVOKE_URL) private val revokeUrl: String,
) {
    private val http = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val _state = MutableStateFlow<AccountState>(
        if (store.refreshToken() != null) AccountState.SignedIn else AccountState.SignedOut,
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Не быстрее, чем раз в столько-то мс: сервер просит интервал, но не меньше 3 с, как у SmartTube. */
    internal var minPollMs = 3_000L

    private var signInJob: Job? = null
    private val tokenLock = Mutex()
    private var clientData: ClientData? = null
    private var visitor: String? = null
    private var accessToken: String? = null
    private var accessType: String = "Bearer"
    private var accessExpiresAt = 0L

    val isSignedIn: Boolean get() = _state.value == AccountState.SignedIn

    /** Начинает вход: получает код и ждёт подтверждения. Повторный вызов перезапускает вход. */
    fun startSignIn() {
        signInJob?.cancel()
        signInJob = scope.launch(io) { runSignIn() }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        if (_state.value !is AccountState.SignedIn) _state.value = AccountState.SignedOut
    }

    /** Выход: токен удаляется с устройства, доступ отзывается у Google (если сеть доступна). */
    fun signOut() {
        signInJob?.cancel()
        val token = store.refreshToken()
        store.clear()
        accessToken = null
        accessExpiresAt = 0
        _state.value = AccountState.SignedOut
        if (token != null) scope.launch(io) { revoke(token) }
    }

    /** Значение заголовка `Authorization` или null, если вход не выполнен или не удалось обновить токен. */
    suspend fun authorizationHeader(): String? = tokenLock.withLock {
        val refresh = store.refreshToken() ?: return@withLock null
        val now = System.currentTimeMillis()
        if (accessToken != null && now < accessExpiresAt - EXPIRY_MARGIN_MS) return@withLock "$accessType $accessToken"
        withContext(io) {
            val data = loadClientData() ?: return@withContext null
            val token = postToken(
                """{"refresh_token":"$refresh","client_id":"${data.clientId}","client_secret":"${data.clientSecret}","grant_type":"${TvClient.GRANT_REFRESH}"}""",
            ) ?: return@withContext null
            if (token.error == "invalid_grant") {
                // Доступ отозван на стороне Google: нужно войти заново
                store.clear()
                _state.value = AccountState.SignedOut
                return@withContext null
            }
            val access = token.accessToken ?: return@withContext null
            accessToken = access
            accessType = token.tokenType
            accessExpiresAt = System.currentTimeMillis() + token.expiresIn * 1000L
            "$accessType $access"
        }
    }

    private suspend fun runSignIn() {
        _state.value = AccountState.Starting
        try {
            val data = loadClientData()
            if (data == null) {
                _state.value = AccountState.Failed(AccountState.Reason.UNAVAILABLE)
                return
            }
            val code = requestUserCode(data)
            if (code == null || code.deviceCode.isEmpty()) {
                _state.value = AccountState.Failed(AccountState.Reason.NETWORK)
                return
            }
            _state.value = AccountState.WaitingForCode(code.userCode, code.verificationUrl)
            pollForToken(data, code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Вход не удался: ${e.javaClass.simpleName}")
            _state.value = AccountState.Failed(AccountState.Reason.NETWORK)
        }
    }

    private suspend fun pollForToken(data: ClientData, code: UserCodeDto) {
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        var interval = maxOf(code.interval * 1000L, minPollMs)
        var failures = 0
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            delay(interval)
            val token = postToken(
                """{"code":"${code.deviceCode}","client_id":"${data.clientId}","client_secret":"${data.clientSecret}","grant_type":"${TvClient.GRANT_DEVICE}"}""",
            )
            if (token == null) {
                if (++failures >= MAX_FAILURES) {
                    _state.value = AccountState.Failed(AccountState.Reason.NETWORK)
                    return
                }
                continue
            }
            failures = 0
            val refresh = token.refreshToken
            when {
                refresh != null -> {
                    store.saveRefreshToken(refresh)
                    token.accessToken?.let {
                        accessToken = it
                        accessType = token.tokenType
                        accessExpiresAt = System.currentTimeMillis() + token.expiresIn * 1000L
                    }
                    _state.value = AccountState.SignedIn
                    return
                }
                token.error == "slow_down" -> interval += 5_000
                token.error == "access_denied" -> {
                    _state.value = AccountState.Failed(AccountState.Reason.DENIED)
                    return
                }
                token.error == "expired_token" -> {
                    _state.value = AccountState.Failed(AccountState.Reason.EXPIRED)
                    return
                }
                // authorization_pending и всё остальное — ждём дальше
            }
        }
        if (_state.value is AccountState.WaitingForCode) _state.value = AccountState.Failed(AccountState.Reason.EXPIRED)
    }

    /** Идентификатор и секрет клиента ищутся в скрипте страницы YouTube TV; запоминаются на время работы. */
    /** Идентификатор посетителя со страницы YouTube TV: без него лента пустая или нерелевантная. */
    internal suspend fun visitorData(): String? = withContext(io) {
        loadClientData()
        visitor
    }

    private fun loadClientData(): ClientData? {
        clientData?.let { return it }
        val page = getText(tvPageUrl) ?: return null
        visitor = Regex("\"visitorData\":\"(.*?)\"").find(page)?.groupValues?.get(1)
        TvClient.useLiveVersion(page)
        val origin = tvPageUrl.toHttpUrl().newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        val url = ClientDataParser.scriptUrl(page, origin) ?: return null
        val data = getText(url)?.let(ClientDataParser::clientData)
            ?: getText(ClientDataParser.legacyScriptUrl(url))?.let(ClientDataParser::clientData)
        return data?.also { clientData = it }
    }

    private fun requestUserCode(data: ClientData): UserCodeDto? {
        val body = """{"client_id":"${data.clientId}","device_id":"${store.deviceId()}","model_name":"${TvClient.DEVICE_MODEL}","scope":"${TvClient.SCOPE}"}"""
        val text = postJson("$oauthBase/device/code", body) ?: return null
        return runCatching { json.decodeFromString<UserCodeDto>(text) }.getOrNull()
    }

    private fun postToken(body: String): TokenDto? {
        val text = postJson("$oauthBase/token", body) ?: return null
        return runCatching { json.decodeFromString<TokenDto>(text) }.getOrNull()
    }

    private fun revoke(token: String) {
        try {
            val request = Request.Builder().url("$revokeUrl?token=$token").post("".toRequestBody()).build()
            http.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Отзыв доступа не выполнен: ${e.javaClass.simpleName}")
        }
    }

    private fun getText(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", TvClient.USER_AGENT).header("Referer", TvClient.REFERER).build()
        http.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
    } catch (e: Exception) {
        null
    }

    /** Ответ с кодом 4xx тоже разбирается: так сервер сообщает «ожидаем подтверждения». */
    private fun postJson(url: String, body: String): String? = try {
        val request = Request.Builder().url(url)
            .header("User-Agent", TvClient.USER_AGENT)
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { it.body.string() }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val TV_PAGE_URL = "account.tvPageUrl"
        const val OAUTH_BASE_URL = "account.oauthBaseUrl"
        const val REVOKE_URL = "account.revokeUrl"
        private const val TAG = "Lumen/Account"
        private const val MAX_FAILURES = 5
        private const val EXPIRY_MARGIN_MS = 60_000L
        private val JSON = "application/json".toMediaType()
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal object AccountModule {
    @dagger.Provides
    @Named(GoogleAccount.TV_PAGE_URL)
    fun tvPageUrl(): String = "https://www.youtube.com/tv"

    @dagger.Provides
    @Named(GoogleAccount.OAUTH_BASE_URL)
    fun oauthBase(): String = "https://www.youtube.com/o/oauth2"

    @dagger.Provides
    @Named(GoogleAccount.REVOKE_URL)
    fun revokeUrl(): String = "https://oauth2.googleapis.com/revoke"

    @dagger.Provides
    @Singleton
    fun store(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): AccountStore = KeystoreAccountStore(context)
}

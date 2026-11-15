package tv.lumen.data.updater.internal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.lumen.core.common.IoDispatcher
import tv.lumen.data.updater.UpdateConfig
import tv.lumen.data.updater.UpdateInfo
import tv.lumen.data.updater.UpdateManager
import tv.lumen.data.updater.UpdateState
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Serializable
internal data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
internal data class AssetDto(
    val name: String,
    val size: Long = -1,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Singleton
internal class GitHubUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val json: Json,
    private val config: UpdateConfig,
    @Named(API_URL_NAME) private val apiUrl: String,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UpdateManager {

    // Без HTTP-кэша: ответ GitHub должен быть свежим, APK не кэшируется
    private val http = client.newBuilder().cache(null).build()
    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    internal var clock: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    override suspend fun check(force: Boolean, userInitiated: Boolean) {
        if (!config.isConfigured) {
            if (userInitiated) _state.value = UpdateState.Failed(UpdateState.Reason.NOT_CONFIGURED)
            return
        }
        val now = clock()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        if (userInitiated) _state.value = UpdateState.Checking
        val result = fetchLatest()
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        _state.value = when {
            result == null -> if (userInitiated) UpdateState.Failed(UpdateState.Reason.NETWORK) else UpdateState.Idle
            !Versions.isNewer(result.tagName, config.currentVersion) -> if (userInitiated) UpdateState.UpToDate else UpdateState.Idle
            else -> toInfo(result)?.let(UpdateState::Available)
                ?: if (userInitiated) UpdateState.Failed(UpdateState.Reason.NO_APK) else UpdateState.Idle
        }
    }

    /** GET /repos/{owner}/{repo}/releases/latest (SPEC 10.5, п. 1). */
    internal suspend fun fetchLatest(): ReleaseDto? = withContext(io) {
        try {
            val request = Request.Builder()
                .url("$apiUrl/repos/${config.repoOwner}/${config.repoName}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                json.decodeFromString<ReleaseDto>(response.body.string())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Проверка обновления не удалась: ${e.message}")
            null
        }
    }

    /** Первый файл .apk из assets (SPEC 10.5, п. 3). */
    internal fun toInfo(release: ReleaseDto): UpdateInfo? {
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return UpdateInfo(release.tagName.removePrefix("v"), release.body.orEmpty(), apk.downloadUrl, apk.size)
    }

    override suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!canInstall()) {
            openUnknownSourcesSettings()
            return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        val file = download(info) ?: run {
            _state.value = UpdateState.Failed(UpdateState.Reason.NETWORK)
            return
        }
        _state.value = UpdateState.Installing(info)
        val ok = withContext(io) { runCatching { install(file) }.onFailure { Log.w(TAG, "Установка", it) }.isSuccess }
        if (!ok) _state.value = UpdateState.Failed(UpdateState.Reason.INSTALL_FAILED)
    }

    /** В cacheDir/updates/ с прогрессом (SPEC 10.5, п. 3). */
    private suspend fun download(info: UpdateInfo): File? = withContext(io) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "lumen-${info.version}.apk")
        try {
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                var read = 0L
                var lastReported = 0f
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            val progress = if (total > 0) read.toFloat() / total else -1f
                            if (progress < 0 || progress - lastReported >= 0.01f) {
                                lastReported = progress
                                _state.value = UpdateState.Downloading(info, progress)
                            }
                        }
                    }
                }
            }
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Загрузка обновления", e)
            null
        }
    }

    /** PackageInstaller: сессия → запись → commit; подтверждение показывает система (SPEC 10.5, п. 4). */
    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("lumen.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    private fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun dismiss() {
        _state.value = UpdateState.Idle
    }

    companion object {
        const val API_URL_NAME = "updater.apiUrl"
        const val DEFAULT_API_URL = "https://api.github.com"
        private const val CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L
        private const val KEY_LAST_CHECK = "last_check"
        private const val TAG = "Lumen/Updater"
    }
}
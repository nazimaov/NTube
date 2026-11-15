package tv.lumen.data.updater

import kotlinx.coroutines.flow.StateFlow

/** Где искать релизы и какая версия установлена. Значения даёт модуль app из BuildConfig. */
data class UpdateConfig(
    val repoOwner: String,
    val repoName: String,
    val currentVersion: String,
) {
    /** Владелец не задан (CHANGE_ME) — самообновление выключено. */
    val isConfigured: Boolean get() = repoOwner.isNotBlank() && repoOwner != "CHANGE_ME" && repoName.isNotBlank()
}

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** progress 0..1; -1 — размер неизвестен. */
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val reason: Reason) : UpdateState

    enum class Reason { NETWORK, NO_APK, INSTALL_FAILED, NOT_CONFIGURED }
}

/** Самообновление из релизов GitHub (SPEC 10.5). Один экземпляр на приложение. */
interface UpdateManager {
    val state: StateFlow<UpdateState>

    /**
     * Проверить наличие новой версии. Без [force] — не чаще раза в 3 часа.
     * [userInitiated] — показать результат «последняя версия» и ошибки (кнопка в настройках).
     */
    suspend fun check(force: Boolean, userInitiated: Boolean = false)

    /** Скачать и установить; при отсутствии разрешения открывает системный экран «Неизвестные источники». */
    suspend fun downloadAndInstall(info: UpdateInfo)

    fun dismiss()
}
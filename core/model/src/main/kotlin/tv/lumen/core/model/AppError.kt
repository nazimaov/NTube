package tv.lumen.core.model

/** Единственный вид ошибок, который выходит наружу из data-модулей (SPEC, раздел 3). */
sealed interface AppError {
    /** Нет сети, таймаут, ошибка HTTP. */
    data object Network : AppError

    /** Контент существует, но недоступен: приватный, удалён, заблокирован в стране и т. п. */
    data class ContentUnavailable(val reason: UnavailableReason, val message: String? = null) : AppError

    /** Экстрактор не смог разобрать ответ YouTube — нужна новая версия приложения. */
    data object ExtractionBroken : AppError

    data object AgeRestricted : AppError

    data class Unknown(val message: String?) : AppError
}

enum class UnavailableReason {
    PRIVATE,
    GEO_BLOCKED,
    PAID,
    ACCOUNT_TERMINATED,
    /** YouTube требует подтвердить, что запрос не от бота (капча, «Sign in to confirm»). */
    BOT_CHECK,
    UNSUPPORTED,
    /** Видео «для детей»: YouTube отдаёт его сторонним приложениям только после входа в аккаунт. */
    SIGN_IN_REQUIRED,
    OTHER,
}

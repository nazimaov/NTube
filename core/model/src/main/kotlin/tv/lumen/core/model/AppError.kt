package tv.lumen.core.model

/** Единственный вид ошибок, который выходит наружу из data-модулей (SPEC, раздел 3). */
sealed interface AppError {
    data object Network : AppError
    data class ContentUnavailable(val reason: String) : AppError
    data object ExtractionBroken : AppError
    data object AgeRestricted : AppError
    data class Unknown(val message: String?) : AppError
}
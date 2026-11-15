package tv.lumen.data.dearrow

/** Нейтральные заголовок и кадр-обложка от сообщества (SPEC 6.2). */
data class Branding(
    /** null — оставить оригинальный заголовок. */
    val title: String?,
    /** Секунда кадра для обложки; null — оригинальная обложка. */
    val thumbnailTime: Double?,
)

interface DeArrowRepository {
    /** null — брендинга нет или сервер недоступен (сбой мода игнорируется). */
    suspend fun branding(videoId: String): Branding?

    fun thumbnailUrl(videoId: String, time: Double): String
}
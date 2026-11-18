package tv.lumen.data.youtube

/**
 * Запрос `player` телевизионного клиента YouTube от имени аккаунта (реализация — в app через data:account).
 * Нужен для видео «для детей»: NewPipeExtractor v0.27 их не отдаёт (клиенты, которые умели, удалены из
 * библиотеки), а телевизионный клиент со входом — отдаёт, как в SmartTube.
 */
interface AuthorizedPlayer {
    /** Выполнен вход в аккаунт: без него запасной путь не работает. */
    val isSignedIn: Boolean

    /** Тело ответа `player` или null — входа нет либо запрос не удался. [signatureTimestamp] — от плеера, которым расшифровываются ссылки. */
    suspend fun playerJson(videoId: String, signatureTimestamp: Int?): String?
}

package tv.lumen.data.youtube.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.HttpResponseException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.UnsupportedTabException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.CreationException
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.UnavailableReason
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Перевод исключений экстрактора в [AppError] (SPEC 4.5). Порядок веток важен: от частного к общему. */
internal fun Throwable.toAppError(): AppError = when (this) {
    is AgeRestrictedContentException -> AppError.AgeRestricted
    is PrivateContentException -> unavailable(UnavailableReason.PRIVATE)
    is GeographicRestrictionException,
    is UnsupportedContentInCountryException -> unavailable(UnavailableReason.GEO_BLOCKED)
    is PaidContentException,
    is YoutubeMusicPremiumContentException,
    is SoundCloudGoPlusContentException -> unavailable(UnavailableReason.PAID)
    is AccountTerminatedException -> unavailable(UnavailableReason.ACCOUNT_TERMINATED)
    is ContentNotAvailableException -> unavailable(UnavailableReason.OTHER)
    is SignInConfirmNotBotException,
    is ReCaptchaException -> unavailable(UnavailableReason.BOT_CHECK)
    is ContentNotSupportedException,
    is UnsupportedTabException -> unavailable(UnavailableReason.UNSUPPORTED)
    is HttpResponseException -> AppError.Network
    is IOException -> AppError.Network
    is CreationException -> AppError.ExtractionBroken
    // ParsingException и прочие ошибки разбора
    is ExtractionException -> AppError.ExtractionBroken
    else -> AppError.Unknown(message ?: this::class.java.simpleName)
}

private const val LOG_TAG = "Lumen/YouTube"

private fun Throwable.unavailable(reason: UnavailableReason) = AppError.ContentUnavailable(reason, message)

/**
 * Выполняет блокирующий вызов экстрактора на [io] и упаковывает результат.
 * Отмена корутины пробрасывается, всё остальное становится [AppResult.Failure].
 */
internal suspend fun <T> extract(io: CoroutineDispatcher, block: () -> T): AppResult<T> =
    withContext(io) {
        try {
            AppResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Только локальный logcat для диагностики; никуда не отправляется
            android.util.Log.w(LOG_TAG, "Ошибка экстрактора", e)
            AppResult.Failure(e.toAppError())
        } catch (e: StackOverflowError) {
            // Rhino при расшифровке подписи иногда уходит в глубокую рекурсию
            AppResult.Failure(AppError.ExtractionBroken)
        }
    }

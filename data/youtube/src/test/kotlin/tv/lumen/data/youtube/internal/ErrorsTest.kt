package tv.lumen.data.youtube.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import tv.lumen.core.model.AppError
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.UnavailableReason
import java.io.IOException
import java.net.SocketTimeoutException

class ErrorsTest {

    @Test
    fun `исключения экстрактора переводятся в AppError`() {
        assertEquals(AppError.AgeRestricted, AgeRestrictedContentException("age").toAppError())
        assertReason(UnavailableReason.PRIVATE, PrivateContentException("private"))
        assertReason(UnavailableReason.GEO_BLOCKED, GeographicRestrictionException("geo"))
        assertReason(UnavailableReason.OTHER, ContentNotAvailableException("gone"))
        assertReason(UnavailableReason.BOT_CHECK, ReCaptchaException("captcha", "https://youtube.com"))
        assertEquals(AppError.ExtractionBroken, ParsingException("bad json").toAppError())
        assertEquals(AppError.Network, IOException("offline").toAppError())
        assertEquals(AppError.Network, SocketTimeoutException().toAppError())
        assertTrue(IllegalStateException("x").toAppError() is AppError.Unknown)
    }

    @Test
    fun `extract возвращает Failure вместо исключения`() = runTest {
        val result = extract(StandardTestDispatcher(testScheduler)) { throw ParsingException("broken") }
        assertEquals(AppResult.Failure(AppError.ExtractionBroken), result)
    }

    @Test
    fun `extract возвращает значение`() = runTest {
        assertEquals(AppResult.Success(42), extract(StandardTestDispatcher(testScheduler)) { 42 })
    }

    @Test(expected = CancellationException::class)
    fun `отмена не превращается в ошибку`() = runTest {
        extract(StandardTestDispatcher(testScheduler)) { throw CancellationException("cancel") }
    }

    private fun assertReason(expected: UnavailableReason, e: Exception) {
        val error = e.toAppError()
        assertTrue("$e → $error", error is AppError.ContentUnavailable && error.reason == expected)
    }
}

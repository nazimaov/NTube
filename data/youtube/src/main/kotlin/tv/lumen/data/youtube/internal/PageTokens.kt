package tv.lumen.data.youtube.internal

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.schabi.newpipe.extractor.Page
import tv.lumen.core.model.PageToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Страница экстрактора ([Page]) сериализуется в строку, чтобы наружу уходил только [PageToken].
 * Токен переживает пересоздание ViewModel и не требует хранить состояние в репозитории.
 */
internal object PageTokens {

    fun encode(page: Page?): PageToken? {
        if (page == null) return null
        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(page) }
            buffer.toByteArray()
        }
        return PageToken(bytes.toByteString().base64Url())
    }

    fun decode(token: PageToken): Page {
        val bytes = requireNotNull(token.value.decodeBase64()) { "Повреждённый PageToken" }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as Page }
    }
}

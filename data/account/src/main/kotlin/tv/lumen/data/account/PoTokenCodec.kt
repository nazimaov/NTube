package tv.lumen.data.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * Разбор ответов сервиса проверки YouTube (BotGuard) и перевод данных между Kotlin и JavaScript.
 * Формат и приёмы — как у NewPipe (GPLv3), файл `JavaScriptUtil.kt`.
 */
internal object PoTokenCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** Описание задания из ответа `jnn/v1/Create`: объект, который принимает `runBotGuard` в JavaScript. */
    fun challengeData(raw: String): String {
        val scrambled = json.parseToJsonElement(raw).jsonArray
        val second = scrambled.getOrNull(1) as? JsonPrimitive
        val data: JsonArray = if (scrambled.size > 1 && second != null && second.isString) {
            json.parseToJsonElement(descramble(second.content)).jsonArray
        } else {
            scrambled[0].jsonArray
        }
        fun text(i: Int) = (data.getOrNull(i) as? JsonPrimitive)?.contentOrNull
        fun firstString(i: Int): String? = (data.getOrNull(i) as? JsonArray)?.firstNotNullOfOrNull {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
        }
        val obj = buildJsonObject {
            put("messageId", text(0))
            put("interpreterJavascript", buildJsonObject {
                put("privateDoNotAccessOrElseSafeScriptWrappedValue", firstString(1))
                put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", firstString(2))
            })
            put("interpreterHash", text(3))
            put("program", text(4))
            put("globalName", text(5))
            put("clientExperimentsStateBlob", text(7))
        }
        return obj.toString()
    }

    /** Токен целостности из ответа `GenerateIT`: выражение `Uint8Array` для JavaScript и срок жизни в секундах. */
    fun integrityToken(raw: String): Pair<String, Long> {
        val array = json.parseToJsonElement(raw).jsonArray
        val token = array[0].jsonPrimitive.content
        val seconds = array[1].jsonPrimitive.content.toLong()
        return uint8Array(base64Bytes(token)) to seconds
    }

    /** Строка (например, visitorData) как выражение `Uint8Array` для JavaScript. */
    fun stringToU8(value: String): String = uint8Array(value.toByteArray())

    /** Результат `obtainPoToken`, числа через запятую, в base64 формата YouTube. */
    fun toToken(bytes: String): String = bytes.split(",")
        .map { it.trim().toInt().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace('+', '-')
        .replace('/', '_')

    /** Сбой разбора: YouTube изменил формат. */
    class FormatException(message: String) : Exception(message)

    private fun descramble(scrambled: String): String =
        base64Bytes(scrambled).map { (it + 97).toByte() }.toByteArray().decodeToString()

    private fun base64Bytes(text: String): ByteArray {
        val standard = text.replace('-', '+').replace('_', '/').replace('.', '=')
        return standard.decodeBase64()?.toByteArray() ?: throw FormatException("base64")
    }

    private fun uint8Array(bytes: ByteArray) =
        "new Uint8Array([" + bytes.joinToString(",") { (it.toInt() and 0xFF).toString() } + "])"
}

private fun kotlinx.serialization.json.JsonObjectBuilder.put(key: String, value: String?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}

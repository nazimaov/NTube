package tv.lumen.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Хранилище данных входа. Токен лежит только в зашифрованном виде. */
internal interface AccountStore {
    fun refreshToken(): String?
    fun saveRefreshToken(token: String)
    fun clear()

    /** Постоянный случайный идентификатор этого устройства для запроса кода входа. */
    fun deviceId(): String
}

/**
 * refresh-токен шифруется AES-GCM, ключ создаётся в Android Keystore и из него не извлекается:
 * скопированный файл настроек или резервная копия без этого ключа бесполезны.
 */
internal class KeystoreAccountStore(context: Context) : AccountStore {
    private val prefs = context.getSharedPreferences("google_account", Context.MODE_PRIVATE)

    override fun refreshToken(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            // Ключ потерян (сброс экрана блокировки, восстановление на другом устройстве) — нужно войти заново
            Log.w("Lumen/Account", "Токен не расшифровать: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun saveRefreshToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    override fun deviceId(): String =
        prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().replace("-", "").take(16).also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "lumen_account_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_TOKEN = "refresh_token"
        const val KEY_DEVICE = "device_id"
    }
}

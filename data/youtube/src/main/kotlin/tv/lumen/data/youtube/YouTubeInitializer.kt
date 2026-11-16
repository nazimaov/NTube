package tv.lumen.data.youtube

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import tv.lumen.data.youtube.internal.OkHttpDownloader
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Однократная инициализация NewPipeExtractor (SPEC 4.2). Вызывается в Application.onCreate;
 * репозиторий дополнительно вызывает [ensureInitialized] перед каждым запросом.
 */
@Singleton
class YouTubeInitializer @Inject constructor(private val client: OkHttpClient) {

    fun initialize(locale: Locale = Locale.getDefault()) {
        ensureInitialized(client, locale)
    }

    /** Язык и страну контента меняют настройки (фаза 5). */
    /** Язык и страна контента из настроек; null — системная локаль (SPEC 4.2, 9.2). */
    fun applyContentLocale(language: String?, country: String?) {
        val system = Locale.getDefault()
        setContentLocale(language ?: system.language, country ?: system.country.ifBlank { "US" })
    }

    fun setContentLocale(language: String, country: String) {
        ensureInitialized(client, Locale.getDefault())
        NewPipe.setupLocalization(localization(language, country), ContentCountry(country))
    }

    companion object {
        @Volatile
        private var initialized = false

        internal fun ensureInitialized(client: OkHttpClient, locale: Locale = Locale.getDefault()) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                val country = locale.country.ifBlank { DEFAULT_COUNTRY }
                NewPipe.init(
                    OkHttpDownloader(client),
                    localization(locale.language.ifBlank { DEFAULT_LANGUAGE }, country),
                    ContentCountry(country),
                )
                initialized = true
            }
        }

        private fun localization(language: String, country: String) =
            Localization(language, country.ifBlank { null })

        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_COUNTRY = "US"
    }
}

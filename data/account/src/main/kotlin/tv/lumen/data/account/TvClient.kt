package tv.lumen.data.account

/**
 * Как приложение представляется YouTube при входе в аккаунт: как телевизионный клиент YouTube (Fire TV).
 * Протокол входа и значения повторяют SmartTube (MediaServiceCore, GPLv3, автор Yuriy Liskov):
 * https://github.com/yuliskov/MediaServiceCore
 */
internal object TvClient {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux armeabi-v7a; Android 7.1.2; Fire OS 6.0) Cobalt/22.lts.3.306369-gold (unlike Gecko) " +
            "v8/8.8.278.8-jit gles Starboard/13, Amazon_ATV_mediatek8695_2019/NS6294 (Amazon, AFTMM, Wireless) " +
            "com.amazon.firetv.youtube/22.3.r2.v66.0"
    const val REFERER = "https://www.youtube.com/tv"
    const val SCRIPTS_BASE = "https://www.youtube.com"

    /** Права, которые запрашивает телевизионное приложение YouTube. */
    const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
    const val DEVICE_MODEL = "ytlr::"
    const val GRANT_DEVICE = "http://oauth.net/grant_type/device/1.0"
    const val GRANT_REFRESH = "refresh_token"
}

/** Идентификатор и секрет клиента YouTube TV, найденные в его скрипте. Не хранятся и в репозиторий не попадают. */
internal data class ClientData(val clientId: String, val clientSecret: String)

internal object ClientDataParser {
    // Адрес основного скрипта на странице https://www.youtube.com/tv (три варианта вёрстки)
    private val SCRIPT_URL = listOf(
        Regex("""id="base-js" src="(.*?)""""),
        Regex("""\.src = '(.*?m=base)'"""),
        Regex("""\.src = '(.*?)'; .\.id = 'base-js'"""),
    )

    // Берётся первое совпадение: остальные содержат чужие идентификаторы и дают ошибку 401
    private val CLIENT = Regex("""clientId:"([-\w]+\.apps\.googleusercontent\.com)",\n?[$\w]+:"(\w+)"""")

    /** [base] — «https://www.youtube.com»: от него строится адрес, если на странице указан относительный путь. */
    fun scriptUrl(page: String, base: String = TvClient.SCRIPTS_BASE): String? {
        val raw = SCRIPT_URL.firstNotNullOfOrNull { it.find(page)?.groupValues?.get(1) } ?: return null
        return tidyUrl(raw, base)
    }

    fun clientData(script: String): ClientData? {
        val match = CLIENT.find(script) ?: return null
        return ClientData(match.groupValues[1], match.groupValues[2])
    }

    /** Запасной адрес для старого скрипта: основной скрипт заменяется на «main». */
    fun legacyScriptUrl(url: String): String = url.replace("/dg=0/", "/exm=base/ed=1/").replace("/m=base", "/m=main")

    internal fun tidyUrl(url: String, base: String = TvClient.SCRIPTS_BASE): String {
        val unescaped = url.replace("\\/", "/").replace("\\x3d", "=")
        return if (unescaped.startsWith("/")) base + unescaped else unescaped
    }
}

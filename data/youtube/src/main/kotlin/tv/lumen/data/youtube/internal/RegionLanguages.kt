package tv.lumen.data.youtube.internal

/** Основной язык страны — для регионального поиска, когда у YouTube нет чартов страны. */
internal object RegionLanguages {
    private val map = mapOf(
        "RU" to "ru", "AZ" to "az", "TR" to "tr", "KZ" to "kk", "UA" to "uk", "BY" to "be", "UZ" to "uz",
        "KG" to "ky", "TJ" to "tg", "AM" to "hy", "GE" to "ka", "MD" to "ro", "US" to "en", "GB" to "en",
        "DE" to "de", "FR" to "fr", "ES" to "es", "IT" to "it", "PT" to "pt", "BR" to "pt", "PL" to "pl",
        "NL" to "nl", "JP" to "ja", "KR" to "ko", "CN" to "zh", "IN" to "hi", "IR" to "fa", "AE" to "ar",
        "SA" to "ar", "EG" to "ar", "IL" to "he",
    )

    fun languageFor(country: String): String = map[country.uppercase()] ?: "en"
}

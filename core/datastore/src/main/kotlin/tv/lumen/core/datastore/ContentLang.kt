package tv.lumen.core.datastore

/** Язык подборок: «ru», «tr», «az» или «en» (для остальных — английский). Одна логика для всех разделов. */
object ContentLang {
    private val russian = setOf("RU", "BY", "KZ", "KG", "UZ", "UA", "AM", "GE", "MD", "TJ", "TM")

    /**
     * [byRegion] включено — язык страны контента из настроек (пока страна не выбрана — язык устройства);
     * выключено — язык устройства [deviceLanguage].
     * [dubbed] — для контента с дубляжом (детские мультики и шоу): в Азербайджане дублируют на турецкий, поэтому «tr».
     */
    fun code(country: String?, byRegion: Boolean, deviceLanguage: String, dubbed: Boolean = false): String {
        if (byRegion && !country.isNullOrBlank()) {
            val c = country.uppercase()
            return when {
                c == "AZ" -> if (dubbed) "tr" else "az"
                c == "TR" -> "tr"
                c in russian -> "ru"
                else -> "en"
            }
        }
        return if (deviceLanguage in setOf("ru", "tr", "az")) deviceLanguage else "en"
    }
}

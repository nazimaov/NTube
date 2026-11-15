package tv.lumen.core.model

/** Категории SponsorBlock (SPEC 6.1); [apiName] — значение в API. */
enum class SponsorCategory(val apiName: String) {
    SPONSOR("sponsor"),
    SELF_PROMO("selfpromo"),
    INTERACTION("interaction"),
    INTRO("intro"),
    OUTRO("outro"),
    PREVIEW("preview"),
    MUSIC_OFFTOPIC("music_offtopic"),
    FILLER("filler"),
    ;

    companion object {
        fun fromApi(name: String): SponsorCategory? = entries.firstOrNull { it.apiName == name }
    }
}

/** Режим категории: пропускать, показать кнопку пропуска, выключено (SPEC 6.1). */
enum class SegmentMode { SKIP, BUTTON, OFF }

data class Segment(
    val startMs: Long,
    val endMs: Long,
    val category: SponsorCategory,
    val uuid: String,
)
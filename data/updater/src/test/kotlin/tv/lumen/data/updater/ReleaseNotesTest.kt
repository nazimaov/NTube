package tv.lumen.data.updater

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    private val bilingual = """
        ## ru
        - Новое меню
        - **Быстрее** видео
        ## en
        - New menu
        - Faster video
    """.trimIndent()

    @Test
    fun picksSectionOfInterfaceLanguage() {
        assertEquals("• Новое меню\n• Быстрее видео", ReleaseNotes.select(bilingual, "ru"))
        assertEquals("• New menu\n• Faster video", ReleaseNotes.select(bilingual, "en"))
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals("• New menu\n• Faster video", ReleaseNotes.select(bilingual, "tr"))
    }

    @Test
    fun plainNotesAreCleanedAndAutoChangelogDropped() {
        val raw = "## Что нового\n- Раз\n- Два\n\n**Full Changelog**: https://github.com/x/compare/v1...v2"
        assertEquals("Что нового\n• Раз\n• Два", ReleaseNotes.select(raw, "ru"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", ReleaseNotes.select("**Full Changelog**: https://x", "ru"))
    }
}

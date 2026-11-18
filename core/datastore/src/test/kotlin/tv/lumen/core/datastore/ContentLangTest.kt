package tv.lumen.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentLangTest {
    @Test
    fun byRegionUsesCountryLanguage() {
        assertEquals("az", ContentLang.code("AZ", true, "ru"))
        assertEquals("tr", ContentLang.code("tr", true, "en"))
        assertEquals("ru", ContentLang.code("KZ", true, "en"))
        assertEquals("en", ContentLang.code("DE", true, "ru"))
    }

    @Test
    fun dubbedContentInAzerbaijanIsTurkish() {
        assertEquals("tr", ContentLang.code("AZ", true, "ru", dubbed = true))
        assertEquals("az", ContentLang.code("AZ", true, "ru", dubbed = false))
        assertEquals("ru", ContentLang.code("RU", true, "en", dubbed = true))
    }

    @Test
    fun withoutRegionOrWhenOffUsesDeviceLanguage() {
        assertEquals("ru", ContentLang.code(null, true, "ru"))
        assertEquals("ru", ContentLang.code("AZ", false, "ru"))
        assertEquals("en", ContentLang.code("AZ", false, "de"))
    }
}

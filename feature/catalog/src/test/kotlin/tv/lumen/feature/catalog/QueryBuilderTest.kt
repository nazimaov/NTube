package tv.lumen.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.lumen.core.datastore.DubLanguage

class QueryBuilderTest {
    private val turkishSeries = Categories.series.first { it.id == "turkish" }
    private val newSeries = Categories.series.first { it.id == "new" }

    private fun series(c: Category, dub: DubLanguage, genre: Genre = Genre.ANY, year: Int? = null, ui: QueryLang = QueryLang.RU) =
        QueryBuilder.build(CatalogKind.SERIES, c, dub, genre, year, ui)

    @Test
    fun `оригинал для турецких - запрос по-турецки даже в русском интерфейсе`() {
        assertEquals("Türk dizi tüm bölümler", series(turkishSeries, DubLanguage.ORIGINAL))
    }

    @Test
    fun `русская озвучка - запрос по-русски`() {
        assertEquals("турецкий сериал все серии", series(turkishSeries, DubLanguage.RU))
    }

    @Test
    fun `азербайджанская озвучка, жанр и год`() {
        assertEquals(
            "türk serial komediya bütün seriyalar 2025",
            series(turkishSeries, DubLanguage.AZ, Genre.COMEDY, 2025),
        )
    }

    @Test
    fun `оригинал без страны - язык интерфейса`() {
        assertEquals("новый сериал драма все серии", series(newSeries, DubLanguage.ORIGINAL, Genre.DRAMA, ui = QueryLang.RU))
        assertEquals("new series drama full episodes", series(newSeries, DubLanguage.ORIGINAL, Genre.DRAMA, ui = QueryLang.EN))
    }

    @Test
    fun `фильмы - английский запрос без дублирования слова movie`() {
        val turkish = Categories.movies.first { it.id == "turkish" }
        assertEquals(
            "Turkish action full movie",
            QueryBuilder.build(CatalogKind.MOVIES, turkish, DubLanguage.EN, Genre.ACTION, null, QueryLang.RU),
        )
        assertEquals(
            "Türk film aksiyon tek parça",
            QueryBuilder.build(CatalogKind.MOVIES, turkish, DubLanguage.ORIGINAL, Genre.ACTION, null, QueryLang.RU),
        )
    }

    @Test
    fun `документальные - своё существительное вместо слова фильм`() {
        val doc = Categories.movies.first { it.id == "documentary" }
        assertEquals("документальный фильм полностью", QueryBuilder.build(CatalogKind.MOVIES, doc, DubLanguage.RU, Genre.ANY, null, QueryLang.RU))
    }
}

package tv.lumen.feature.catalog

import androidx.annotation.StringRes
import tv.lumen.core.datastore.DubLanguage

/** Раздел бокового меню: фильмы или сериалы. */
enum class CatalogKind { MOVIES, SERIES }

/** Язык, на котором пишется поисковый запрос, — им определяется и язык найденной озвучки. */
enum class QueryLang { RU, TR, AZ, EN }

private fun terms(ru: String, tr: String, az: String, en: String) =
    mapOf(QueryLang.RU to ru, QueryLang.TR to tr, QueryLang.AZ to az, QueryLang.EN to en)

/** Жанр: слова для запроса на каждом языке. */
enum class Genre(@StringRes val label: Int, val terms: Map<QueryLang, String>) {
    ANY(R.string.genre_any, emptyMap()),
    ACTION(R.string.genre_action, terms("боевик", "aksiyon", "aksiyon", "action")),
    COMEDY(R.string.genre_comedy, terms("комедия", "komedi", "komediya", "comedy")),
    DRAMA(R.string.genre_drama, terms("драма", "dram", "dram", "drama")),
    ROMANCE(R.string.genre_romance, terms("мелодрама", "romantik", "romantik", "romance")),
    HORROR(R.string.genre_horror, terms("ужасы", "korku", "dəhşət", "horror")),
    SCIFI(R.string.genre_scifi, terms("фантастика", "bilim kurgu", "elmi-fantastika", "sci-fi")),
    THRILLER(R.string.genre_thriller, terms("детектив триллер", "polisiye gerilim", "detektiv triller", "mystery thriller")),
    HISTORICAL(R.string.genre_historical, terms("исторический", "tarihi", "tarixi", "historical")),
}

/**
 * Подборка: чип сверху. Запрос собирается из [adjective] («турецкий»), [noun] (по умолчанию
 * «фильм» или «сериал»), жанра, года и слов «полностью» / «все серии» на выбранном языке.
 * [minDurationSec] отсеивает нарезки и трейлеры; [trailers] — вместо поиска чарт трейлеров.
 * [originLang] — язык страны подборки для озвучки «Авто».
 */
data class Category(
    val id: String,
    @StringRes val label: Int,
    val minDurationSec: Long,
    val trailers: Boolean = false,
    val originLang: QueryLang? = null,
    val adjective: Map<QueryLang, String> = emptyMap(),
    val noun: Map<QueryLang, String>? = null,
)

object QueryBuilder {
    private val movieNoun = terms("фильм", "film", "film", "")
    private val seriesNoun = terms("сериал", "dizi", "serial", "series")
    private val movieFull = terms("полностью", "tek parça", "tam", "full movie")
    private val seriesFull = terms("все серии", "tüm bölümler", "bütün seriyalar", "full episodes")

    /**
     * Язык запроса. Оригинал — язык страны подборки (турецкие — по-турецки), а у подборок
     * без страны — язык интерфейса. Дубляж — выбранный язык.
     */
    fun lang(category: Category, dub: DubLanguage, uiLang: QueryLang): QueryLang = when (dub) {
        DubLanguage.ORIGINAL -> category.originLang ?: uiLang
        DubLanguage.RU -> QueryLang.RU
        DubLanguage.TR -> QueryLang.TR
        DubLanguage.AZ -> QueryLang.AZ
        DubLanguage.EN -> QueryLang.EN
    }

    fun build(kind: CatalogKind, category: Category, dub: DubLanguage, genre: Genre, year: Int?, uiLang: QueryLang): String {
        val lang = lang(category, dub, uiLang)
        val defaultNoun = if (kind == CatalogKind.MOVIES) movieNoun else seriesNoun
        val full = if (kind == CatalogKind.MOVIES) movieFull else seriesFull
        return listOf(
            category.adjective[lang],
            (category.noun ?: defaultNoun)[lang],
            genre.terms[lang],
            full[lang],
            year?.toString(),
        ).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
    }
}

object Categories {
    private const val MOVIE = 40 * 60L
    private const val EPISODE = 20 * 60L

    private val new = terms("новый", "yeni", "yeni", "new")
    private val turkish = terms("турецкий", "Türk", "türk", "Turkish")
    private val russian = terms("русский", "Rus", "rus", "Russian")
    private val azerbaijani = terms("азербайджанский", "Azerbaycan", "Azərbaycan", "Azerbaijani")
    private val korean = terms("корейский", "Kore", "Koreya", "Korean")
    private val soviet = terms("советский", "Sovyet", "sovet", "Soviet")
    private val indian = terms("индийский", "Hint", "hind", "Indian")
    private val anime = terms("аниме", "anime", "anime", "anime")

    val movies = listOf(
        Category("trailers", R.string.cat_trailers, -1, trailers = true), // длительность у чарта может быть неизвестна
        Category("new", R.string.cat_movies_new, MOVIE, adjective = new),
        Category("russian", R.string.cat_russian, MOVIE, originLang = QueryLang.RU, adjective = russian),
        Category("soviet", R.string.cat_soviet, MOVIE, originLang = QueryLang.RU, adjective = soviet),
        Category("turkish", R.string.cat_turkish, MOVIE, originLang = QueryLang.TR, adjective = turkish),
        Category("azerbaijani", R.string.cat_azerbaijani, MOVIE, originLang = QueryLang.AZ, adjective = azerbaijani),
        Category("korean", R.string.cat_korean, MOVIE, adjective = korean),
        Category("indian", R.string.cat_indian, MOVIE, adjective = indian),
        Category("cartoons", R.string.cat_cartoons, 20 * 60L, noun = terms("мультфильм", "çizgi film", "cizgi film", "animated")),
        Category("anime", R.string.cat_anime, 20 * 60L, noun = anime),
        Category("documentary", R.string.cat_documentary, 30 * 60L, noun = terms("документальный фильм", "belgesel", "sənədli film", "documentary")),
    )

    val series = listOf(
        Category("new", R.string.cat_series_new, EPISODE, adjective = new),
        Category("turkish", R.string.cat_turkish, EPISODE, originLang = QueryLang.TR, adjective = turkish),
        Category("russian", R.string.cat_russian, EPISODE, originLang = QueryLang.RU, adjective = russian),
        Category("azerbaijani", R.string.cat_azerbaijani, EPISODE, originLang = QueryLang.AZ, adjective = azerbaijani),
        Category("korean", R.string.cat_korean_dramas, EPISODE, adjective = korean),
        Category("cartoons", R.string.cat_series_cartoons, 10 * 60L, noun = terms("мультсериал", "çizgi dizi", "cizgi serial", "animated series")),
        Category("anime", R.string.cat_anime, 10 * 60L, noun = anime),
        Category("documentary", R.string.cat_documentary, EPISODE, noun = terms("документальный сериал", "belgesel dizi", "sənədli serial", "documentary series")),
    )

    fun of(kind: CatalogKind): List<Category> = if (kind == CatalogKind.MOVIES) movies else series
}

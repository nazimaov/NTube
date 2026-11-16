package tv.lumen.feature.catalog

import androidx.annotation.StringRes

/** Раздел бокового меню: фильмы или сериалы. */
enum class CatalogKind { MOVIES, SERIES }

/**
 * Подраздел: чип сверху и поисковый запрос под ним. Запросы лежат в strings.xml и меняются
 * вместе с языком интерфейса. [minDurationSec] отсеивает нарезки и трейлеры — нужны только
 * полнометражные фильмы и серии. [trailers] — вместо поиска чарт трейлеров YouTube.
 */
data class Category(
    val id: String,
    @StringRes val label: Int,
    @StringRes val query: Int,
    val minDurationSec: Long,
    val trailers: Boolean = false,
)

object Categories {
    private const val MOVIE = 40 * 60L
    private const val SHORT_FILM = 20 * 60L
    private const val EPISODE = 20 * 60L

    val movies = listOf(
        Category("trailers", R.string.cat_trailers, R.string.q_trailers, 0, trailers = true),
        Category("new", R.string.cat_movies_new, R.string.q_movies_new, MOVIE),
        Category("action", R.string.cat_action, R.string.q_action, MOVIE),
        Category("comedy", R.string.cat_comedy, R.string.q_comedy, MOVIE),
        Category("drama", R.string.cat_drama, R.string.q_drama, MOVIE),
        Category("melodrama", R.string.cat_melodrama, R.string.q_melodrama, MOVIE),
        Category("horror", R.string.cat_horror, R.string.q_horror, MOVIE),
        Category("scifi", R.string.cat_scifi, R.string.q_scifi, MOVIE),
        Category("thriller", R.string.cat_thriller, R.string.q_thriller, MOVIE),
        Category("kids", R.string.cat_cartoons, R.string.q_cartoons, SHORT_FILM),
        Category("documentary", R.string.cat_documentary, R.string.q_documentary, 30 * 60L),
        Category("russian", R.string.cat_movies_russian, R.string.q_movies_russian, MOVIE),
        Category("soviet", R.string.cat_movies_soviet, R.string.q_movies_soviet, MOVIE),
        Category("turkish", R.string.cat_movies_turkish, R.string.q_movies_turkish, MOVIE),
        Category("azerbaijani", R.string.cat_movies_azerbaijani, R.string.q_movies_azerbaijani, MOVIE),
    )

    val series = listOf(
        Category("new", R.string.cat_series_new, R.string.q_series_new, EPISODE),
        Category("turkish", R.string.cat_series_turkish, R.string.q_series_turkish, EPISODE),
        Category("russian", R.string.cat_series_russian, R.string.q_series_russian, EPISODE),
        Category("azerbaijani", R.string.cat_series_azerbaijani, R.string.q_series_azerbaijani, EPISODE),
        Category("korean", R.string.cat_series_korean, R.string.q_series_korean, EPISODE),
        Category("detective", R.string.cat_series_detective, R.string.q_series_detective, EPISODE),
        Category("melodrama", R.string.cat_series_melodrama, R.string.q_series_melodrama, EPISODE),
        Category("comedy", R.string.cat_series_comedy, R.string.q_series_comedy, EPISODE),
        Category("historical", R.string.cat_series_historical, R.string.q_series_historical, EPISODE),
        Category("cartoons", R.string.cat_series_cartoons, R.string.q_series_cartoons, 10 * 60L),
        Category("anime", R.string.cat_series_anime, R.string.q_series_anime, 10 * 60L),
        Category("documentary", R.string.cat_series_documentary, R.string.q_series_documentary, EPISODE),
    )

    fun of(kind: CatalogKind): List<Category> = if (kind == CatalogKind.MOVIES) movies else series
}

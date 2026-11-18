package tv.lumen.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import tv.lumen.app.R
import tv.lumen.core.designsystem.component.LumenIcons

/** Пункты бокового меню в порядке из SPEC, раздел 8. */
enum class TopLevelDestination(val route: Any, val icon: ImageVector, @StringRes val label: Int) {
    SEARCH(Search, Icons.Filled.Search, R.string.menu_search),
    HOME(Home, Icons.Filled.Home, R.string.menu_home),
    MUSIC(Music, LumenIcons.MusicNote, R.string.menu_music),
    CINEMA(Cinema, LumenIcons.Movie, R.string.menu_cinema),
    EXPLORE(Explore, Icons.Filled.Star, R.string.menu_explore),
    KIDS(Kids, Icons.Filled.Face, R.string.menu_kids),
    LIBRARY(Library, Icons.Filled.Favorite, R.string.menu_library),
    SETTINGS(Settings, Icons.Filled.Settings, R.string.menu_settings),
}
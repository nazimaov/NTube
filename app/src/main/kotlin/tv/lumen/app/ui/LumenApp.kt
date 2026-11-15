package tv.lumen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import tv.lumen.app.navigation.History
import tv.lumen.app.navigation.Home
import tv.lumen.app.navigation.Search
import tv.lumen.app.navigation.Settings
import tv.lumen.app.navigation.Subscriptions
import tv.lumen.app.navigation.TopLevelDestination
import tv.lumen.feature.home.HomeRoute
import tv.lumen.feature.library.HistoryRoute
import tv.lumen.feature.library.SubscriptionsRoute
import tv.lumen.feature.search.SearchRoute
import tv.lumen.feature.settings.SettingsRoute

/** Корневой UI: боковое меню tv-material, свёрнутое до иконок, и NavHost с разделами. */
@Composable
fun LumenApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                TopLevelDestination.entries.forEach { item ->
                    NavigationDrawerItem(
                        selected = currentDestination.isOn(item),
                        onClick = { navController.navigateToTopLevel(item) },
                        leadingContent = { Icon(imageVector = item.icon, contentDescription = null) },
                    ) {
                        Text(text = stringResource(item.label))
                    }
                }
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Home) {
            composable<Home> { HomeRoute() }
            composable<Search> { SearchRoute() }
            composable<Subscriptions> { SubscriptionsRoute() }
            composable<History> { HistoryRoute() }
            composable<Settings> { SettingsRoute() }
        }
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.route::class) } == true

private fun NavHostController.navigateToTopLevel(item: TopLevelDestination) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
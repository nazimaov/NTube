package tv.lumen.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import tv.lumen.app.navigation.Channel
import tv.lumen.app.navigation.History
import tv.lumen.app.navigation.Home
import tv.lumen.app.navigation.Playlist
import tv.lumen.app.navigation.Search
import tv.lumen.app.navigation.Settings
import tv.lumen.app.navigation.Subscriptions
import tv.lumen.app.navigation.TopLevelDestination
import tv.lumen.app.navigation.Watch
import tv.lumen.feature.channel.ChannelRoute
import tv.lumen.feature.home.HomeRoute
import tv.lumen.feature.library.HistoryRoute
import tv.lumen.feature.library.SubscriptionsRoute
import tv.lumen.feature.playlist.PlaylistRoute
import tv.lumen.feature.search.SearchRoute
import tv.lumen.feature.settings.SettingsRoute
import tv.lumen.feature.watch.WatchRoute

/** Корневой UI: боковое меню tv-material, свёрнутое до иконок, и NavHost с разделами. */
@Composable
fun LumenApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val activity = LocalActivity.current

    // На экране плеера бокового меню нет: видео на весь экран
    val isWatching = currentDestination?.hasRoute(Watch::class) == true
    val isTopLevel = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    var drawerHasFocus by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val drawerFocus = remember { TopLevelDestination.entries.associateWith { FocusRequester() } }

    fun openVideo(id: String, playlistId: String? = null) = navController.navigate(Watch(videoId = id, playlistId = playlistId))
    fun openChannel(id: String) = navController.navigate(Channel(id))
    fun openPlaylist(id: String, title: String) = navController.navigate(Playlist(id, title))

    /**
     * Back обрабатывается здесь, а не BackHandler-ом: на ТВ Compose иначе снимает фокус с элемента
     * и событие теряется. Плеер обрабатывает Back сам.
     * Корневой экран: первое нажатие — фокус в меню, второе — диалог выхода (SPEC 8).
     */
    fun onBack(): Boolean {
        when {
            !isTopLevel -> navController.popBackStack()
            drawerHasFocus -> showExitDialog = true
            else -> TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
                ?.let { runCatching { drawerFocus.getValue(it).requestFocus() } }
        }
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back || isWatching || showExitDialog) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onBack()
                true
            },
    ) {
        NavigationDrawer(
            drawerContent = {
                if (isWatching) return@NavigationDrawer
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(12.dp)
                        .selectableGroup()
                        .onFocusChanged { drawerHasFocus = it.hasFocus },
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    TopLevelDestination.entries.forEach { item ->
                        NavigationDrawerItem(
                            selected = currentDestination.isOn(item),
                            onClick = { navController.navigateToTopLevel(item) },
                            leadingContent = { Icon(imageVector = item.icon, contentDescription = null) },
                            modifier = Modifier.focusRequester(drawerFocus.getValue(item)),
                        ) {
                            Text(text = stringResource(item.label))
                        }
                    }
                }
            },
        ) {
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> { HomeRoute(onOpenVideo = { openVideo(it) }) }
                composable<Search> {
                    SearchRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel, onOpenPlaylist = ::openPlaylist)
                }
                composable<Subscriptions> { SubscriptionsRoute() }
                composable<History> { HistoryRoute() }
                composable<Settings> { SettingsRoute() }
                composable<Channel> {
                    ChannelRoute(onOpenVideo = { openVideo(it) }, onOpenPlaylist = ::openPlaylist)
                }
                composable<Playlist> {
                    PlaylistRoute(onOpenVideo = { videoId, playlistId -> openVideo(videoId, playlistId) })
                }
                composable<Watch> {
                    WatchRoute(
                        onBack = { navController.popBackStack() },
                        // Похожее видео заменяет текущее, чтобы Back вёл туда, откуда пришли
                        onOpenVideo = { id ->
                            navController.navigate(Watch(videoId = id)) {
                                popUpTo<Watch> { inclusive = true }
                            }
                        },
                        onOpenChannel = ::openChannel,
                    )
                }
            }
        }

        if (showExitDialog) {
            ExitDialog(onConfirm = { activity?.finish() }, onDismiss = { showExitDialog = false })
        }
    }

    // При возврате с экрана без меню (плеер) фокус не должен остаться «нигде»
    LaunchedEffect(isWatching) { if (!isWatching) drawerHasFocus = false }
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

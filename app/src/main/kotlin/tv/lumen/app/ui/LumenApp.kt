package tv.lumen.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import tv.lumen.core.designsystem.component.LocalChannelAvatarResolver
import tv.lumen.core.designsystem.component.LocalQueueActions
import tv.lumen.core.designsystem.component.LocalVideoDecorator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import tv.lumen.core.designsystem.theme.LumenColors
import androidx.tv.material3.Text
import tv.lumen.app.navigation.Channel
import tv.lumen.app.navigation.Home
import tv.lumen.app.navigation.Cinema
import tv.lumen.app.navigation.Explore
import tv.lumen.app.navigation.Kids
import tv.lumen.app.navigation.Library
import tv.lumen.app.navigation.Music
import tv.lumen.app.navigation.Playlist
import tv.lumen.app.navigation.Search
import tv.lumen.app.navigation.Settings
import tv.lumen.app.navigation.SettingsSection
import androidx.navigation.toRoute
import tv.lumen.feature.settings.SettingsPage
import tv.lumen.feature.settings.SettingsPageRoute
import tv.lumen.app.navigation.TopLevelDestination
import tv.lumen.app.navigation.Watch
import tv.lumen.feature.channel.ChannelRoute
import tv.lumen.feature.home.HomeRoute
import tv.lumen.feature.library.LibraryRoute
import tv.lumen.feature.playlist.PlaylistRoute
import tv.lumen.feature.catalog.CinemaRoute
import tv.lumen.feature.catalog.ExploreRoute
import tv.lumen.feature.catalog.KidsRoute
import tv.lumen.feature.search.MusicRoute
import tv.lumen.feature.search.SearchRoute
import tv.lumen.feature.settings.SettingsRoute
import tv.lumen.feature.watch.WatchRoute

/** Корневой UI: боковое меню tv-material, свёрнутое до иконок, и NavHost с разделами. */
@Composable
fun LumenApp(
    pendingVideoId: String? = null,
    onPendingVideoHandled: () -> Unit = {},
    appViewModel: AppViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val menu by appViewModel.menu.collectAsStateWithLifecycle()
    val updateState by appViewModel.updateState.collectAsStateWithLifecycle()
    val updatePending by appViewModel.updatePending.collectAsStateWithLifecycle()
    val decorator by appViewModel.decorator.collectAsStateWithLifecycle()
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

    fun openVideo(id: String, playlistId: String? = null, audioOnly: Boolean = false) =
        navController.navigate(Watch(videoId = id, playlistId = playlistId, audioOnly = audioOnly))
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
            // Второе Back вне «Главной» ведёт на «Главную», а уже на ней — к диалогу выхода
            drawerHasFocus -> if (currentDestination.isOn(TopLevelDestination.HOME)) showExitDialog = true
                else navController.navigateToTopLevel(TopLevelDestination.HOME)
            else -> TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
                ?.let { runCatching { drawerFocus.getValue(it).requestFocus() } }
        }
        return true
    }

    CompositionLocalProvider(
        LocalVideoDecorator provides decorator,
        LocalChannelAvatarResolver provides appViewModel.channelAvatars,
        LocalQueueActions provides appViewModel.queueActions,
        tv.lumen.core.designsystem.component.LocalMenuExtras provides appViewModel.menuExtras,
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back || isWatching || showExitDialog) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onBack()
                true
            },
    ) {
        // Экран начинается правее узкой панели меню (80 dp) и не двигается, когда меню раскрывается
        Box(Modifier.fillMaxSize().padding(start = if (isWatching) 0.dp else ClosedDrawerWidth)) {
            // Без анимации переходов: стандартное затухание на ТВ накладывает экраны друг на друга и мерцает
            NavHost(
                navController = navController,
                startDestination = Home,
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None },
            ) {
                composable<Home> {
                    HomeRoute(
                        onOpenVideo = { openVideo(it) },
                        onOpenChannel = ::openChannel,
                        onOpenSearch = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) },
                    )
                }
                composable<Search> {
                    SearchRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel, onOpenPlaylist = ::openPlaylist)
                }
                composable<Music> {
                    MusicRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel, onOpenPlaylist = ::openPlaylist)
                }
                composable<Cinema> { CinemaRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel) }
                composable<Explore> { ExploreRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel) }
                composable<Kids> { KidsRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel) }
                composable<Library> { LibraryRoute(onOpenVideo = { openVideo(it) }, onOpenChannel = ::openChannel) }
                composable<Settings> {
                    SettingsRoute(
                        versionName = tv.lumen.app.BuildConfig.VERSION_NAME,
                        onOpenPage = { navController.navigate(SettingsSection(it.name)) },
                    )
                }
                composable<SettingsSection> { entry ->
                    SettingsPageRoute(
                        page = SettingsPage.valueOf(entry.toRoute<SettingsSection>().page),
                        versionName = tv.lumen.app.BuildConfig.VERSION_NAME,
                        appName = stringResource(tv.lumen.app.R.string.app_name),
                    )
                }
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
                        onOpenVideo = { id, playlistId, audioOnly ->
                            navController.navigate(Watch(videoId = id, playlistId = playlistId, audioOnly = audioOnly)) {
                                popUpTo<Watch> { inclusive = true }
                            }
                        },
                        onOpenChannel = ::openChannel,
                    )
                }
            }
        }

        // Своя лёгкая панель меню вместо NavigationDrawer из tv-material (как боковые заголовки у SmartTube):
        // раскрывается сразу, без анимации ширины, поверх экрана; фокус при входе сразу на текущем разделе
        if (!isWatching) {
            val current = menu.firstOrNull { currentDestination.isOn(it) }
                ?: if (currentDestination?.hasRoute(SettingsSection::class) == true) TopLevelDestination.SETTINGS else null
            SideMenu(
                menu = menu,
                current = current,
                updatePending = updatePending,
                focusRequesters = drawerFocus,
                onFocusChanged = { drawerHasFocus = it },
                onOpen = { navController.navigateToTopLevel(it) },
            )
        }

        UpdateDialog(state = updateState, onUpdate = appViewModel::installUpdate, onDismiss = appViewModel::dismissUpdate)

        if (showExitDialog) {
            ExitDialog(onConfirm = { activity?.finish() }, onDismiss = { showExitDialog = false })
        }
    }
    }

    // Смена языка интерфейса — сохранить и пересоздать экран (SharedPreferences читает attachBaseContext)
    LaunchedEffect(Unit) {
        appViewModel.appLanguage.collect { tag ->
            val current = activity ?: return@collect
            if (tag != tv.lumen.app.AppLocale.saved(current)) {
                tv.lumen.app.AppLocale.save(current, tag)
                current.recreate()
            }
        }
    }

    // Внешняя ссылка youtube.com/watch или youtu.be открывает плеер
    LaunchedEffect(pendingVideoId) {
        pendingVideoId?.let {
            openVideo(it)
            onPendingVideoHandled()
        }
    }

    // При возврате с экрана без меню (плеер) фокус не должен остаться «нигде»
    LaunchedEffect(isWatching) { if (!isWatching) drawerHasFocus = false }
}

private fun NavDestination?.isOn(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.route::class) } == true

private fun NavHostController.navigateToTopLevel(item: TopLevelDestination) {
    // Нажатие на пункт, где мы уже находимся (или внутри его подэкрана), возвращает его в начальное состояние
    val here = currentDestination.isOn(item)
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = !here
            inclusive = here && item == TopLevelDestination.HOME
        }
        launchSingleTop = !here
        restoreState = !here
    }
}

/** Ширина узкой панели меню в tv-material (ClosedDrawerWidth). */
private val ClosedDrawerWidth = 80.dp

private val OpenMenuWidth = 280.dp

/**
 * Боковое меню: узкая полоска с иконками; когда в неё приходит фокус — раскрывается до 280 dp поверх экрана
 * (сплошной фон, справа затемнение). Ширина меняется сразу, без анимации: на слабом ТВ анимация ширины
 * перерисовывала всё меню на каждом кадре. Фокус при входе — сразу на текущем разделе ([current]).
 */
@Composable
private fun SideMenu(
    menu: List<TopLevelDestination>,
    current: TopLevelDestination?,
    updatePending: Boolean,
    focusRequesters: Map<TopLevelDestination, FocusRequester>,
    onFocusChanged: (Boolean) -> Unit,
    onOpen: (TopLevelDestination) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (expanded) OpenMenuWidth else ClosedDrawerWidth)
            .background(if (expanded) LumenColors.Background else Color.Transparent)
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .focusProperties {
                onEnter = { current?.let { runCatching { focusRequesters.getValue(it).requestFocus() } } }
            }
            .focusGroup(),
    ) {
        // Логотип: в свёрнутом меню — эмблема, в раскрытом — полностью
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(
                if (expanded) tv.lumen.app.R.drawable.logo_full else tv.lumen.app.R.drawable.logo_emblem,
            ),
            contentDescription = stringResource(tv.lumen.app.R.string.app_name),
            modifier = Modifier.padding(start = 16.dp, top = 24.dp).height(if (expanded) 52.dp else 40.dp),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            menu.forEach { item ->
                MenuEntry(
                    item = item,
                    selected = item == current,
                    expanded = expanded,
                    showDot = item == TopLevelDestination.SETTINGS && updatePending,
                    modifier = Modifier.focusRequester(focusRequesters.getValue(item)),
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun MenuEntry(
    item: TopLevelDestination,
    selected: Boolean,
    expanded: Boolean,
    showDot: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) LumenColors.SurfaceFocused else Color.Transparent,
            contentColor = LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.SurfaceFocused,
            pressedContentColor = LumenColors.TextPrimary,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box {
                Icon(imageVector = item.icon, contentDescription = stringResource(item.label), modifier = Modifier.size(24.dp))
                if (showDot) tv.lumen.core.designsystem.component.RedDot(Modifier.align(Alignment.TopEnd))
            }
            if (expanded) {
                Text(text = stringResource(item.label), style = androidx.tv.material3.MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

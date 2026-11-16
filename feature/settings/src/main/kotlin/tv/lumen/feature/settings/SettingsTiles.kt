package tv.lumen.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.lumen.core.datastore.Settings
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.designsystem.theme.LumenDimens
import java.util.Locale

/** Разделы настроек — плитки главного экрана настроек (как в SmartTube). */
enum class SettingsPage(@StringRes val title: Int, @StringRes val description: Int, val icon: ImageVector) {
    PROFILES(R.string.settings_section_profiles, R.string.settings_profile_hint, Icons.Filled.AccountCircle),
    ACCOUNT(R.string.settings_section_account, R.string.settings_account_desc, Icons.Filled.Person),
    PLAYER(R.string.settings_section_player, R.string.settings_desc_player, Icons.Filled.PlayArrow),
    LANGUAGE(R.string.settings_section_language, R.string.settings_desc_language, Icons.Filled.Place),
    FEED(R.string.settings_section_feed, R.string.settings_desc_feed, Icons.AutoMirrored.Filled.List),
    SPONSORBLOCK(R.string.settings_section_sponsorblock, R.string.settings_desc_sponsorblock, Icons.Filled.CheckCircle),
    MODS(R.string.settings_section_mods, R.string.settings_desc_mods, Icons.Filled.Build),
    MENU(R.string.settings_section_menu, R.string.settings_desc_menu, Icons.Filled.Menu),
    GENERAL(R.string.settings_section_general, R.string.settings_desc_general, Icons.Filled.Settings),
    ABOUT(R.string.settings_section_about, R.string.settings_desc_about, Icons.Filled.Info),
}

private val TileShape = RoundedCornerShape(16.dp)

/** Главный экран настроек: плитки разделов с кратким текущим значением. */
@Composable
fun SettingsRoute(
    versionName: String,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    FocusMemoryScope(viewModel.focusMemory)

    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)
    val system = stringResource(R.string.settings_system)
    val summaries = mapOf(
        SettingsPage.PROFILES to (profile?.name?.ifBlank { null } ?: stringResource(R.string.settings_profile_default)),
        SettingsPage.ACCOUNT to stringResource(
            if (account == tv.lumen.data.account.AccountState.SignedIn) R.string.settings_account_signed_in else R.string.settings_account_signed_out,
        ),
        SettingsPage.PLAYER to listOf(
            if (s.maxResolution == 0) stringResource(R.string.settings_res_screen) else "${s.maxResolution}p",
            speedLabel(s.defaultSpeed),
        ).joinToString(" · "),
        SettingsPage.LANGUAGE to listOf(
            s.appLanguage?.let(::nativeName) ?: system,
            s.contentCountry?.let { Locale("", it).getDisplayCountry(Locale.getDefault()) } ?: system,
        ).joinToString(" · "),
        SettingsPage.FEED to stringResource(if (s.hideShorts) R.string.settings_summary_shorts_hidden else R.string.settings_summary_shorts_shown),
        SettingsPage.SPONSORBLOCK to if (s.sponsorBlockEnabled) on else off,
        SettingsPage.MODS to modsSummary(s, on, off),
        SettingsPage.MENU to stringResource(R.string.settings_summary_menu, s.menuItems.size - s.hiddenMenuItems.size),
        SettingsPage.GENERAL to stringResource(if (s.historyEnabled) R.string.settings_summary_history_on else R.string.settings_summary_history_off),
        SettingsPage.ABOUT to stringResource(R.string.settings_version, versionName),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = LumenDimens.ContentStart, end = LumenDimens.SafeHorizontal, top = LumenDimens.SafeVertical, bottom = LumenDimens.SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxSize().focusRestorer(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        }
        items(SettingsPage.entries, key = { it.name }) { page ->
            SettingsTile(
                page = page,
                summary = summaries[page].orEmpty(),
                onClick = { onOpenPage(page) },
                modifier = Modifier.rememberFocus(viewModel.focusMemory, page.name, isFirst = page == SettingsPage.entries.first()),
            )
        }
    }
}

@Composable
private fun SettingsTile(page: SettingsPage, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LumenColors.Surface,
            contentColor = LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.SurfaceFocused,
            pressedContentColor = LumenColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, LumenColors.FocusRing), shape = TileShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.aspectRatio(16f / 9f),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(page.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(page.title), style = MaterialTheme.typography.titleMedium, color = Color.Unspecified, maxLines = 1)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

private fun modsSummary(s: Settings, on: String, off: String): String {
    val enabled = listOf(s.deArrowTitles || s.deArrowThumbnails, s.rydEnabled).count { it }
    return if (enabled == 0) off else "$on: $enabled"
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"

private fun nativeName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

package tv.lumen.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import tv.lumen.core.datastore.DubLanguage
import tv.lumen.core.datastore.MenuItem
import tv.lumen.core.datastore.PreferredCodec
import tv.lumen.core.designsystem.focus.FocusMemoryScope
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenDimens
import tv.lumen.core.designsystem.theme.LumenColors
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import tv.lumen.core.model.SegmentMode
import tv.lumen.core.model.SponsorCategory
import java.util.Locale

private data class Choice<T>(val label: String, val value: T)

private class ChoiceRequest(val title: String, val options: List<Choice<*>>, val selected: Any?, val onSelect: (Any?) -> Unit)

/** Экран одного раздела настроек; плитки разделов — [SettingsRoute]. */
@Composable
fun SettingsPageRoute(
    page: SettingsPage,
    versionName: String,
    appName: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedChannels.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<ChoiceRequest?>(null) }
    var addingWord by remember { mutableStateOf(false) }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    // Ввод имени профиля: null — диалог закрыт; id = null — новый профиль
    var naming by remember { mutableStateOf<ProfileNaming?>(null) }
    FocusMemoryScope(viewModel.focusMemory)
    val memory = viewModel.focusMemory

    val screen = stringResource(R.string.settings_res_screen)
    val qualities = listOf(Choice(screen, 0)) + listOf(720, 1080, 1440, 2160).map { Choice("${it}p", it) }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { Choice(formatSpeed(it), it) }
    val codecs = listOf(
        Choice(stringResource(R.string.settings_auto), PreferredCodec.AUTO),
        Choice("AV1", PreferredCodec.AV1),
        Choice("VP9", PreferredCodec.VP9),
        Choice("H.264 (AVC)", PreferredCodec.AVC),
    )
    val off = stringResource(R.string.settings_off)
    val defaultName = stringResource(R.string.settings_profile_default)
    val selectLabel = stringResource(R.string.settings_profile_select)
    val renameLabel = stringResource(R.string.settings_profile_rename)
    val deleteLabel = stringResource(R.string.settings_profile_delete)
    val currentLabel = stringResource(R.string.settings_profile_current)
    val original = stringResource(R.string.settings_original)
    val system = stringResource(R.string.settings_system)
    val subtitleLangs = listOf(Choice<String?>(off, null)) + LANGUAGES.map { Choice<String?>(languageName(it), it) }
    val audioLangs = listOf(Choice<String?>(original, null)) + LANGUAGES.map { Choice<String?>(languageName(it), it) }
    val contentLangs = listOf(Choice<String?>(system, null)) + LANGUAGES.map { Choice<String?>(languageName(it), it) }
    val countries = listOf(Choice<String?>(system, null)) + COUNTRIES.map { Choice<String?>(countryName(it), it) }
    // Языки, на которые переведён интерфейс; название — на самом языке
    val appLangs = listOf(Choice<String?>(system, null)) + UI_LANGUAGES.map { Choice<String?>(nativeLanguageName(it), it) }
    // Язык фильмов и сериалов: оригинал или дубляж на выбранный язык
    val dubs = listOf(
        Choice(stringResource(R.string.settings_dub_original), DubLanguage.ORIGINAL),
        Choice(stringResource(R.string.settings_dub_ru), DubLanguage.RU),
        Choice(stringResource(R.string.settings_dub_tr), DubLanguage.TR),
        Choice(stringResource(R.string.settings_dub_az), DubLanguage.AZ),
        Choice(stringResource(R.string.settings_dub_en), DubLanguage.EN),
    )
    val modes = listOf(
        Choice(stringResource(R.string.settings_sb_skip), SegmentMode.SKIP),
        Choice(stringResource(R.string.settings_sb_button), SegmentMode.BUTTON),
        Choice(stringResource(R.string.settings_sb_off), SegmentMode.OFF),
    )

    fun <T> ask(title: String, options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        choice = ChoiceRequest(title, options, selected) { onSelect(it as T) }
    }

    // Слева — раздел: иконка, название, пояснение; справа — пункты-карточки
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxSize().padding(start = LumenDimens.ContentStart),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(
            modifier = Modifier.weight(0.34f).padding(top = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.tv.material3.Icon(page.icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = LumenColors.TextPrimary)
            Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(page.description), style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextSecondary)
        }
    LazyColumn(
        modifier = Modifier.weight(0.66f).fillMaxHeight(),
        contentPadding = PaddingValues(top = 64.dp, bottom = 64.dp, end = LumenDimens.SafeHorizontal),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        if (page == SettingsPage.PROFILES) {
        profiles.forEach { profile ->
            item(key = "profile:${profile.id}") {
                val name = profile.name.ifBlank { defaultName }
                Row(
                    name,
                    currentLabel.takeIf { profile.id == currentProfile?.id },
                    Modifier.rememberFocus(memory, "profile:${profile.id}", isFirst = profile == profiles.first()),
                ) {
                    // Действия с профилем: выбрать, переименовать, удалить (последний профиль не удаляется)
                    val actions = buildList {
                        if (profile.id != currentProfile?.id) add(Choice(selectLabel, 0))
                        add(Choice(renameLabel, 1))
                        if (profiles.size > 1) add(Choice(deleteLabel, 2))
                    }
                    ask(name, actions, -1) { action ->
                        when (action) {
                            0 -> viewModel.selectProfile(profile.id)
                            1 -> naming = ProfileNaming(profile.id, profile.name)
                            2 -> {
                                viewModel.deleteProfile(profile.id)
                                memory.restore(toKey = "profile:add")
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(stringResource(R.string.settings_profile_add), null, Modifier.rememberFocus(memory, "profile:add")) {
                naming = ProfileNaming(null, "")
            }
        }

        }
        if (page == SettingsPage.ACCOUNT) {
            item {
                AccountSection(
                    state = account,
                    onSignIn = viewModel::signIn,
                    onCancel = viewModel::cancelSignIn,
                    onSignOut = viewModel::signOut,
                    focusMemory = memory,
                )
            }
        }

        if (page == SettingsPage.PLAYER) {
        item {
            val title = stringResource(R.string.settings_max_quality)
            Row(title, qualities.label(s.maxResolution), Modifier.rememberFocus(memory, "quality", isFirst = true)) {
                ask(title, qualities, s.maxResolution) { v -> viewModel.update { it.copy(maxResolution = v) } }
            }
        }
        item {
            val title = stringResource(R.string.settings_speed)
            Row(title, formatSpeed(s.defaultSpeed), Modifier.rememberFocus(memory, "speed")) {
                ask(title, speeds, s.defaultSpeed) { v -> viewModel.update { it.copy(defaultSpeed = v) } }
            }
        }
        item {
            val title = stringResource(R.string.settings_codec)
            Row(title, codecs.label(s.preferredCodec), Modifier.rememberFocus(memory, "codec")) {
                ask(title, codecs, s.preferredCodec) { v -> viewModel.update { it.copy(preferredCodec = v) } }
            }
        }
        item {
            Toggle(stringResource(R.string.settings_autoplay), s.autoplayNext, Modifier.rememberFocus(memory, "autoplay")) {
                viewModel.update { it.copy(autoplayNext = !it.autoplayNext) }
            }
        }
        item {
            val title = stringResource(R.string.settings_subtitles)
            Row(title, subtitleLangs.label(s.subtitlesLang), Modifier.rememberFocus(memory, "subs")) {
                ask(title, subtitleLangs, s.subtitlesLang) { v -> viewModel.update { it.copy(subtitlesLang = v) } }
            }
        }
        item {
            val title = stringResource(R.string.settings_audio)
            Row(title, audioLangs.label(s.audioLang), Modifier.rememberFocus(memory, "audio")) {
                ask(title, audioLangs, s.audioLang) { v -> viewModel.update { it.copy(audioLang = v) } }
            }
        }

        item {
            Toggle(stringResource(R.string.settings_match_fps), s.matchFrameRate, Modifier.rememberFocus(memory, "fps")) {
                viewModel.update { it.copy(matchFrameRate = !it.matchFrameRate) }
            }
        }

        }
        if (page == SettingsPage.SPONSORBLOCK) {
        item {
            Toggle(stringResource(R.string.settings_sb_enabled), s.sponsorBlockEnabled, Modifier.rememberFocus(memory, "sb", isFirst = true)) {
                viewModel.update { it.copy(sponsorBlockEnabled = !it.sponsorBlockEnabled) }
            }
        }
        if (s.sponsorBlockEnabled) {
            items(SponsorCategory.entries, key = { "sb:${it.name}" }) { category ->
                val title = categoryLabel(category)
                Row(title, modes.label(s.modeOf(category)), Modifier.rememberFocus(memory, "sb:${category.name}")) {
                    ask(title, modes, s.modeOf(category)) { v ->
                        viewModel.update { it.copy(sponsorCategories = it.sponsorCategories + (category to v)) }
                    }
                }
            }
        }

        }
        if (page == SettingsPage.MODS) {
        item {
            Toggle(stringResource(R.string.settings_dearrow_titles), s.deArrowTitles, Modifier.rememberFocus(memory, "daTitles", isFirst = true)) {
                viewModel.update { it.copy(deArrowTitles = !it.deArrowTitles) }
            }
        }
        item {
            Toggle(stringResource(R.string.settings_dearrow_thumbs), s.deArrowThumbnails, Modifier.rememberFocus(memory, "daThumbs")) {
                viewModel.update { it.copy(deArrowThumbnails = !it.deArrowThumbnails) }
            }
        }
        item {
            Toggle(stringResource(R.string.settings_ryd), s.rydEnabled, Modifier.rememberFocus(memory, "ryd")) {
                viewModel.update { it.copy(rydEnabled = !it.rydEnabled) }
            }
        }

        }
        if (page == SettingsPage.FEED) {
        item {
            Toggle(stringResource(R.string.settings_hide_shorts), s.hideShorts, Modifier.rememberFocus(memory, "shorts", isFirst = true)) {
                viewModel.update { it.copy(hideShorts = !it.hideShorts) }
            }
        }
        item {
            Toggle(stringResource(R.string.settings_hide_watched), s.hideWatched, Modifier.rememberFocus(memory, "watched")) {
                viewModel.update { it.copy(hideWatched = !it.hideWatched) }
            }
        }
        item {
            Row(stringResource(R.string.settings_add_word), null, Modifier.rememberFocus(memory, "addword")) { addingWord = true }
        }
        items(s.blockedWords.sorted(), key = { "w:$it" }) { word ->
            Row("«$word»", stringResource(R.string.settings_remove), Modifier.rememberFocus(memory, "w:$word")) { viewModel.removeBlockedWord(word) }
        }
        if (blocked.isNotEmpty()) {
            item { Text(stringResource(R.string.settings_blocked_channels), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            items(blocked, key = { "bc:${it.id}" }) { channel ->
                Row(channel.name.ifBlank { channel.id }, stringResource(R.string.settings_unblock), Modifier.rememberFocus(memory, "bc:${channel.id}")) {
                    viewModel.unblockChannel(channel.id)
                }
            }
        }

        }
        if (page == SettingsPage.LANGUAGE) {
        item {
            val title = stringResource(R.string.settings_app_language)
            Row(title, appLangs.label(s.appLanguage), Modifier.rememberFocus(memory, "app_lang", isFirst = true)) {
                ask(title, appLangs, s.appLanguage) { v -> viewModel.update { it.copy(appLanguage = v) } }
            }
        }
        item {
            val title = stringResource(R.string.settings_region)
            Row(title, countries.label(s.contentCountry), Modifier.rememberFocus(memory, "country")) {
                ask(title, countries, s.contentCountry) { v -> viewModel.update { it.copy(contentCountry = v) } }
            }
        }
        item {
            Text(
                stringResource(R.string.settings_region_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).fillMaxWidth(0.8f),
            )
        }
        item {
            Toggle(stringResource(R.string.settings_by_region), s.contentByRegion, Modifier.rememberFocus(memory, "by_region")) {
                viewModel.update { it.copy(contentByRegion = !it.contentByRegion) }
            }
        }
        item {
            Text(
                stringResource(R.string.settings_by_region_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).fillMaxWidth(0.8f),
            )
        }
        item {
            val title = stringResource(R.string.settings_content_language)
            Row(title, contentLangs.label(s.contentLanguage), Modifier.rememberFocus(memory, "lang")) {
                ask(title, contentLangs, s.contentLanguage) { v -> viewModel.update { it.copy(contentLanguage = v) } }
            }
        }
        item {
            val title = stringResource(R.string.settings_catalog_dub)
            Row(title, dubs.label(s.catalogDub), Modifier.rememberFocus(memory, "catalog_dub")) {
                ask(title, dubs, s.catalogDub) { v -> viewModel.update { it.copy(catalogDub = v) } }
            }
        }
        item {
            Text(
                stringResource(R.string.settings_catalog_dub_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).fillMaxWidth(0.8f),
            )
        }

        }
        if (page == SettingsPage.GENERAL) {
        item {
            Toggle(stringResource(R.string.settings_history_enabled), s.historyEnabled, Modifier.rememberFocus(memory, "history", isFirst = true)) {
                viewModel.update { it.copy(historyEnabled = !it.historyEnabled) }
            }
        }
        item {
            val title = stringResource(R.string.settings_clear_history)
            val yes = stringResource(R.string.settings_clear_confirm)
            val no = stringResource(R.string.settings_cancel)
            Row(title, null, Modifier.rememberFocus(memory, "clear")) {
                ask(title, listOf(Choice(no, false), Choice(yes, true)), false) { confirmed -> if (confirmed) viewModel.clearHistory() }
            }
        }

        item {
            Row(stringResource(R.string.settings_backup), null, Modifier.rememberFocus(memory, "backup")) { viewModel.openBackup() }
        }

        }
        if (page == SettingsPage.MENU) {
        items(s.menuItems, key = { "menu:${it.name}" }) { item ->
            val title = menuLabel(item)
            val shown = item !in s.hiddenMenuItems
            val show = stringResource(R.string.settings_menu_show)
            val hide = stringResource(R.string.settings_menu_hide)
            val up = stringResource(R.string.settings_menu_up)
            Row(title, if (shown) show else hide, Modifier.rememberFocus(memory, "menu:${item.name}", isFirst = item == s.menuItems.first())) {
                ask(title, listOf(Choice(if (shown) hide else show, 0), Choice(up, 1)), -1) { action ->
                    if (action == 0) viewModel.toggleMenuItem(item) else viewModel.moveMenuItemUp(item)
                }
            }
        }

        }
        if (page == SettingsPage.ABOUT) {
        item {
            val pending by viewModel.pendingUpdate.collectAsStateWithLifecycle()
            val found = pending
            if (found != null) {
                // Найдено обновление: пункт выделен красной точкой, по нажатию открывается окно «Обновить»
                SettingCard(
                    title = stringResource(R.string.settings_update_available, found.version),
                    value = stringResource(R.string.settings_update_install),
                    onClick = { viewModel.checkForUpdates() },
                    modifier = Modifier.rememberFocus(memory, "update", isFirst = true),
                    trailing = { tv.lumen.core.designsystem.component.RedDot(size = 14.dp) },
                )
            } else {
                Row(stringResource(R.string.settings_check_updates), null, Modifier.rememberFocus(memory, "update", isFirst = true)) { viewModel.checkForUpdates() }
            }
        }
        item {
            Toggle(stringResource(R.string.settings_update_on_start), s.updateCheckOnStart, Modifier.rememberFocus(memory, "updateOnStart")) {
                viewModel.update { it.copy(updateCheckOnStart = !it.updateCheckOnStart) }
            }
        }
        item { Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyLarge) }
        item {
            Text(
                stringResource(R.string.settings_licenses, appName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
        }
    }

    }
    backup?.let { BackupDialog(it, onClose = viewModel::closeBackup) }

    choice?.let { req ->
        ChoiceDialog(req) { choice = null; memory.restore() }
    }
    if (addingWord) {
        WordDialog(onDone = { word -> word?.let(viewModel::addBlockedWord); addingWord = false; memory.restore() })
    }
    naming?.let { n ->
        WordDialog(
            title = stringResource(R.string.settings_profile_name),
            initial = n.name,
            confirm = stringResource(R.string.settings_profile_save),
            onDone = { text ->
                naming = null
                memory.restore()
                val name = text?.trim().orEmpty()
                if (text != null) {
                    if (n.id == null) viewModel.createProfile(name) else viewModel.renameProfile(n.id, name)
                }
            },
        )
    }
}

private fun LazyListScope.section(title: Int) {
    item { Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) }
}

@Composable
private fun Row(title: String, value: String?, modifier: Modifier = Modifier, onClick: () -> Unit) =
    SettingCard(title = title, value = value, onClick = onClick, modifier = modifier)

@Composable
private fun Toggle(title: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) =
    SettingCard(title = title, value = null, onClick = onToggle, modifier = modifier) {
        Switch(checked = checked, onCheckedChange = null)
    }

private val SettingShape = RoundedCornerShape(14.dp)

/** Пункт настроек — карточка: название, текущее значение под ним, справа переключатель или стрелка. */
@Composable
internal fun SettingCard(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(SettingShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = LumenColors.Surface,
            contentColor = LumenColors.TextPrimary,
            focusedContainerColor = LumenColors.TextPrimary,
            focusedContentColor = LumenColors.Background,
            pressedContainerColor = LumenColors.SurfaceFocused,
            pressedContentColor = LumenColors.TextPrimary,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.Unspecified)
                if (value != null) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.Unspecified,
                        modifier = Modifier.alpha(0.7f),
                    )
                }
            }
            if (trailing != null) trailing() else Text("›", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.alpha(0.5f))
        }
    }
}

@Composable
private fun ChoiceDialog(req: ChoiceRequest, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(req) { runCatching { focus.requestFocus() } }
    val selectedIndex = req.options.indexOfFirst { it.value == req.selected }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(480.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(req.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                items(req.options.size) { i ->
                    val option = req.options[i]
                    ListItem(
                        selected = option.value == req.selected,
                        onClick = {
                            onDismiss()
                            req.onSelect(option.value)
                        },
                        headlineContent = { Text(option.label) },
                        modifier = if (i == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordDialog(
    onDone: (String?) -> Unit,
    title: String = stringResource(R.string.settings_add_word),
    initial: String = "",
    confirm: String = stringResource(R.string.settings_add),
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = { onDone(null) }) {
        Column(
            modifier = Modifier.width(560.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(12.dp),
            )
            Button(onClick = { onDone(text) }) { Text(confirm) }
        }
    }
}

private fun <T> List<Choice<T>>.label(value: T): String = firstOrNull { it.value == value }?.label ?: value.toString()

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.1f" else "%.2f").format(speed).trimEnd('0').trimEnd('.', ',') + "×"

private val LANGUAGES = listOf(
    "ru", "en", "az", "tr", "uk", "kk", "uz", "be", "ky", "hy", "ka", "de", "fr", "es", "it", "pt", "pl", "nl",
    "ar", "fa", "he", "hi", "ja", "ko", "zh",
)

// Языки, на которые переведён интерфейс (есть values-XX/strings.xml)
private val UI_LANGUAGES = listOf("ru", "en")

private val COUNTRIES = listOf(
    "RU", "AZ", "TR", "KZ", "UA", "BY", "UZ", "KG", "TJ", "AM", "GE", "MD", "US", "GB", "DE", "FR", "ES", "IT",
    "PT", "PL", "NL", "JP", "KR", "IN", "BR", "AE", "SA", "EG", "IL", "IR",
)

private fun languageName(tag: String): String =
    Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }

private fun countryName(code: String): String = Locale("", code).getDisplayCountry(Locale.getDefault())

private fun nativeLanguageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
private fun categoryLabel(c: SponsorCategory): String = stringResource(
    when (c) {
        SponsorCategory.SPONSOR -> R.string.settings_sb_sponsor
        SponsorCategory.SELF_PROMO -> R.string.settings_sb_selfpromo
        SponsorCategory.INTERACTION -> R.string.settings_sb_interaction
        SponsorCategory.INTRO -> R.string.settings_sb_intro
        SponsorCategory.OUTRO -> R.string.settings_sb_outro
        SponsorCategory.PREVIEW -> R.string.settings_sb_preview
        SponsorCategory.MUSIC_OFFTOPIC -> R.string.settings_sb_music
        SponsorCategory.FILLER -> R.string.settings_sb_filler
    },
)

@Composable
private fun menuLabel(item: MenuItem): String = stringResource(
    when (item) {
        MenuItem.SEARCH -> R.string.settings_menu_search
        MenuItem.HOME -> R.string.settings_menu_home
        MenuItem.MUSIC -> R.string.settings_menu_music
        MenuItem.CINEMA -> R.string.settings_menu_cinema
        MenuItem.EXPLORE -> R.string.settings_menu_explore
        MenuItem.KIDS -> R.string.settings_menu_kids
        MenuItem.LIBRARY -> R.string.settings_menu_library
    },
)
/** Ввод имени профиля: [id] = null — создание нового. */
private data class ProfileNaming(val id: Long?, val name: String)

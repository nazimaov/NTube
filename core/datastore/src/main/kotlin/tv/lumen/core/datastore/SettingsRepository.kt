package tv.lumen.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.lumen.core.common.ApplicationScope
import tv.lumen.core.model.SegmentMode
import tv.lumen.core.model.SponsorCategory
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Единственный источник настроек (SPEC 9.2): каждая настройка — Flow, изменение — [set].
 * Экраны и плеер читают настройки только через этот класс.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = context.settingsStore

    val settings: Flow<Settings> = store.data.map(::read).distinctUntilChanged()

    /** Текущее значение для мест, где нужен снимок без ожидания (создание плеера). */
    val current: StateFlow<Settings> = settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    fun <T> flow(select: (Settings) -> T): Flow<T> = settings.map(select).distinctUntilChanged()

    suspend fun set(update: (Settings) -> Settings) {
        store.edit { prefs -> write(prefs, update(read(prefs))) }
    }

    private fun read(p: Preferences) = Settings(
        maxResolution = p[MAX_RES] ?: 0,
        defaultSpeed = p[SPEED] ?: 1f,
        preferredCodec = p[CODEC]?.let { runCatching { PreferredCodec.valueOf(it) }.getOrNull() } ?: PreferredCodec.AUTO,
        autoplayNext = p[AUTOPLAY] ?: true,
        subtitlesLang = p[SUBS],
        audioLang = p[AUDIO],
        matchFrameRate = p[MATCH_FPS] ?: false,
        sponsorBlockEnabled = p[SB_ENABLED] ?: true,
        sponsorCategories = SponsorCategory.entries.associateWith { c ->
            p[sbKey(c)]?.let { runCatching { SegmentMode.valueOf(it) }.getOrNull() } ?: Settings.DEFAULT_SPONSOR_MODES.getValue(c)
        },
        deArrowTitles = p[DEARROW_TITLES] ?: false,
        deArrowThumbnails = p[DEARROW_THUMBS] ?: false,
        rydEnabled = p[RYD] ?: false,
        hideShorts = p[HIDE_SHORTS] ?: true,
        hideWatched = p[HIDE_WATCHED] ?: false,
        blockedWords = p[BLOCKED_WORDS] ?: emptySet(),
        contentCountry = p[COUNTRY],
        contentLanguage = p[LANGUAGE],
        historyEnabled = p[HISTORY] ?: true,
        menuItems = p[MENU_ORDER]?.split(',')?.mapNotNull { runCatching { MenuItem.valueOf(it) }.getOrNull() }
            ?.let { saved -> saved + MenuItem.entries.filter { it !in saved } } ?: MenuItem.entries,
        hiddenMenuItems = p[MENU_HIDDEN]?.mapNotNull { runCatching { MenuItem.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet(),
        updateCheckOnStart = p[UPDATE_CHECK] ?: true,
        currentProfileId = p[PROFILE] ?: 0L,
    )

    private fun write(p: MutablePreferences, s: Settings) {
        p[MAX_RES] = s.maxResolution
        p[SPEED] = s.defaultSpeed
        p[CODEC] = s.preferredCodec.name
        p[AUTOPLAY] = s.autoplayNext
        if (s.subtitlesLang != null) p[SUBS] = s.subtitlesLang else p.remove(SUBS)
        if (s.audioLang != null) p[AUDIO] = s.audioLang else p.remove(AUDIO)
        p[MATCH_FPS] = s.matchFrameRate
        p[SB_ENABLED] = s.sponsorBlockEnabled
        s.sponsorCategories.forEach { (c, mode) -> p[sbKey(c)] = mode.name }
        p[DEARROW_TITLES] = s.deArrowTitles
        p[DEARROW_THUMBS] = s.deArrowThumbnails
        p[RYD] = s.rydEnabled
        p[HIDE_SHORTS] = s.hideShorts
        p[HIDE_WATCHED] = s.hideWatched
        p[BLOCKED_WORDS] = s.blockedWords
        if (s.contentCountry != null) p[COUNTRY] = s.contentCountry else p.remove(COUNTRY)
        if (s.contentLanguage != null) p[LANGUAGE] = s.contentLanguage else p.remove(LANGUAGE)
        p[HISTORY] = s.historyEnabled
        p[MENU_ORDER] = s.menuItems.joinToString(",") { it.name }
        p[MENU_HIDDEN] = s.hiddenMenuItems.mapTo(HashSet()) { it.name }
        p[UPDATE_CHECK] = s.updateCheckOnStart
        p[PROFILE] = s.currentProfileId
    }

    private companion object {
        // Имена ключей — как в SPEC 9.2
        val MAX_RES = intPreferencesKey("player.max_resolution")
        val SPEED = floatPreferencesKey("player.default_speed")
        val CODEC = stringPreferencesKey("player.preferred_codec")
        val AUTOPLAY = booleanPreferencesKey("player.autoplay_next")
        val SUBS = stringPreferencesKey("player.subtitles_lang")
        val AUDIO = stringPreferencesKey("player.audio_lang")
        val MATCH_FPS = booleanPreferencesKey("player.match_frame_rate")
        val SB_ENABLED = booleanPreferencesKey("sb.enabled")
        fun sbKey(c: SponsorCategory) = stringPreferencesKey("sb.category.${c.apiName}")
        val DEARROW_TITLES = booleanPreferencesKey("dearrow.titles")
        val DEARROW_THUMBS = booleanPreferencesKey("dearrow.thumbnails")
        val RYD = booleanPreferencesKey("ryd.enabled")
        val HIDE_SHORTS = booleanPreferencesKey("feed.hide_shorts")
        val HIDE_WATCHED = booleanPreferencesKey("feed.hide_watched")
        val BLOCKED_WORDS = stringSetPreferencesKey("feed.blocked_words")
        val COUNTRY = stringPreferencesKey("general.content_country")
        val LANGUAGE = stringPreferencesKey("general.content_language")
        val HISTORY = booleanPreferencesKey("general.history_enabled")
        val MENU_ORDER = stringPreferencesKey("general.menu_order")
        val MENU_HIDDEN = stringSetPreferencesKey("general.menu_hidden")
        val UPDATE_CHECK = booleanPreferencesKey("update.check_on_start")
        val PROFILE = longPreferencesKey("profile.current")
    }
}
package tv.lumen.app.navigation

import kotlinx.serialization.Serializable

// Типобезопасные маршруты (SPEC, раздел 8). Имена полей читают ViewModel экранов из SavedStateHandle.
@Serializable data object Home
@Serializable data object Search
@Serializable data object Music
@Serializable data object Cinema
@Serializable data object Explore
@Serializable data object Kids
@Serializable data object Library
@Serializable data object Settings

/** Раздел настроек (плитка); [page] — имя SettingsPage. */
@Serializable data class SettingsSection(val page: String)

/** Имена полей читает WatchViewModel из SavedStateHandle — не переименовывать без него. */
@Serializable data class Watch(val videoId: String, val playlistId: String? = null, val startMs: Long = 0)

@Serializable data class Channel(val channelId: String)

@Serializable data class Playlist(val playlistId: String, val title: String)
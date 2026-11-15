package tv.lumen.app.navigation

import kotlinx.serialization.Serializable

// Типобезопасные маршруты (SPEC, раздел 8). Channel и Playlist добавляются в фазе 3.
@Serializable data object Home
@Serializable data object Search
@Serializable data object Subscriptions
@Serializable data object History
@Serializable data object Settings

/** Имена полей читает WatchViewModel из SavedStateHandle — не переименовывать без него. */
@Serializable data class Watch(val videoId: String, val playlistId: String? = null, val startMs: Long = 0)
package tv.lumen.app.navigation

import kotlinx.serialization.Serializable

// Типобезопасные маршруты (SPEC, раздел 8). Channel, Playlist и Watch добавляются в фазах 2–3.
@Serializable data object Home
@Serializable data object Search
@Serializable data object Subscriptions
@Serializable data object History
@Serializable data object Settings
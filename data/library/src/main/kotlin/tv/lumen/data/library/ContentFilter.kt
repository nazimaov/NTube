package tv.lumen.data.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.core.datastore.UserLists
import tv.lumen.core.model.SearchItem
import tv.lumen.core.model.Video
import javax.inject.Inject

/** Правила фильтрации лент (SPEC 6.4). Чистая логика — покрыта модульными тестами. */
data class FilterRules(
    val hideShorts: Boolean = false,
    val hideWatched: Boolean = false,
    val blockedChannels: Set<String> = emptySet(),
    val blockedWords: Set<String> = emptySet(),
    /** Видео, скрытые кнопкой «Не интересно». */
    val hiddenVideos: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap(),
) {
    fun allows(video: Video): Boolean {
        if (hideShorts && (video.isShort || video.durationSec in 1..SHORT_MAX_SEC)) return false
        if (video.id in hiddenVideos) return false
        if (hideWatched && (progress[video.id] ?: 0f) >= WATCHED_FRACTION) return false
        if (video.channelId != null && video.channelId in blockedChannels) return false
        if (blockedWords.isNotEmpty()) {
            val title = video.title.lowercase()
            if (blockedWords.any { it.isNotBlank() && title.contains(it.lowercase()) }) return false
        }
        return true
    }

    fun allows(item: SearchItem): Boolean = when (item) {
        is SearchItem.VideoItem -> allows(item.video)
        is SearchItem.ChannelItem -> item.channel.id !in blockedChannels
        is SearchItem.PlaylistItem -> true
    }

    fun videos(list: List<Video>) = list.filter(::allows)

    companion object {
        const val SHORT_MAX_SEC = 60L
        const val WATCHED_FRACTION = 0.9f
    }
}

/**
 * Фильтр-декоратор над списками (SPEC 6.4): новый мод — ключ в настройках + правило здесь,
 * код экранов не меняется.
 */
class ContentFilter @Inject constructor(
    settings: SettingsRepository,
    library: LibraryRepository,
    userLists: UserLists,
) {
    val rules: Flow<FilterRules> = combine(settings.settings, library.blockedChannels, library.progress, userLists.hiddenVideos) { s, blocked, progress, hidden ->
        FilterRules(
            hideShorts = s.hideShorts,
            hideWatched = s.hideWatched,
            blockedChannels = blocked,
            blockedWords = s.blockedWords,
            hiddenVideos = hidden,
            progress = progress,
        )
    }
}
package tv.lumen.data.library.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tv.lumen.core.datastore.MenuItem
import tv.lumen.core.datastore.PreferredCodec
import tv.lumen.core.datastore.Settings
import tv.lumen.core.model.SegmentMode
import tv.lumen.core.model.SponsorCategory

/** Резервная копия: подписки, история и настройки в одном JSON (SPEC 7.1). */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val subscriptions: List<SubscriptionBackup> = emptyList(),
    val history: List<HistoryBackup> = emptyList(),
    val settings: SettingsBackup? = null,
) {
    companion object {
        const val FORMAT = "lumen-backup"
    }
}

@Serializable
data class SubscriptionBackup(val channelId: String, val name: String, val avatarUrl: String? = null, val subscribedAt: Long = 0, val playbackSpeed: Float? = null)

@Serializable
data class HistoryBackup(
    val videoId: String,
    val title: String,
    val channelId: String? = null,
    val channelName: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long,
    val positionMs: Long,
    val watchedAt: Long,
)

@Serializable
data class SettingsBackup(
    val maxResolution: Int = 0,
    val defaultSpeed: Float = 1f,
    val preferredCodec: String = "AUTO",
    val autoplayNext: Boolean = true,
    val subtitlesLang: String? = null,
    val audioLang: String? = null,
    val matchFrameRate: Boolean = false,
    val sponsorBlockEnabled: Boolean = true,
    val sponsorCategories: Map<String, String> = emptyMap(),
    val deArrowTitles: Boolean = false,
    val deArrowThumbnails: Boolean = false,
    val rydEnabled: Boolean = false,
    val hideShorts: Boolean = true,
    val hideWatched: Boolean = false,
    val blockedWords: List<String> = emptyList(),
    val contentCountry: String? = null,
    val contentLanguage: String? = null,
    val historyEnabled: Boolean = true,
    val menuItems: List<String> = emptyList(),
    val hiddenMenuItems: List<String> = emptyList(),
    val updateCheckOnStart: Boolean = true,
)

object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** null — не наш формат. */
    fun decode(text: String): Backup? =
        runCatching { json.decodeFromString(Backup.serializer(), text) }.getOrNull()?.takeIf { it.format == Backup.FORMAT }

    /**
     * subscriptions.csv из Google Takeout: «Channel Id,Channel Url,Channel Title» (заголовок может быть
     * на другом языке). Берутся строки, где первая колонка — ID вида UC…
     */
    fun parseTakeoutCsv(text: String): List<SubscriptionBackup> =
        text.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val cols = splitCsv(line)
                val id = cols.getOrNull(0)?.trim().orEmpty()
                if (!id.startsWith("UC") || id.length != 24) return@mapNotNull null
                SubscriptionBackup(channelId = id, name = cols.getOrNull(2)?.trim().orEmpty().ifEmpty { id })
            }
            .distinctBy { it.channelId }
            .toList()

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    fun toBackup(s: Settings) = SettingsBackup(
        maxResolution = s.maxResolution,
        defaultSpeed = s.defaultSpeed,
        preferredCodec = s.preferredCodec.name,
        autoplayNext = s.autoplayNext,
        subtitlesLang = s.subtitlesLang,
        audioLang = s.audioLang,
        matchFrameRate = s.matchFrameRate,
        sponsorBlockEnabled = s.sponsorBlockEnabled,
        sponsorCategories = s.sponsorCategories.mapKeys { it.key.apiName }.mapValues { it.value.name },
        deArrowTitles = s.deArrowTitles,
        deArrowThumbnails = s.deArrowThumbnails,
        rydEnabled = s.rydEnabled,
        hideShorts = s.hideShorts,
        hideWatched = s.hideWatched,
        blockedWords = s.blockedWords.sorted(),
        contentCountry = s.contentCountry,
        contentLanguage = s.contentLanguage,
        historyEnabled = s.historyEnabled,
        menuItems = s.menuItems.map { it.name },
        hiddenMenuItems = s.hiddenMenuItems.map { it.name },
        updateCheckOnStart = s.updateCheckOnStart,
    )

    fun toSettings(b: SettingsBackup): Settings = Settings(
        maxResolution = b.maxResolution,
        defaultSpeed = b.defaultSpeed,
        preferredCodec = runCatching { PreferredCodec.valueOf(b.preferredCodec) }.getOrDefault(PreferredCodec.AUTO),
        autoplayNext = b.autoplayNext,
        subtitlesLang = b.subtitlesLang,
        audioLang = b.audioLang,
        matchFrameRate = b.matchFrameRate,
        sponsorBlockEnabled = b.sponsorBlockEnabled,
        sponsorCategories = Settings.DEFAULT_SPONSOR_MODES + b.sponsorCategories.mapNotNull { (k, v) ->
            val c = SponsorCategory.fromApi(k) ?: return@mapNotNull null
            val m = runCatching { SegmentMode.valueOf(v) }.getOrNull() ?: return@mapNotNull null
            c to m
        },
        deArrowTitles = b.deArrowTitles,
        deArrowThumbnails = b.deArrowThumbnails,
        rydEnabled = b.rydEnabled,
        hideShorts = b.hideShorts,
        hideWatched = b.hideWatched,
        blockedWords = b.blockedWords.toSet(),
        contentCountry = b.contentCountry,
        contentLanguage = b.contentLanguage,
        historyEnabled = b.historyEnabled,
        menuItems = b.menuItems.mapNotNull { runCatching { MenuItem.valueOf(it) }.getOrNull() }
            .let { saved -> saved + MenuItem.entries.filter { it !in saved } },
        hiddenMenuItems = b.hiddenMenuItems.mapNotNull { runCatching { MenuItem.valueOf(it) }.getOrNull() }.toSet(),
        updateCheckOnStart = b.updateCheckOnStart,
    )
}
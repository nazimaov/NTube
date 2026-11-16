package tv.lumen.data.library.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.database.SubscriptionDao
import tv.lumen.core.database.SubscriptionEntity
import tv.lumen.core.database.WatchHistoryDao
import tv.lumen.core.database.WatchHistoryEntity
import tv.lumen.core.datastore.SettingsRepository
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val subscriptions: Int, val history: Int, val settings: Boolean)

/** Импорт подписок из Takeout и резервная копия всего (SPEC 7.1). */
@Singleton
class BackupService @Inject internal constructor(
    private val subscriptions: SubscriptionDao,
    private val history: WatchHistoryDao,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Копия текущего профиля: его подписки и история плюс общие настройки. */
    suspend fun export(): String = withContext(io) {
        val profileId = profiles.currentId()
        BackupCodec.encode(
            Backup(
                subscriptions = subscriptions.getAll(profileId).map {
                    SubscriptionBackup(it.channelId, it.name, it.avatarUrl, it.subscribedAt, it.playbackSpeed)
                },
                history = history.getAll(profileId).map {
                    HistoryBackup(it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationMs, it.positionMs, it.watchedAt)
                },
                settings = BackupCodec.toBackup(settings.current.value),
            ),
        )
    }

    /** Принимает резервную копию Lumen (JSON) или subscriptions.csv из Takeout. null — формат не распознан. */
    suspend fun import(text: String): ImportResult? = withContext(io) {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val backup = if (trimmed.startsWith("{")) BackupCodec.decode(trimmed) else null
        val subs = backup?.subscriptions ?: BackupCodec.parseTakeoutCsv(trimmed).takeIf { it.isNotEmpty() } ?: return@withContext null
        val now = System.currentTimeMillis()
        val profileId = profiles.currentId() // импорт — в текущий профиль
        subs.forEach {
            subscriptions.upsert(SubscriptionEntity(profileId, it.channelId, it.name, it.avatarUrl, it.subscribedAt.takeIf { t -> t > 0 } ?: now, it.playbackSpeed))
        }
        backup?.history?.forEach {
            history.upsert(WatchHistoryEntity(profileId, it.videoId, it.title, it.channelId, it.channelName, it.thumbnailUrl, it.durationMs, it.positionMs, it.watchedAt))
        }
        backup?.settings?.let { s -> settings.set { BackupCodec.toSettings(s) } }
        // Лента новых подписок — сразу, не дожидаясь 30 минут
        library.refreshFeed(force = true)
        ImportResult(subs.size, backup?.history?.size ?: 0, backup?.settings != null)
    }
}
package tv.lumen.app.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import tv.lumen.core.common.ApplicationScope
import tv.lumen.core.common.IoDispatcher
import tv.lumen.core.designsystem.component.ChannelAvatarResolver
import tv.lumen.core.model.AppResult
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.youtube.YouTubeRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Недостающие аватары каналов для карточек: сначала из подписок, затем со страницы канала.
 * Найденное запоминается на устройстве, поэтому запрос к каналу делается один раз.
 */
@Singleton
class ChannelAvatars @Inject constructor(
    @ApplicationContext context: Context,
    private val youtube: YouTubeRepository,
    private val library: LibraryRepository,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ChannelAvatarResolver {

    private val prefs by lazy { context.getSharedPreferences("channel_avatars", Context.MODE_PRIVATE) }
    private val pending = ConcurrentHashMap<String, Deferred<String?>>()
    private val limiter = Semaphore(MAX_PARALLEL)

    override suspend fun avatarUrl(channelId: String): String? = withContext(io) {
        prefs.getString(channelId, null)?.let { return@withContext it }
        library.subscriptions.first().firstOrNull { it.id == channelId }?.avatarUrl?.let { return@withContext it }
        // Один запрос на канал, даже если на экране много его карточек
        pending.getOrPut(channelId) {
            scope.async(io) {
                limiter.withPermit {
                    (youtube.channel(channelId) as? AppResult.Success)?.value?.channel?.avatarUrl
                }?.also { prefs.edit().putString(channelId, it).apply() }
            }
        }.await()
    }

    private companion object {
        const val MAX_PARALLEL = 3
    }
}

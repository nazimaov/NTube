package tv.lumen.data.library

import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.Video

/**
 * Откуда библиотека берёт данные каналов. data:* зависит только от core:* (SPEC 3), поэтому
 * реализацию поверх YouTubeRepository предоставляет модуль app.
 */
interface ChannelSource {
    /** Канонические данные канала (ID вида UC…, имя, аватар). */
    suspend fun channel(channelId: ChannelId): AppResult<ChannelSummary>

    /** Лёгкий RSS-фид канала (SPEC 7.1). */
    suspend fun channelFeed(channelId: ChannelId): AppResult<List<Video>>
}
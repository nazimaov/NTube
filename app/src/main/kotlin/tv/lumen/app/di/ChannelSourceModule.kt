package tv.lumen.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.core.model.AppResult
import tv.lumen.core.model.ChannelId
import tv.lumen.core.model.ChannelSummary
import tv.lumen.core.model.Video
import tv.lumen.core.model.map
import tv.lumen.data.library.ChannelSource
import tv.lumen.data.youtube.YouTubeRepository
import javax.inject.Inject

/** Связка data:library ↔ data:youtube живёт в app: data-модули друг от друга не зависят (SPEC 3). */
class YouTubeChannelSource @Inject constructor(
    private val youtube: YouTubeRepository,
) : ChannelSource {
    override suspend fun channel(channelId: ChannelId): AppResult<ChannelSummary> =
        youtube.channel(channelId).map { it.channel }

    override suspend fun channelFeed(channelId: ChannelId): AppResult<List<Video>> =
        youtube.channelFeed(channelId)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChannelSourceModule {
    @Binds
    abstract fun channelSource(impl: YouTubeChannelSource): ChannelSource
}
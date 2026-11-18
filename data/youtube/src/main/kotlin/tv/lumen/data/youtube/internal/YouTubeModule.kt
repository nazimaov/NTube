package tv.lumen.data.youtube.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.data.youtube.YouTubeRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class YouTubeModule {
    @Binds
    abstract fun repository(impl: NewPipeYouTubeRepository): YouTubeRepository

    /** Реализацию даёт app (через аккаунт); если её нет — запасного пути для видео «для детей» нет. */
    @dagger.BindsOptionalOf
    abstract fun authorizedPlayer(): tv.lumen.data.youtube.AuthorizedPlayer
}

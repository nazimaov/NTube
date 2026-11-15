package tv.lumen.data.dearrow.internal

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.data.dearrow.DeArrowRepository
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DeArrowModule {
    @Binds
    abstract fun repository(impl: DeArrowClient): DeArrowRepository

    companion object {
        @Provides @Named(DeArrowClient.API_URL) fun apiUrl(): String = "https://sponsor.ajay.app"
        @Provides @Named(DeArrowClient.THUMB_URL) fun thumbUrl(): String = "https://dearrow-thumb.ajay.app"
    }
}
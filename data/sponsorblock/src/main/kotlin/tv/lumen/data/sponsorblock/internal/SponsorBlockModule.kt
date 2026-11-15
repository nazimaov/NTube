package tv.lumen.data.sponsorblock.internal

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.data.sponsorblock.SponsorBlockRepository
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SponsorBlockModule {
    @Binds
    abstract fun repository(impl: SponsorBlockClient): SponsorBlockRepository

    companion object {
        @Provides
        @Named(SponsorBlockClient.BASE_URL_NAME)
        fun baseUrl(): String = SponsorBlockClient.DEFAULT_BASE_URL
    }
}
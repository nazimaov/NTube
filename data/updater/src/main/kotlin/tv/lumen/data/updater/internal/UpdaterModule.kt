package tv.lumen.data.updater.internal

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.data.updater.UpdateManager
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdaterModule {
    @Binds
    abstract fun manager(impl: GitHubUpdateManager): UpdateManager

    companion object {
        @Provides
        @Named(GitHubUpdateManager.API_URL_NAME)
        fun apiUrl(): String = GitHubUpdateManager.DEFAULT_API_URL
    }
}
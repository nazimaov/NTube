package tv.lumen.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.app.BuildConfig
import tv.lumen.data.updater.UpdateConfig

/** Репозиторий релизов и текущая версия — из gradle.properties через BuildConfig (SPEC 10.5, п. 5). */
@Module
@InstallIn(SingletonComponent::class)
object UpdateConfigModule {
    @Provides
    fun updateConfig() = UpdateConfig(
        repoOwner = BuildConfig.UPDATE_REPO_OWNER,
        repoName = BuildConfig.UPDATE_REPO_NAME,
        currentVersion = BuildConfig.VERSION_NAME,
    )
}
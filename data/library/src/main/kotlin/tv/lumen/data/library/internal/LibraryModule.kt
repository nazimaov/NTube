package tv.lumen.data.library.internal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tv.lumen.data.library.LibraryRepository
import tv.lumen.data.library.ProfileRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LibraryModule {
    @Binds
    abstract fun repository(impl: RoomLibraryRepository): LibraryRepository

    @Binds
    abstract fun profiles(impl: RoomProfileRepository): ProfileRepository
}
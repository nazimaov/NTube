package tv.lumen.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        SubscriptionEntity::class,
        FeedItemEntity::class,
        WatchHistoryEntity::class,
        BlockedChannelEntity::class,
        BrandingCacheEntity::class,
        ProfileEntity::class,
        VideoReactionEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 1 → 2: новые таблицы profile и video_reaction
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun subscriptions(): SubscriptionDao
    abstract fun feed(): FeedDao
    abstract fun history(): WatchHistoryDao
    abstract fun blockedChannels(): BlockedChannelDao
    abstract fun branding(): BrandingCacheDao
    abstract fun profiles(): ProfileDao
    abstract fun reactions(): VideoReactionDao

    companion object {
        const val NAME = "lumen.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /** fallbackToDestructiveMigration запрещён (SPEC 9.1): новая версия — только с миграцией. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LumenDatabase =
        Room.databaseBuilder(context, LumenDatabase::class.java, LumenDatabase.NAME).build()

    @Provides fun subscriptionDao(db: LumenDatabase) = db.subscriptions()
    @Provides fun feedDao(db: LumenDatabase) = db.feed()
    @Provides fun historyDao(db: LumenDatabase) = db.history()
    @Provides fun blockedDao(db: LumenDatabase) = db.blockedChannels()
    @Provides fun brandingDao(db: LumenDatabase) = db.branding()
    @Provides fun profileDao(db: LumenDatabase) = db.profiles()
    @Provides fun reactionDao(db: LumenDatabase) = db.reactions()
}
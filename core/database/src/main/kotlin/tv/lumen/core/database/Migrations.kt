package tv.lumen.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 2 → 3: подписки и история становятся своими у каждого профиля.
 * Всё, что было до обновления, переходит в первый профиль (при необходимости он создаётся).
 * Кэш ленты остаётся общим и теряет внешний ключ на subscription (у неё теперь составной ключ).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO profile (name, created_at) SELECT '', ${System.currentTimeMillis()} " +
                "WHERE NOT EXISTS (SELECT 1 FROM profile)",
        )
        val owner = "(SELECT MIN(id) FROM profile)"

        // Лента — первой: старая таблица ссылается на subscription
        db.execSQL(
            "CREATE TABLE feed_item_new (`video_id` TEXT NOT NULL, `channel_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`thumbnail_url` TEXT, `duration_s` INTEGER NOT NULL, `published_at` INTEGER NOT NULL, " +
                "`is_live` INTEGER NOT NULL, `is_short` INTEGER NOT NULL, PRIMARY KEY(`video_id`))",
        )
        db.execSQL("INSERT INTO feed_item_new SELECT video_id, channel_id, title, thumbnail_url, duration_s, published_at, is_live, is_short FROM feed_item")
        db.execSQL("DROP TABLE feed_item")
        db.execSQL("ALTER TABLE feed_item_new RENAME TO feed_item")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_item_published_at` ON `feed_item` (`published_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_item_channel_id` ON `feed_item` (`channel_id`)")

        db.execSQL(
            "CREATE TABLE subscription_new (`profile_id` INTEGER NOT NULL, `channel_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`avatar_url` TEXT, `subscribed_at` INTEGER NOT NULL, `playback_speed` REAL, " +
                "PRIMARY KEY(`profile_id`, `channel_id`), " +
                "FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO subscription_new SELECT $owner, channel_id, name, avatar_url, subscribed_at, playback_speed FROM subscription",
        )
        db.execSQL("DROP TABLE subscription")
        db.execSQL("ALTER TABLE subscription_new RENAME TO subscription")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscription_channel_id` ON `subscription` (`channel_id`)")

        db.execSQL(
            "CREATE TABLE watch_history_new (`profile_id` INTEGER NOT NULL, `video_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`channel_id` TEXT, `channel_name` TEXT, `thumbnail_url` TEXT, `duration_ms` INTEGER NOT NULL, " +
                "`position_ms` INTEGER NOT NULL, `watched_at` INTEGER NOT NULL, PRIMARY KEY(`profile_id`, `video_id`), " +
                "FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO watch_history_new SELECT $owner, video_id, title, channel_id, channel_name, thumbnail_url, " +
                "duration_ms, position_ms, watched_at FROM watch_history",
        )
        db.execSQL("DROP TABLE watch_history")
        db.execSQL("ALTER TABLE watch_history_new RENAME TO watch_history")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_profile_id_watched_at` ON `watch_history` (`profile_id`, `watched_at`)")
    }
}

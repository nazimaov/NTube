package tv.lumen.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Обновление схемы без потери данных (SPEC 9.1: destructive-миграции запрещены). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LumenDatabase::class.java)

    @Test
    fun миграция_1_2_сохраняет_подписки_и_добавляет_профили() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO subscription (channel_id, name, avatar_url, subscribed_at, playback_speed) VALUES ('UC1', 'Ch', NULL, 1, NULL)")
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT name FROM subscription").use { c ->
                c.moveToFirst()
                assertEquals("Ch", c.getString(0))
            }
            db.execSQL("INSERT INTO profile (name, created_at) VALUES ('P', 1)")
            db.execSQL("INSERT INTO video_reaction (profile_id, video_id, reaction, updated_at) VALUES (1, 'v', 1, 1)")
        }
    }

    @Test
    fun миграция_2_3_переносит_подписки_и_историю_в_профиль_по_умолчанию() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO subscription (channel_id, name, avatar_url, subscribed_at, playback_speed) VALUES ('UC1', 'Ch', NULL, 1, 1.5)")
            db.execSQL("INSERT INTO feed_item VALUES ('v1', 'UC1', 'T', NULL, 60, 10, 0, 0)")
            db.execSQL("INSERT INTO watch_history VALUES ('v1', 'T', 'UC1', 'Ch', NULL, 100, 50, 5)")
        }
        helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3).use { db ->
            val profileId = db.query("SELECT MIN(id) FROM profile").use { c -> c.moveToFirst(); c.getLong(0) }
            db.query("SELECT profile_id, playback_speed FROM subscription WHERE channel_id = 'UC1'").use { c ->
                c.moveToFirst()
                assertEquals(profileId, c.getLong(0))
                assertEquals(1.5, c.getDouble(1), 0.0)
            }
            db.query("SELECT profile_id, position_ms FROM watch_history WHERE video_id = 'v1'").use { c ->
                c.moveToFirst()
                assertEquals(profileId, c.getLong(0))
                assertEquals(50L, c.getLong(1))
            }
            db.query("SELECT COUNT(*) FROM feed_item").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}

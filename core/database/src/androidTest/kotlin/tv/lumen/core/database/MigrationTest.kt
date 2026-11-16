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

    private companion object {
        const val DB = "migration-test"
    }
}

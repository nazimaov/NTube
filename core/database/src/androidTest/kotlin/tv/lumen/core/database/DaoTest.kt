package tv.lumen.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: LumenDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LumenDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun sub(id: String) = SubscriptionEntity(id, "Channel $id", null, 1, null)
    private fun item(id: String, ch: String, at: Long) = FeedItemEntity(id, ch, "Video $id", null, 60, at, false, false)
    private fun watched(id: String, pos: Long, dur: Long, at: Long) = WatchHistoryEntity(id, "V $id", "UC1", "Ch", null, dur, pos, at)

    @Test
    fun подписка_и_лента_удаляются_каскадом() = runTest {
        db.subscriptions().upsert(sub("UC1"))
        db.subscriptions().upsert(sub("UC2"))
        db.feed().replaceForChannel("UC1", listOf(item("a", "UC1", 10), item("b", "UC1", 30)))
        db.feed().replaceForChannel("UC2", listOf(item("c", "UC2", 20)))
        assertEquals(listOf("b", "c", "a"), db.feed().observeLatest(10).first().map { it.videoId })
        assertTrue(db.subscriptions().observeIsSubscribed("UC1").first())

        db.subscriptions().delete("UC1")
        assertFalse(db.subscriptions().observeIsSubscribed("UC1").first())
        assertEquals(listOf("c"), db.feed().observeLatest(10).first().map { it.videoId })
    }

    @Test
    fun лента_канала_заменяется_целиком() = runTest {
        db.subscriptions().upsert(sub("UC1"))
        db.feed().replaceForChannel("UC1", listOf(item("a", "UC1", 10), item("b", "UC1", 20)))
        db.feed().replaceForChannel("UC1", listOf(item("c", "UC1", 30)))
        assertEquals(listOf("c"), db.feed().observeLatest(10).first().map { it.videoId })
    }

    @Test
    fun продолжить_просмотр_только_5_95_процентов() = runTest {
        val h = db.history()
        h.upsert(watched("start", 1_000, 100_000, 1))
        h.upsert(watched("middle", 50_000, 100_000, 2))
        h.upsert(watched("edge", 5_000, 100_000, 3))
        h.upsert(watched("done", 99_000, 100_000, 4))
        assertEquals(listOf("edge", "middle"), h.observeInProgress(10).first().map { it.videoId })
    }

    @Test
    fun история_обрезается_до_лимита() = runTest {
        val h = db.history()
        (1..10).forEach { h.upsert(watched("v$it", 0, 100, it.toLong())) }
        h.trim(3)
        assertEquals(listOf("v10", "v9", "v8"), h.observeRecent(100).first().map { it.videoId })
    }
}
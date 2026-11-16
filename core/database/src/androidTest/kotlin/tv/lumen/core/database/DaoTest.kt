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

    // Профиль P создаётся в каждом тесте: подписки и история принадлежат профилю
    private var P = 0L

    private suspend fun profile(name: String) = db.profiles().insert(ProfileEntity(name = name, createdAt = 1))

    private fun sub(id: String, p: Long = P) = SubscriptionEntity(p, id, "Channel $id", null, 1, null)
    private fun item(id: String, ch: String, at: Long) = FeedItemEntity(id, ch, "Video $id", null, 60, at, false, false)
    private fun watched(id: String, pos: Long, dur: Long, at: Long, p: Long = P) = WatchHistoryEntity(p, id, "V $id", "UC1", "Ch", null, dur, pos, at)

    @Test
    fun лента_по_подпискам_профиля_и_очистка_кэша() = runTest {
        P = profile("A")
        db.subscriptions().upsert(sub("UC1"))
        db.subscriptions().upsert(sub("UC2"))
        db.feed().replaceForChannel("UC1", listOf(item("a", "UC1", 10), item("b", "UC1", 30)))
        db.feed().replaceForChannel("UC2", listOf(item("c", "UC2", 20)))
        assertEquals(listOf("b", "c", "a"), db.feed().observeLatest(P, 10).first().map { it.videoId })
        assertTrue(db.subscriptions().observeIsSubscribed(P, "UC1").first())

        db.subscriptions().delete(P, "UC1")
        db.feed().deleteOrphans()
        assertFalse(db.subscriptions().observeIsSubscribed(P, "UC1").first())
        assertEquals(listOf("c"), db.feed().observeLatest(P, 10).first().map { it.videoId })
    }

    @Test
    fun лента_канала_заменяется_целиком() = runTest {
        P = profile("A")
        db.subscriptions().upsert(sub("UC1"))
        db.feed().replaceForChannel("UC1", listOf(item("a", "UC1", 10), item("b", "UC1", 20)))
        db.feed().replaceForChannel("UC1", listOf(item("c", "UC1", 30)))
        assertEquals(listOf("c"), db.feed().observeLatest(P, 10).first().map { it.videoId })
    }

    @Test
    fun продолжить_просмотр_только_5_95_процентов() = runTest {
        P = profile("A")
        val h = db.history()
        h.upsert(watched("start", 1_000, 100_000, 1))
        h.upsert(watched("middle", 50_000, 100_000, 2))
        h.upsert(watched("edge", 5_000, 100_000, 3))
        h.upsert(watched("done", 99_000, 100_000, 4))
        assertEquals(listOf("edge", "middle"), h.observeInProgress(P, 10).first().map { it.videoId })
    }

    @Test
    fun история_обрезается_до_лимита() = runTest {
        P = profile("A")
        val h = db.history()
        (1..10).forEach { h.upsert(watched("v$it", 0, 100, it.toLong())) }
        h.trim(P, 3)
        assertEquals(listOf("v10", "v9", "v8"), h.observeRecent(P, 100).first().map { it.videoId })
    }

    @Test
    fun отметки_у_каждого_профиля_свои_и_удаляются_с_профилем() = runTest {
        val a = db.profiles().insert(ProfileEntity(name = "A", createdAt = 1))
        val b = db.profiles().insert(ProfileEntity(name = "B", createdAt = 2))
        db.reactions().upsert(VideoReactionEntity(a, "v1", 1, 1))
        db.reactions().upsert(VideoReactionEntity(b, "v1", -1, 1))
        assertEquals(1, db.reactions().observe(a, "v1").first())
        assertEquals(-1, db.reactions().observe(b, "v1").first())

        db.profiles().delete(a)
        assertEquals(null, db.reactions().observe(a, "v1").first())
        assertEquals(-1, db.reactions().observe(b, "v1").first())
    }

    @Test
    fun подписки_и_история_у_профилей_раздельные() = runTest {
        val a = profile("A")
        val b = profile("B")
        db.subscriptions().upsert(sub("UC1", a))
        db.subscriptions().upsert(sub("UC2", b))
        db.feed().replaceForChannel("UC1", listOf(item("x", "UC1", 10)))
        db.feed().replaceForChannel("UC2", listOf(item("y", "UC2", 20)))
        db.history().upsert(watched("v", 50, 100, 1, a))

        assertEquals(listOf("x"), db.feed().observeLatest(a, 10).first().map { it.videoId })
        assertEquals(listOf("y"), db.feed().observeLatest(b, 10).first().map { it.videoId })
        assertTrue(db.history().observeRecent(b, 10).first().isEmpty())

        // Канал нужен другому профилю — его кэш не удаляется
        db.subscriptions().upsert(sub("UC2", a))
        db.subscriptions().delete(b, "UC2")
        db.feed().deleteOrphans()
        assertEquals(listOf("y", "x"), db.feed().observeLatest(a, 10).first().map { it.videoId })

        // Удаление профиля уносит его подписки и историю
        db.profiles().delete(a)
        assertTrue(db.subscriptions().getAll(a).isEmpty())
        assertTrue(db.history().getAll(a).isEmpty())
    }
}

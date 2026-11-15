package tv.lumen.data.library.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.lumen.core.datastore.MenuItem
import tv.lumen.core.datastore.PreferredCodec
import tv.lumen.core.datastore.Settings
import tv.lumen.core.model.SegmentMode
import tv.lumen.core.model.SponsorCategory

class BackupCodecTest {
    @Test
    fun `csv из takeout - id, имя, кавычки, дубли и чужие строки`() {
        val csv = "\uFEFFChannel Id,Channel Url,Channel Title\n" +
            "UCSMOQeBJ2RAnuFungnQOxLg,http://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg,Blender\n" +
            "UCXuqSBlHAE6Xw-yeJA0Tunw,http://www.youtube.com/channel/UCXuqSBlHAE6Xw-yeJA0Tunw,\"Linus Tech Tips, Inc\"\n" +
            "UCSMOQeBJ2RAnuFungnQOxLg,http://dup,Blender\n" +
            "\n" +
            "garbage,line\n"
        val subs = BackupCodec.parseTakeoutCsv(csv)
        assertEquals(listOf("UCSMOQeBJ2RAnuFungnQOxLg", "UCXuqSBlHAE6Xw-yeJA0Tunw"), subs.map { it.channelId })
        assertEquals("Linus Tech Tips, Inc", subs[1].name)
    }

    @Test
    fun `резервная копия переживает кодирование`() {
        val settings = Settings(
            maxResolution = 1080,
            preferredCodec = PreferredCodec.VP9,
            blockedWords = setOf("asmr"),
            sponsorCategories = Settings.DEFAULT_SPONSOR_MODES + (SponsorCategory.INTRO to SegmentMode.BUTTON),
            hiddenMenuItems = setOf(MenuItem.HISTORY),
        )
        val backup = Backup(
            subscriptions = listOf(SubscriptionBackup("UCSMOQeBJ2RAnuFungnQOxLg", "Blender", playbackSpeed = 1.5f)),
            history = listOf(HistoryBackup("aqz-KE-bpKQ", "BBB", "UC1", "Blender", null, 634_000, 100_000, 42)),
            settings = BackupCodec.toBackup(settings),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(backup))!!
        assertEquals(backup, restored)
        assertEquals(settings, BackupCodec.toSettings(restored.settings!!))
    }

    @Test
    fun `чужой json не принимается`() {
        assertNull(BackupCodec.decode("""{"format":"other","subscriptions":[]}"""))
        assertNull(BackupCodec.decode("not json"))
    }
}
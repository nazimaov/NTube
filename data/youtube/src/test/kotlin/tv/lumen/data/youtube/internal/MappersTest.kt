package tv.lumen.data.youtube.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import tv.lumen.core.model.AudioCodec
import tv.lumen.core.model.ChannelTab
import tv.lumen.core.model.DeliveryMethod
import tv.lumen.core.model.VideoCodec
import tv.lumen.core.model.channelUrl
import org.schabi.newpipe.extractor.stream.DeliveryMethod as NpDeliveryMethod

class MappersTest {

    @Test
    fun `channel id без префикса channel и обратно`() {
        assertEquals("UCSMOQeBJ2RAnuFungnQOxLg", Mappers.normalizeChannelId("channel/UCSMOQeBJ2RAnuFungnQOxLg"))
        assertEquals("@blender", Mappers.normalizeChannelId("@blender"))
        assertEquals("channel/UCSMOQeBJ2RAnuFungnQOxLg", Mappers.channelPath("UCSMOQeBJ2RAnuFungnQOxLg"))
        assertEquals("@blender", Mappers.channelPath("@blender"))
        assertEquals("https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg", channelUrl("UCSMOQeBJ2RAnuFungnQOxLg"))
        assertEquals("https://www.youtube.com/@blender", channelUrl("@blender"))
    }

    @Test
    fun `id видео и канала из ссылок`() {
        assertEquals("aqz-KE-bpKQ", Mappers.videoIdFromUrl("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        assertEquals("aqz-KE-bpKQ", Mappers.videoIdFromUrl("https://youtu.be/aqz-KE-bpKQ"))
        assertEquals(
            "UCSMOQeBJ2RAnuFungnQOxLg",
            Mappers.channelIdFromUrl("https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg"),
        )
        assertNull(Mappers.videoIdFromUrl("https://example.com/"))
        assertNull(Mappers.videoIdFromUrl(null))
    }

    @Test
    fun `карточка видео из StreamInfoItem`() {
        val item = StreamInfoItem(
            ServiceList.YouTube.serviceId,
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
            "Big Buck Bunny",
            StreamType.VIDEO_STREAM,
        ).apply {
            uploaderUrl = "https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg"
            uploaderName = "Blender"
            duration = 635
            viewCount = 1000
            isShortFormContent = false
        }
        val video = requireNotNull(Mappers.video(item))
        assertEquals("aqz-KE-bpKQ", video.id)
        assertEquals("UCSMOQeBJ2RAnuFungnQOxLg", video.channelId)
        assertEquals(635L, video.durationSec)
        assertFalse(video.isLive)
    }

    @Test
    fun `неизвестная длительность и прямой эфир`() {
        val item = StreamInfoItem(
            ServiceList.YouTube.serviceId,
            "https://www.youtube.com/watch?v=jfKfPfyJRdk",
            "live",
            StreamType.LIVE_STREAM,
        ).apply { duration = -1 }
        val video = requireNotNull(Mappers.video(item))
        assertEquals(-1L, video.durationSec)
        assertTrue(video.isLive)
    }

    @Test
    fun `обложка - самая большая не выше лимита`() {
        val images = listOf(
            Image("s", 90, 120, Image.ResolutionLevel.LOW),
            Image("m", 360, 480, Image.ResolutionLevel.MEDIUM),
            Image("l", 720, 1280, Image.ResolutionLevel.HIGH),
            Image("xl", 1080, 1920, Image.ResolutionLevel.HIGH),
        )
        assertEquals("l", Mappers.bestImageUrl(images))
        assertEquals("m", Mappers.bestImageUrl(images, maxHeight = 400))
        assertEquals("s", Mappers.bestImageUrl(images.take(1), maxHeight = 50))
        assertNull(Mappers.bestImageUrl(emptyList()))
    }

    @Test
    fun `обложка без высоты оценивается по уровню`() {
        val images = listOf(
            Image("low", Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.LOW),
            Image("high", Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.HIGH),
        )
        assertEquals("high", Mappers.bestImageUrl(images))
    }

    @Test
    fun `кодеки и высота`() {
        assertEquals(VideoCodec.AV1, Mappers.videoCodec("av01.0.08M.08"))
        assertEquals(VideoCodec.VP9, Mappers.videoCodec("vp9"))
        assertEquals(VideoCodec.VP9, Mappers.videoCodec("vp09.02.51.10"))
        assertEquals(VideoCodec.AVC, Mappers.videoCodec("avc1.640028"))
        assertEquals(VideoCodec.OTHER, Mappers.videoCodec(null))
        assertEquals(AudioCodec.OPUS, Mappers.audioCodec("opus", null))
        assertEquals(AudioCodec.AAC, Mappers.audioCodec("mp4a.40.2", null))
        assertEquals(AudioCodec.OPUS, Mappers.audioCodec(null, MediaFormat.WEBMA_OPUS))
        assertEquals(1080, Mappers.parseHeight("1080p60 HDR"))
        assertEquals(2160, Mappers.parseHeight("2160p"))
        assertEquals(0, Mappers.parseHeight(null))
    }

    @Test
    fun `способ доставки и вкладки канала`() {
        assertEquals(DeliveryMethod.PROGRESSIVE_HTTP, Mappers.delivery(NpDeliveryMethod.PROGRESSIVE_HTTP))
        assertEquals(DeliveryMethod.DASH, Mappers.delivery(NpDeliveryMethod.DASH))
        assertEquals(DeliveryMethod.HLS, Mappers.delivery(NpDeliveryMethod.HLS))
        assertEquals(DeliveryMethod.OTHER, Mappers.delivery(NpDeliveryMethod.SS))
        ChannelTab.entries.forEach { assertEquals(it, Mappers.channelTab(Mappers.channelTabName(it))) }
    }

    @Test
    fun `html комментария превращается в текст`() {
        val html = org.schabi.newpipe.extractor.stream.Description(
            "Comments<br>90% - Big Chungus<br/>&quot;free&quot; &amp; <a href=\"x\">open</a>",
            org.schabi.newpipe.extractor.stream.Description.Type.HTML,
        )
        assertEquals("Comments\n90% - Big Chungus\n\"free\" & open", Mappers.plainText(html))
    }
}
package tv.lumen.player.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.lumen.core.model.AudioCodec
import tv.lumen.core.model.AudioStream
import tv.lumen.core.model.DeliveryMethod
import tv.lumen.core.model.StreamSet
import tv.lumen.core.model.SubtitleTrack
import tv.lumen.core.model.VideoCodec
import tv.lumen.core.model.VideoStream
import tv.lumen.player.CodecPreference
import tv.lumen.player.PlayerPreferences

class StreamSelectorTest {

    private class FakeCaps(
        override val displayHeight: Int = 1080,
        override val hdrSupported: Boolean = false,
        override val hasHardwareVideoDecoders: Boolean = true,
        /** Кодек → максимальная высота аппаратного декодера. */
        val hardware: Map<VideoCodec, Int> = mapOf(VideoCodec.AVC to 2160, VideoCodec.VP9 to 2160, VideoCodec.AV1 to 2160),
        val software: Map<VideoCodec, Int> = emptyMap(),
    ) : DeviceCapabilities {
        override fun canDecode(codec: VideoCodec, width: Int, height: Int, fps: Int, allowSoftware: Boolean): Boolean =
            height <= (hardware[codec] ?: 0) || (allowSoftware && height <= (software[codec] ?: 0))
    }

    private fun v(height: Int, codec: VideoCodec, fps: Int = 30, hdr: Boolean = false, id: String = "$height$codec$fps$hdr") =
        VideoStream(id, "https://v/$id", DeliveryMethod.PROGRESSIVE_HTTP, null, codec, null, height * 16 / 9, height, fps, height * 1000, hdr, "${height}p")

    private fun a(codec: AudioCodec, bitrate: Int, trackId: String? = null, lang: String? = null, original: Boolean = true) =
        AudioStream("$codec$bitrate$trackId", "https://a", DeliveryMethod.PROGRESSIVE_HTTP, null, codec, null, bitrate, trackId, trackId, lang, original)

    private fun set(video: List<VideoStream>, audio: List<AudioStream> = listOf(a(AudioCodec.OPUS, 160)), muxed: List<VideoStream> = emptyList()) =
        StreamSet(video, audio, muxed, emptyList(), null, null, isLive = false, durationSec = 600)

    private val all = listOf(
        v(2160, VideoCodec.VP9, 60), v(2160, VideoCodec.AV1, 60),
        v(1440, VideoCodec.VP9, 60),
        v(1080, VideoCodec.AVC, 60), v(1080, VideoCodec.VP9, 60), v(1080, VideoCodec.AV1, 60), v(1080, VideoCodec.VP9, 30),
        v(720, VideoCodec.AVC, 30),
    )

    @Test
    fun `экран 1080 - лучший поток не выше 1080, AV1 впереди`() {
        val sel = StreamSelector(FakeCaps()).select(set(all), PlayerPreferences()) as Selection.Separate
        assertEquals(1080, sel.video.height)
        assertEquals(60, sel.video.fps)
        assertEquals(VideoCodec.AV1, sel.video.codec)
        assertEquals(listOf(2160, 1440, 1080, 720), sel.qualities.map { it.height })
    }

    @Test
    fun `без аппаратного AV1 берётся VP9`() {
        val caps = FakeCaps(hardware = mapOf(VideoCodec.AVC to 2160, VideoCodec.VP9 to 2160))
        val sel = StreamSelector(caps).select(set(all), PlayerPreferences()) as Selection.Separate
        assertEquals(VideoCodec.VP9, sel.video.codec)
        assertEquals(60, sel.video.fps)
    }

    @Test
    fun `декодер VP9 только до 1080 - 4K берётся в AV1`() {
        val caps = FakeCaps(displayHeight = 2160, hardware = mapOf(VideoCodec.VP9 to 1080, VideoCodec.AV1 to 2160))
        val sel = StreamSelector(caps).select(set(all), PlayerPreferences()) as Selection.Separate
        assertEquals(2160, sel.video.height)
        assertEquals(VideoCodec.AV1, sel.video.codec)
    }

    @Test
    fun `настройка maxResolution и ручной выбор качества`() {
        val selector = StreamSelector(FakeCaps(displayHeight = 2160))
        assertEquals(720, (selector.select(set(all), PlayerPreferences(maxResolution = 720)) as Selection.Separate).video.height)
        assertEquals(1440, (selector.select(set(all), PlayerPreferences(), requestedHeight = 1440) as Selection.Separate).video.height)
    }

    @Test
    fun `предпочтительный кодек из настроек`() {
        val sel = StreamSelector(FakeCaps()).select(set(all), PlayerPreferences(preferredCodec = CodecPreference.AVC)) as Selection.Separate
        assertEquals(VideoCodec.AVC, sel.video.codec)
    }

    @Test
    fun `HDR только при поддержке дисплея`() {
        val streams = listOf(v(1080, VideoCodec.VP9, 60, hdr = true), v(1080, VideoCodec.VP9, 30))
        val sdr = StreamSelector(FakeCaps()).select(set(streams), PlayerPreferences()) as Selection.Separate
        assertEquals(false, sdr.video.isHdr)
        val hdr = StreamSelector(FakeCaps(hdrSupported = true)).select(set(streams), PlayerPreferences()) as Selection.Separate
        assertEquals(true, hdr.video.isHdr)
    }

    @Test
    fun `без аппаратных декодеров вообще разрешены программные`() {
        val caps = FakeCaps(hasHardwareVideoDecoders = false, hardware = emptyMap(), software = mapOf(VideoCodec.AVC to 1080, VideoCodec.VP9 to 1080))
        val sel = StreamSelector(caps).select(set(all), PlayerPreferences()) as Selection.Separate
        assertEquals(1080, sel.video.height)
    }

    @Test
    fun `программные не используются, если есть аппаратные`() {
        val caps = FakeCaps(hardware = mapOf(VideoCodec.AVC to 720), software = mapOf(VideoCodec.VP9 to 2160))
        val sel = StreamSelector(caps).select(set(all), PlayerPreferences()) as Selection.Separate
        assertEquals(720, sel.video.height)
        assertEquals(VideoCodec.AVC, sel.video.codec)
    }

    @Test
    fun `нет отдельных потоков - 360p со звуком и уведомление`() {
        val muxed = listOf(v(360, VideoCodec.AVC))
        val sel = StreamSelector(FakeCaps()).select(set(emptyList(), muxed = muxed), PlayerPreferences())
        assertTrue(sel is Selection.Muxed && sel.lowQualityNotice && sel.stream.height == 360)
    }

    @Test
    fun `аудио - opus с наибольшим битрейтом, оригинальная дорожка`() {
        val audio = listOf(
            a(AudioCodec.AAC, 256, "en.4", "en", original = true),
            a(AudioCodec.OPUS, 128, "en.4", "en", original = true),
            a(AudioCodec.OPUS, 160, "en.4", "en", original = true),
            a(AudioCodec.OPUS, 160, "ru.3", "ru", original = false),
        )
        val selector = StreamSelector(FakeCaps())
        val original = selector.selectAudio(audio, PlayerPreferences(), null)!!
        assertEquals(AudioCodec.OPUS, original.codec)
        assertEquals(160, original.bitrate)
        assertEquals("en.4", original.trackId)
        assertEquals("ru.3", selector.selectAudio(audio, PlayerPreferences(audioLanguage = "ru"), null)!!.trackId)
        assertEquals("ru.3", selector.selectAudio(audio, PlayerPreferences(), "ru.3")!!.trackId)
    }

    @Test
    fun `субтитры - ручные важнее автоматических`() {
        val tracks = listOf(
            SubtitleTrack("u1", "en", "English (auto)", true, null),
            SubtitleTrack("u2", "en", "English", false, null),
        )
        val selector = StreamSelector(FakeCaps())
        assertEquals("u2", selector.selectSubtitle(tracks, "en")?.url)
        assertNull(selector.selectSubtitle(tracks, null))
        assertNull(selector.selectSubtitle(tracks, "de"))
    }

    @Test
    fun `прямой эфир идёт по манифесту`() {
        val live = StreamSet(emptyList(), emptyList(), emptyList(), emptyList(), "https/hls.m3u8", null, isLive = true, durationSec = -1)
        assertTrue(StreamSelector(FakeCaps()).select(live, PlayerPreferences()) is Selection.Live)
    }

    @Test
    fun `режим только аудио выбирает один звуковой поток без видео`() {
        val sel = StreamSelector(FakeCaps()).select(set(all), PlayerPreferences(), audioOnly = true)
        assertTrue(sel is Selection.AudioOnly)
        assertEquals(AudioCodec.OPUS, (sel as Selection.AudioOnly).audio.codec)
    }

    @Test
    fun `режим только аудио без звуковых потоков играет как обычное видео`() {
        val sel = StreamSelector(FakeCaps()).select(set(all, audio = emptyList(), muxed = listOf(v(360, VideoCodec.AVC))), PlayerPreferences(), audioOnly = true)
        assertTrue(sel is Selection.Muxed)
    }
}

package tv.lumen.data.youtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tv.lumen.core.model.VideoCodec
import tv.lumen.data.youtube.internal.TvPlayerParser
import java.time.Instant

class TvPlayerParserTest {
    private val json = """
        {"playabilityStatus":{"status":"OK"},
         "videoDetails":{"title":"Baby Shark","author":"Pinkfong","channelId":"UCcdwLMPsaU2ezNSJU1nFoBQ","lengthSeconds":"136","viewCount":"1000"},
         "streamingData":{
           "formats":[{"itag":18,"url":"https://a/18?n=X","mimeType":"video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"","width":640,"height":360}],
           "adaptiveFormats":[
             {"itag":137,"signatureCipher":"s=ABC&sp=sig&url=https%3A%2F%2Fa%2F137%3Fn%3DY","mimeType":"video/mp4; codecs=\"avc1.640028\"","width":1920,"height":1080,"fps":30,"qualityLabel":"1080p"},
             {"itag":251,"url":"https://a/251","mimeType":"audio/webm; codecs=\"opus\"","bitrate":160000}
           ]}}
    """.trimIndent()

    @Test
    fun parsesStreamsAndDeciphersSignature() {
        val d = TvPlayerParser.details("id", JSONObject(json), Instant.EPOCH, decipher = { it.reversed() }, throttling = { "$it&t" })
        assertNotNull(d)
        d!!
        assertEquals("Baby Shark", d.video.title)
        assertEquals(136L, d.streams.durationSec)
        val v = d.streams.videoOnly.single()
        assertEquals("https://a/137?n=Y&sig=CBA&t", v.url)
        assertEquals(VideoCodec.AVC, v.codec)
        assertEquals(1080, v.height)
        assertEquals("opus", d.streams.audio.single().codecString)
        assertEquals("https://a/18?n=X&t", d.streams.muxed.single().url)
    }

    @Test
    fun unplayableStatusIsReported() {
        assertNull(TvPlayerParser.unplayableReason(JSONObject(json)))
        assertEquals("UNPLAYABLE: no", TvPlayerParser.unplayableReason(JSONObject("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"no"}}""")))
    }
}

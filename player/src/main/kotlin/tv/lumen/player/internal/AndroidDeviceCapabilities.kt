package tv.lumen.player.internal

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import tv.lumen.core.model.VideoCodec
import javax.inject.Inject
import javax.inject.Singleton

/** Проверка дисплея и декодеров (SPEC 5.3): MediaCodecUtil + isVideoSizeAndRateSupported. */
@Singleton
@OptIn(UnstableApi::class)
internal class AndroidDeviceCapabilities @Inject constructor(
    @ApplicationContext context: Context,
) : DeviceCapabilities {

    private val display: Display? =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    override val displayHeight: Int by lazy {
        val mode = display?.mode ?: return@lazy 0
        minOf(mode.physicalWidth, mode.physicalHeight)
    }

    override val hdrSupported: Boolean by lazy {
        @Suppress("DEPRECATION")
        display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    }

    override val hasHardwareVideoDecoders: Boolean by lazy {
        VideoCodec.entries.mapNotNull(::mimeType).any { mime -> decoders(mime).any { it.hardwareAccelerated } }
    }

    private val cache = HashMap<String, Boolean>()

    @Synchronized
    override fun canDecode(codec: VideoCodec, width: Int, height: Int, fps: Int, allowSoftware: Boolean): Boolean {
        val mime = mimeType(codec) ?: return false
        val key = "$mime:$width:$height:$fps:$allowSoftware"
        return cache.getOrPut(key) {
            val w = if (width > 0) width else height * 16 / 9
            val rate = if (fps > 0) fps.toDouble() else 30.0
            decoders(mime).any { info ->
                (allowSoftware || info.hardwareAccelerated) && info.isVideoSizeAndRateSupported(w, height, rate)
            }
        }
    }

    private fun decoders(mime: String): List<MediaCodecInfo> =
        runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false) }.getOrDefault(emptyList())

    private fun mimeType(codec: VideoCodec): String? = when (codec) {
        VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
        VideoCodec.VP9 -> MimeTypes.VIDEO_VP9
        VideoCodec.AVC -> MimeTypes.VIDEO_H264
        VideoCodec.OTHER -> null
    }
}

package tv.lumen.feature.watch

import android.os.Build
import android.view.Display
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Автоподстройка частоты кадров дисплея под видео (SPEC 5.6): preferredDisplayModeId с тем же
 * разрешением и частотой, равной или кратной fps видео; при выходе из плеера — возврат прежнего режима.
 */
@Composable
internal fun FrameRateMatcher(enabled: Boolean, fps: Int?) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(enabled, fps) {
        val window = activity.window
        val previous = window.attributes.preferredDisplayModeId
        if (enabled && fps != null && fps > 0) {
            @Suppress("DEPRECATION")
            val display: Display? = if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
            pickMode(display, fps)?.let { modeId ->
                window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
            }
        }
        onDispose {
            window.attributes = window.attributes.apply { preferredDisplayModeId = previous }
        }
    }
}

/** Точное совпадение частоты лучше кратного (24 → 24 Гц, иначе 48/72/120). null — подходящего режима нет. */
internal fun pickMode(display: Display?, fps: Int): Int? {
    display ?: return null
    val current = display.mode
    val sameSize = display.supportedModes.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }
    val target = fps.toFloat()
    val exact = sameSize.firstOrNull { abs(it.refreshRate - target) < 0.6f }
    val multiple = sameSize.filter { m ->
        val k = m.refreshRate / target
        k.roundToInt() >= 2 && abs(k - k.roundToInt()) < 0.02f
    }.minByOrNull { it.refreshRate }
    return (exact ?: multiple)?.modeId?.takeIf { it != current.modeId }
}
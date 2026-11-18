package tv.lumen.core.designsystem.component

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/**
 * Для диалога, который открывается долгим нажатием OK: пока кнопку не отпустили, её нажатия и повторы
 * не доходят до пунктов диалога. Иначе отпускание OK сразу «нажимает» первый пункт, и меню появляется и
 * тут же исчезает (на пульте ТВ). Если кнопку отпустили ещё до открытия, диалог оживает через 0,6 с тишины.
 */
fun Modifier.swallowOpeningKeyPress(): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    var lastOkDown by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val openedAt = System.currentTimeMillis()
        while (!armed) {
            delay(100)
            if (System.currentTimeMillis() - maxOf(openedAt, lastOkDown) > QUIET_MS) armed = true
        }
    }
    onPreviewKeyEvent { event ->
        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            !ok || armed -> false
            event.type == KeyEventType.KeyUp -> { armed = true; true }
            else -> { lastOkDown = System.currentTimeMillis(); true }
        }
    }
}

private const val QUIET_MS = 600L

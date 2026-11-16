package tv.lumen.feature.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.lumen.core.designsystem.component.LumenChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.lumen.core.designsystem.theme.LumenDimens

private const val COUNTDOWN_S = 10

/** «Далее через N с» с кнопками «Смотреть» и «Отмена» (SPEC 5.6). */
@Composable
internal fun NextUpOverlay(title: String, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var left by remember(title) { mutableIntStateOf(COUNTDOWN_S) }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(title) {
        runCatching { cancelFocus.requestFocus() }
        while (left > 0) {
            delay(1_000)
            left--
        }
        onPlay()
    }
    Column(
        modifier = modifier
            .padding(horizontal = LumenDimens.SafeHorizontal, vertical = LumenDimens.SafeVertical)
            .width(480.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.watch_next_in, left), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LumenChip(text = stringResource(R.string.watch_play), selected = true, onClick = onPlay)
            LumenChip(text = stringResource(R.string.watch_cancel), selected = false, onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus))
        }
    }
}
package tv.lumen.feature.watch

import androidx.compose.foundation.background
import tv.lumen.core.designsystem.component.swallowOpeningKeyPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.core.model.Video

/**
 * Редактирование очереди: действия с выбранным видео. [index] — позиция в очереди, [size] — её длина;
 * сдвиги недоступны на краях.
 */
@Composable
fun QueueItemDialog(
    video: Video,
    index: Int,
    size: Int,
    onPlay: () -> Unit,
    onMove: (delta: Int) -> Unit,
    onRemove: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val actions = buildList {
        add(stringResource(R.string.queue_play) to onPlay)
        if (index > 0) add(stringResource(R.string.queue_move_left) to { onMove(-1) })
        if (index < size - 1) add(stringResource(R.string.queue_move_right) to { onMove(+1) })
        add(stringResource(R.string.queue_remove) to onRemove)
        if (size > 1) add(stringResource(R.string.queue_clear) to onClear)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.swallowOpeningKeyPress().width(480.dp).background(LumenColors.Surface, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            actions.forEachIndexed { i, (label, action) ->
                ListItem(
                    selected = false,
                    onClick = {
                        onDismiss()
                        action()
                    },
                    headlineContent = { Text(label) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

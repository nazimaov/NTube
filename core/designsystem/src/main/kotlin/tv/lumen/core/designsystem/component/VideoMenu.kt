package tv.lumen.core.designsystem.component

import androidx.compose.foundation.background
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
import tv.lumen.core.designsystem.R
import tv.lumen.core.model.Video

/**
 * Меню долгого нажатия OK на карточке (SPEC 8): «Открыть канал», «Подписаться», «Не показывать канал»,
 * «Удалить из истории». Пункт без обработчика не показывается.
 */
@Composable
fun VideoMenu(
    video: Video,
    isSubscribed: Boolean,
    onDismiss: () -> Unit,
    onOpenChannel: (() -> Unit)?,
    onToggleSubscribe: (() -> Unit)?,
    onHideChannel: (() -> Unit)?,
    onRemoveFromHistory: (() -> Unit)?,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val hasChannel = video.channelId != null
    val items = buildList {
        if (hasChannel && onOpenChannel != null) add(stringResource(R.string.ds_menu_open_channel) to onOpenChannel)
        if (hasChannel && onToggleSubscribe != null) {
            add(stringResource(if (isSubscribed) R.string.ds_menu_unsubscribe else R.string.ds_menu_subscribe) to onToggleSubscribe)
        }
        if (hasChannel && onHideChannel != null) add(stringResource(R.string.ds_menu_hide_channel) to onHideChannel)
        if (onRemoveFromHistory != null) add(stringResource(R.string.ds_menu_remove_history) to onRemoveFromHistory)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            items.forEachIndexed { index, (label, action) ->
                ListItem(
                    selected = false,
                    onClick = {
                        onDismiss()
                        action()
                    },
                    headlineContent = { Text(label) },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}
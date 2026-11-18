package tv.lumen.core.designsystem.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
 * Меню долгого нажатия OK на карточке (SPEC 8). Пункты: очередь, «Смотреть позже», свои плейлисты,
 * «Не интересно» (видео сразу исчезает из списков), канал (открыть, подписка, не показывать),
 * «Удалить из истории». Пункт без обработчика не показывается; [extraItems] — особые пункты экрана
 * (например «Убрать из плейлиста»), они идут первыми.
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
    extraItems: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val queue = LocalQueueActions.current
    val extras = LocalMenuExtras.current
    val context = LocalContext.current
    val hasChannel = video.channelId != null
    var picking by remember { mutableStateOf(false) }

    if (picking && extras != null) {
        PlaylistPicker(video, extras, onDismiss = onDismiss)
        return
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val laterName = stringResource(R.string.ds_playlist_watch_later)
    val toastHidden = stringResource(R.string.ds_toast_hidden)
    val toastLater = stringResource(R.string.ds_toast_added_later)
    val toastLaterRemoved = stringResource(R.string.ds_toast_removed_later)
    val items = buildList<Pair<String, () -> Unit>> {
        addAll(extraItems)
        if (extras != null) {
            add(stringResource(R.string.ds_menu_not_interested) to {
                extras.hide(video)
                Toast.makeText(context, toastHidden, Toast.LENGTH_SHORT).show()
            })
        }
        if (queue != null) {
            val queued = queue.contains(video.id)
            add(
                stringResource(if (queued) R.string.ds_menu_in_queue else R.string.ds_menu_add_queue) to {
                    if (!queued) {
                        queue.add(video)
                        Toast.makeText(context, R.string.ds_toast_added_queue, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        if (extras != null) {
            val later = extras.isWatchLater(video.id)
            add(
                stringResource(if (later) R.string.ds_menu_remove_later else R.string.ds_menu_watch_later) to {
                    extras.toggleWatchLater(video, laterName)
                    Toast.makeText(context, if (later) toastLaterRemoved else toastLater, Toast.LENGTH_SHORT).show()
                },
            )
        }
        if (hasChannel && onOpenChannel != null) add(stringResource(R.string.ds_menu_open_channel) to onOpenChannel)
        if (hasChannel && onToggleSubscribe != null) {
            add(stringResource(if (isSubscribed) R.string.ds_menu_unsubscribe else R.string.ds_menu_subscribe) to onToggleSubscribe)
        }
        if (hasChannel && onHideChannel != null) add(stringResource(R.string.ds_menu_hide_channel) to onHideChannel)
        if (onRemoveFromHistory != null) add(stringResource(R.string.ds_menu_remove_history) to onRemoveFromHistory)
    }
    // «Добавить в плейлист» открывает второе окно, поэтому это меню не закрывается
    val addToPlaylist = stringResource(R.string.ds_menu_add_playlist)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .swallowOpeningKeyPress()
                .width(480.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val entries = buildList {
                var inserted = false
                items.forEachIndexed { i, item ->
                    add(item)
                    // Плейлисты — сразу после «Смотреть позже»
                    if (!inserted && extras != null && item.first.let { it == stringResource(R.string.ds_menu_watch_later) || it == stringResource(R.string.ds_menu_remove_later) }) {
                        add(addToPlaylist to { picking = true })
                        inserted = true
                    }
                }
                if (!inserted && extras != null) add(addToPlaylist to { picking = true })
            }
            entries.forEachIndexed { index, (label, action) ->
                ListItem(
                    selected = false,
                    onClick = {
                        if (label == addToPlaylist && extras != null) {
                            picking = true
                        } else {
                            onDismiss()
                            action()
                        }
                    },
                    headlineContent = { Text(label) },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

/** Выбор плейлиста: «Новый плейлист» и свои списки с отметкой, где видео уже есть (повторное нажатие убирает). */
@Composable
private fun PlaylistPicker(video: Video, extras: MenuExtras, onDismiss: () -> Unit) {
    val lists by extras.playlists.collectAsState()
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val newName = stringResource(R.string.ds_playlist_default_name, lists.count { it.id != MenuExtras.WATCH_LATER } + 1)
    val toastAdded = stringResource(R.string.ds_toast_added_playlist)
    val toastRemoved = stringResource(R.string.ds_toast_removed_playlist)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.ds_menu_add_playlist), style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    ListItem(
                        selected = false,
                        onClick = {
                            extras.createPlaylist(video, newName)
                            Toast.makeText(context, toastAdded, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        headlineContent = { Text("＋ " + stringResource(R.string.ds_playlist_new)) },
                        modifier = Modifier.focusRequester(first),
                    )
                }
                itemsIndexed(lists, key = { _, l -> l.id }) { _, list ->
                    val has = video.id in list.videoIds
                    ListItem(
                        selected = has,
                        onClick = {
                            extras.toggleInPlaylist(list.id, video)
                            Toast.makeText(context, if (has) toastRemoved else toastAdded, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        headlineContent = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(stringResource(R.string.ds_playlist_count, list.size)) },
                        trailingContent = { if (has) Text("✓") },
                    )
                }
            }
        }
    }
}

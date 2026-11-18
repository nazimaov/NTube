package tv.lumen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.app.R
import tv.lumen.data.updater.UpdateInfo
import tv.lumen.data.updater.UpdateState

/** Диалог самообновления (SPEC 10.5, п. 2–4). Ничего не показывает в состояниях Idle и Checking без запроса. */
@Composable
fun UpdateDialog(state: UpdateState, onUpdate: (UpdateInfo) -> Unit, onDismiss: () -> Unit) {
    val (title, body, action) = when (state) {
        is UpdateState.Available -> Triple(
            stringResource(R.string.update_available, state.info.version),
            tv.lumen.data.updater.ReleaseNotes.select(state.info.notes, androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language),
            stringResource(R.string.update_install) to { onUpdate(state.info) },
        )
        is UpdateState.Downloading -> Triple(
            stringResource(R.string.update_downloading, state.info.version),
            if (state.progress >= 0) "${(state.progress * 100).toInt()}%" else "",
            null,
        )
        is UpdateState.Installing -> Triple(stringResource(R.string.update_installing), "", null)
        UpdateState.UpToDate -> Triple(stringResource(R.string.update_up_to_date), "", null)
        UpdateState.Checking -> Triple(stringResource(R.string.update_checking), "", null)
        is UpdateState.Failed -> Triple(
            stringResource(
                when (state.reason) {
                    UpdateState.Reason.NOT_CONFIGURED -> R.string.update_not_configured
                    UpdateState.Reason.NO_APK -> R.string.update_no_apk
                    UpdateState.Reason.INSTALL_FAILED -> R.string.update_install_failed
                    UpdateState.Reason.NETWORK -> R.string.update_network
                },
            ),
            "",
            null,
        )
        UpdateState.Idle -> return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(640.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (body.isNotBlank()) {
                if (state is UpdateState.Available) {
                    Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (action != null) {
                    Button(onClick = action.second, modifier = Modifier.focusRequester(focus)) { Text(action.first) }
                    Button(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                } else {
                    Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.update_close)) }
                }
            }
        }
    }
}
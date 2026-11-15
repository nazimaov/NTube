package tv.lumen.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Адрес встроенного сервера и QR-код для телефона (SPEC 7.1). */
@Composable
internal fun BackupDialog(state: BackupUiState, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val qr = remember(state.url) { state.url?.let(::qrBitmap) }
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier.width(960.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleLarge)
            if (state.url == null) {
                Text(stringResource(R.string.settings_backup_no_network))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    qr?.let { Image(it, contentDescription = null, modifier = Modifier.size(200.dp).background(Color.White).padding(8.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.settings_backup_hint))
                        Text(state.url, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            state.lastResult?.let {
                Text(stringResource(R.string.settings_backup_done, it.subscriptions, it.history), color = MaterialTheme.colorScheme.primary)
            }
            if (state.lastFailed) Text(stringResource(R.string.settings_backup_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = onClose, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.settings_close)) }
        }
    }
}

private fun qrBitmap(text: String): ImageBitmap {
    val size = 512
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
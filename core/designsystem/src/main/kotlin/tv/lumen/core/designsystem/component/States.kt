package tv.lumen.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.R
import tv.lumen.core.model.AppError
import tv.lumen.core.model.UnavailableReason

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LumenLoader()
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) = EmptyState(stringResource(R.string.ds_empty), modifier)

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

/** Текст ошибки и кнопка «Повторить» (SPEC 3). Кнопка получает фокус, чтобы пульт не «терялся». */
@Composable
fun ErrorState(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(error) { runCatching { focus.requestFocus() } }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.widthIn(max = 720.dp),
        ) {
            Text(errorMessage(error), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) {
                Text(stringResource(R.string.ds_retry))
            }
        }
    }
}

@Composable
fun errorMessage(error: AppError): String = stringResource(
    when (error) {
        AppError.Network -> R.string.ds_error_network
        AppError.ExtractionBroken -> R.string.ds_error_extraction
        AppError.AgeRestricted -> R.string.ds_error_age
        is AppError.ContentUnavailable -> when (error.reason) {
            UnavailableReason.PRIVATE -> R.string.ds_error_private
            UnavailableReason.GEO_BLOCKED -> R.string.ds_error_geo
            UnavailableReason.PAID -> R.string.ds_error_paid
            UnavailableReason.BOT_CHECK -> R.string.ds_error_bot
            UnavailableReason.SIGN_IN_REQUIRED -> R.string.ds_error_sign_in
            else -> R.string.ds_error_unavailable
        }
        is AppError.Unknown -> R.string.ds_error_unknown
    },
)
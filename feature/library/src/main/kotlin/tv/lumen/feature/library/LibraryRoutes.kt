package tv.lumen.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumen.core.designsystem.component.PlaceholderScreen

@Composable
fun SubscriptionsRoute(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.subscriptions_title),
        note = stringResource(R.string.library_placeholder),
        modifier = modifier,
    )
}

@Composable
fun HistoryRoute(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.history_title),
        note = stringResource(R.string.library_placeholder),
        modifier = modifier,
    )
}
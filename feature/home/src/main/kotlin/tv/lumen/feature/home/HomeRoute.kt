package tv.lumen.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumen.core.designsystem.component.PlaceholderScreen

@Composable
fun HomeRoute(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.home_title),
        note = stringResource(R.string.home_placeholder),
        modifier = modifier,
    )
}
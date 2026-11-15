package tv.lumen.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumen.core.designsystem.component.PlaceholderScreen

@Composable
fun SearchRoute(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.search_title),
        note = stringResource(R.string.search_placeholder),
        modifier = modifier,
    )
}
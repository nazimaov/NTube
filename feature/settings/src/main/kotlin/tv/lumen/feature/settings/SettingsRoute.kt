package tv.lumen.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.lumen.core.designsystem.component.PlaceholderScreen

@Composable
fun SettingsRoute(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        note = stringResource(R.string.settings_placeholder),
        modifier = modifier,
    )
}
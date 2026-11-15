package tv.lumen.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.theme.LumenDimens

/** Временные id для проверки плеера (фаза 2). Уберутся, когда в фазе 3 появится лента. */
private const val TEST_VIDEO_ID = "aqz-KE-bpKQ" // Big Buck Bunny 60fps 4K, канал Blender
// Круглосуточный эфир Lofi Girl, актуален на 2026-11-15. Эфиры перезапускаются с новым id —
// если кнопка показывает «недоступно», взять id со страницы youtube.com/@LofiGirl/live
private const val TEST_LIVE_ID = "4t4AQ15yj1M"

@Composable
fun HomeRoute(onOpenVideo: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = LumenDimens.SafeHorizontal, vertical = LumenDimens.SafeVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.home_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onOpenVideo(TEST_VIDEO_ID) }) { Text(stringResource(R.string.home_test_video)) }
            Button(onClick = { onOpenVideo(TEST_LIVE_ID) }) { Text(stringResource(R.string.home_test_live)) }
        }
    }
}

package tv.lumen.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.theme.LumenColors

private val ChipShape = RoundedCornerShape(10.dp)

/**
 * Чип-фильтр как у YouTube: выбранный — светлый с тёмным текстом, остальные — серые.
 * В фокусе — светлая рамка 2 dp.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumenChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ChipShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) LumenColors.TextPrimary else LumenColors.SurfaceCard,
            contentColor = if (selected) LumenColors.Background else LumenColors.TextPrimary,
            focusedContainerColor = if (selected) LumenColors.TextPrimary else LumenColors.SurfaceFocused,
            focusedContentColor = if (selected) LumenColors.Background else LumenColors.TextPrimary,
            pressedContainerColor = LumenColors.SurfaceFocused,
            pressedContentColor = LumenColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, LumenColors.FocusRing), shape = ChipShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = if (icon != null) 12.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = text, style = MaterialTheme.typography.titleSmall, color = Color.Unspecified)
        }
    }
}

/** Ряд чипов с одинаковым зазором. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

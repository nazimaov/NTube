package tv.lumen.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.theme.LumenColors

/**
 * Круглая кнопка-иконка панели плеера, как у YouTube на ТВ: в покое — полупрозрачная,
 * в фокусе — белая с тёмной иконкой, а под ней появляется подпись [label].
 * [caption] (например, число лайков) показывается внутри вытянутой кнопки рядом с иконкой.
 * [active] подсвечивает включённое состояние (например, субтитры) красной точкой.
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    active: Boolean = false,
    caption: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (caption == null) Modifier.width(size) else Modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                focusedContainerColor = LumenColors.TextPrimary,
                focusedContentColor = LumenColors.Background,
                pressedContainerColor = Color.White.copy(alpha = 0.7f),
                pressedContentColor = LumenColors.Background,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = (if (caption == null) modifier.size(size) else modifier.height(size)).onFocusChanged { focused = it.hasFocus },
        ) {
            Box(if (caption == null) Modifier.size(size) else Modifier.height(size), contentAlignment = Alignment.Center) {
                if (caption == null) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.5f))
                } else {
                    // Вытянутая кнопка как у YouTube: иконка и число внутри
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 18.dp),
                    ) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.45f))
                        Text(caption, style = MaterialTheme.typography.titleSmall, color = Color.Unspecified, maxLines = 1)
                    }
                }
                if (active) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(6.dp)
                            .clip(CircleShape).background(LumenColors.Red),
                    )
                }
            }
        }
        // Подпись видна только в фокусе; место под неё зарезервировано, чтобы ряд не прыгал
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentWidth(unbounded = true).alpha(if (focused) 1f else 0f),
        )
    }
}

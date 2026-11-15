package tv.lumen.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val LumenDarkColors = darkColorScheme(
    primary = Color(0xFFFF5252),
    onPrimary = Color(0xFF1A0000),
    primaryContainer = Color(0xFF8C1D1D),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE6BDB8),
    onSecondary = Color(0xFF442926),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFECE0DF),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFECE0DF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFD8C2BF),
    border = Color(0xFFA08C8A),
)

/** Приложение только с тёмной темой (SPEC, раздел 8). */
@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumenDarkColors, content = content)
}
package tv.lumen.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** Палитра тёмной темы YouTube: нейтральные серые без цветового оттенка, красный — только как акцент. */
object LumenColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF212121)
    val SurfaceCard = Color(0xFF272727)
    val SurfaceFocused = Color(0xFF3F3F3F)
    val TextPrimary = Color(0xFFF1F1F1)
    val TextSecondary = Color(0xFFAAAAAA)
    val Divider = Color(0xFF303030)
    val Red = Color(0xFFFF0000)
    val FocusRing = Color(0xFFF1F1F1)
}

private val LumenDarkColors = darkColorScheme(
    primary = LumenColors.Red,
    onPrimary = Color.White,
    primaryContainer = LumenColors.SurfaceFocused,
    onPrimaryContainer = LumenColors.TextPrimary,
    secondary = LumenColors.TextPrimary,
    onSecondary = LumenColors.Background,
    background = LumenColors.Background,
    onBackground = LumenColors.TextPrimary,
    surface = LumenColors.Background,
    onSurface = LumenColors.TextPrimary,
    surfaceVariant = LumenColors.Surface,
    onSurfaceVariant = LumenColors.TextSecondary,
    border = LumenColors.FocusRing,
)

/** Размеры для ТВ 1080p (960×540 dp): заголовки и подписи крупнее, чем у мобильного YouTube. */
private val LumenTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Приложение только с тёмной темой (SPEC, раздел 8). */
@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumenDarkColors, typography = LumenTypography, content = content)
}

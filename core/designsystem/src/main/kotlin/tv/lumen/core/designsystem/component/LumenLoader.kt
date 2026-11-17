package tv.lumen.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.lumen.core.designsystem.R
import tv.lumen.core.designsystem.theme.LumenColors

/**
 * Индикатор загрузки: красный знак NTube (N с Play, без надписи) и вокруг него вращающееся красное кольцо с
 * мягким свечением. Оборот 360° за 1,2 с, линейно, без остановки (референс владельца).
 */
@Composable
fun LumenLoader(modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val rotation by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    val description = stringResource(R.string.ds_loading)
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.06f
            val inset = stroke * 1.5f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            // Тусклая дорожка кольца
            drawArc(LumenColors.Red.copy(alpha = 0.22f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            // Свечение под бегущей дугой и сама дуга
            drawArc(LumenColors.Red.copy(alpha = 0.28f), rotation, 110f, false, topLeft, arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(LumenColors.Red, rotation, 110f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Image(
            painter = painterResource(R.drawable.ds_logo_emblem),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(size * 0.24f),
        )
    }
}

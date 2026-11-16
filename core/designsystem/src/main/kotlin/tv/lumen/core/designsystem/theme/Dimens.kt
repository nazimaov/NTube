package tv.lumen.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** Отступы безопасной зоны ТВ: 48 dp по горизонтали, 27 dp по вертикали (SPEC, раздел 8). */
object LumenDimens {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp

    /**
     * Отступ контента от бокового меню. Меню уже стоит в безопасной зоне, поэтому слева
     * достаточно небольшого зазора; справа остаётся [SafeHorizontal].
     */
    val ContentStart = 12.dp
}

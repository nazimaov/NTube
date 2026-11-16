package tv.lumen.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Иконки, которых нет в material-icons-core. Контуры — из Material Icons (Apache 2.0),
 * чтобы не подключать тяжёлый material-icons-extended.
 */
object LumenIcons {
    val Pause: ImageVector by lazy { icon("Pause", "M6 5h4v14H6zM14 5h4v14h-4z") }

    val Speed: ImageVector by lazy {
        icon(
            "Speed",
            "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19" +
                "a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0" +
                "l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
        )
    }

    val Subtitles: ImageVector by lazy {
        icon(
            "Subtitles",
            "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2z" +
                "m10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z",
        )
    }

    val Comment: ImageVector by lazy {
        icon(
            "Comment",
            "M21.99 4c0-1.1-.89-2-1.99-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14l4 4-.01-18zM18 14H6v-2h12v2z" +
                "m0-3H6V9h12v2zm0-3H6V6h12v2z",
        )
    }

    val MusicNote: ImageVector by lazy {
        icon("MusicNote", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z")
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}

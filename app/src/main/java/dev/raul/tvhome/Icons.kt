package dev.raul.tvhome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 24x24 icon paths, matching the design canvas.
private const val GEAR = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
private const val PLAY = "M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z"
private const val HDMI = "M3 8h18v8H3z M7 12h10"
private const val TV = "M3 5h18v12H3z M8 21h8"
private const val CHEVRON = "M9 5l7 7-7 7"
private const val CLOSE = "M6 6l12 12 M18 6L6 18"
private const val MOVE = "M8 7l-5 5 5 5 M16 7l5 5-5 5 M3 12h18"
private const val MINUS_CIRCLE = "M5 12h14 M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"
private const val PLUS = "M12 5v14 M5 12h14"

private fun vector(path: String, color: Color, filled: Boolean, strokeWidth: Float): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = if (filled) SolidColor(color) else null,
            stroke = if (filled) null else SolidColor(color),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

@Composable
private fun PathIcon(path: String, color: Color, size: Dp, filled: Boolean = false, strokeWidth: Float = 1.7f) {
    val v = remember(path, color, filled, strokeWidth) { vector(path, color, filled, strokeWidth) }
    Image(rememberVectorPainter(v), contentDescription = null, modifier = Modifier.size(size))
}

@Composable fun GearIcon(color: Color, size: Dp) = PathIcon(GEAR, color, size, strokeWidth = 1.6f)
@Composable fun PlayIcon(color: Color, size: Dp) = PathIcon(PLAY, color, size, filled = true)
@Composable fun HdmiIcon(color: Color, size: Dp) = PathIcon(HDMI, color, size)
@Composable fun TvIcon(color: Color, size: Dp) = PathIcon(TV, color, size)
@Composable fun ChevronIcon(color: Color, size: Dp) = PathIcon(CHEVRON, color, size, strokeWidth = 2.4f)
@Composable fun CloseIcon(color: Color, size: Dp) = PathIcon(CLOSE, color, size, strokeWidth = 2.2f)
@Composable fun MoveArrowsIcon(color: Color, size: Dp) = PathIcon(MOVE, color, size, strokeWidth = 2f)
@Composable fun MinusCircleIcon(color: Color, size: Dp) = PathIcon(MINUS_CIRCLE, color, size, strokeWidth = 2f)
@Composable fun PlusIcon(color: Color, size: Dp) = PathIcon(PLUS, color, size, strokeWidth = 2.4f)

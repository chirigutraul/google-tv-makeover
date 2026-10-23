package dev.raul.tvhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Makes 1.dp equal one pixel of the 1920x1080 design, so sizes can be copied from the canvas
 * as-is and the layout fills the TV whatever its density.
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        CompositionLocalProvider(LocalDensity provides Density(widthPx / 1920f, 1f)) {
            content()
        }
    }
}

/** Translucent panel with a lighter top edge, used for the dock, buttons and cards. */
fun Modifier.glass(shape: Shape, fill: Color = Color.White.copy(alpha = 0.14f)): Modifier =
    this
        .clip(shape)
        .background(fill)
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.08f))),
            shape,
        )

/** A remote-focusable surface that turns white when focused, like the Settings panel's buttons. */
@Composable
fun GlassButton(
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleFill: Color = Color.White.copy(alpha = 0.10f),
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Color.White else idleFill)
            .then(if (!focused && idleFill != Color.Transparent) Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

/** The Settings panel's container: dark glass, top-edge highlight. */
fun Modifier.panelGlass(shape: Shape): Modifier = glass(shape, Color(0xFF242428).copy(alpha = 0.94f))

val TextSecondary = Color(0xFFEBEBF5).copy(alpha = 0.62f)
val LiveRed = Color(0xFFE91916)

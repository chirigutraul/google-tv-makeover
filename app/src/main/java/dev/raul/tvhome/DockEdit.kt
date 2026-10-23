package dev.raul.tvhome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val MenuRed = Color(0xFFFF6961)

/** Places a box of [width] so it is centred over [centerX] (px, screen coordinates), above the dock. */
@Composable
private fun AboveTile(centerX: Float, width: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val half = with(LocalDensity.current) { (width / 2).toPx() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .padding(bottom = 264.dp)
                .offset { IntOffset((centerX - half).roundToInt(), 0) }
                .width(width)
        ) { content() }
    }
}

/** The menu that opens above a dock tile when you hold OK on it. */
@Composable
fun TileMenu(
    label: String,
    centerX: Float,
    canRemove: Boolean,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        delay(60) // wait for the menu to be laid out before moving focus into it
        runCatching { first.requestFocus() }
    }

    AboveTile(centerX, 420.dp) {
        AnimatedVisibility(
            visible = shown,
            enter = scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn(tween(180)),
            exit = scaleOut(tween(150), targetScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(tween(150)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(40.dp, RoundedCornerShape(44.dp))
                    .panelGlass(RoundedCornerShape(44.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(
                    label,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
                )
                Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.08f)).padding(8.dp)) {
                    MenuRow("Move", { c -> MoveArrowsIcon(c, 26.dp) }, Color.White, Modifier.focusRequester(first), onMove)
                    if (canRemove) {
                        Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        MenuRow("Remove from dock", { c -> MinusCircleIcon(c, 26.dp) }, MenuRed, Modifier, onRemove)
                    }
                }
                GlassButton(
                    shape = RoundedCornerShape(28.dp),
                    onClick = onCancel,
                    idleFill = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                ) { focused ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BasicText("Cancel", style = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: @Composable (Color) -> Unit,
    ink: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    GlassButton(
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        idleFill = Color.Transparent,
        modifier = modifier.fillMaxWidth().height(76.dp),
    ) { focused ->
        val c = if (focused) (if (ink == MenuRed) Color(0xFFD70015) else Color.Black) else ink
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            icon(c)
            BasicText(label, style = TextStyle(color = c, fontSize = 25.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/** The "‹ Move ›" hint above a tile being moved. */
@Composable
fun MovePill(centerX: Float) {
    AboveTile(centerX, 280.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(30.dp, RoundedCornerShape(36.dp))
                .panelGlass(RoundedCornerShape(36.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PillArrow(left = true)
            BasicText("Move", style = TextStyle(color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold))
            PillArrow(left = false)
        }
    }
}

@Composable
private fun PillArrow(left: Boolean) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = if (left) 180f else 0f }) { ChevronIcon(Color.White, 24.dp) }
    }
}

/** The dashed "Add app" slot at the end of the dock while editing. */
@Composable
fun AddSlot(modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .height(128.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val s = if (focused) 1.1f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .border(2.5.dp, Color.White.copy(alpha = if (focused) 1f else 0.55f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ink = if (focused) Color.Black else Color.White
        PlusIcon(ink, 36.dp)
        Spacer(Modifier.height(8.dp))
        BasicText("Add app", maxLines = 1, style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** The sheet listing apps that aren't in the dock yet. */
@Composable
fun AddSheet(open: Boolean, apps: List<DockApp>, used: Int, onAdd: (String) -> Unit, onClose: () -> Unit) {
    AnimatedVisibility(visible = open, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.94f) + fadeIn(tween(250)),
        exit = scaleOut(tween(200), targetScale = 0.96f) + fadeOut(tween(200)),
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { first.requestFocus() }
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(start = 208.dp, top = 96.dp)
                    .width(1440.dp)
                    .height(680.dp)
                    .shadow(40.dp, RoundedCornerShape(52.dp))
                    .panelGlass(RoundedCornerShape(52.dp))
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText("Add to dock", style = TextStyle(color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp))
                        BasicText(
                            "$used of $MAX_DOCK spots used. Pick an app to add it at the end.",
                            style = TextStyle(color = TextSecondary, fontSize = 26.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    GlassButton(shape = RoundedCornerShape(36.dp), onClick = onClose, idleFill = Color.White.copy(alpha = 0.14f), modifier = Modifier.height(72.dp).width(150.dp)) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            BasicText("Done", style = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                if (apps.isEmpty()) {
                    BasicText("Every app on this TV is already in the dock.", style = TextStyle(color = TextSecondary, fontSize = 26.sp))
                } else {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(36.dp),
                    ) {
                        apps.chunked(5).forEachIndexed { row, chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                chunk.forEachIndexed { col, app ->
                                    AddTile(
                                        app = app,
                                        modifier = Modifier.weight(1f).then(if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier),
                                        onClick = { onAdd(app.packageName) },
                                    )
                                }
                                repeat(5 - chunk.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTile(app: DockApp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .graphicsLayer {
                    val s = if (focused) 1.08f else 1f
                    scaleX = s
                    scaleY = s
                    translationY = if (focused) -10.dp.toPx() else 0f
                }
                .shadow(if (focused) 28.dp else 4.dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A40), Color(0xFF16161A)))),
            contentAlignment = Alignment.Center,
        ) {
            val art = app.art
            when {
                art != null && app.artIsBanner -> Image(art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                art != null -> Image(art, app.label, Modifier.size(80.dp))
                else -> BasicText(app.label.take(2), style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold))
            }
            if (focused) {
                Box(Modifier.fillMaxSize().border(3.dp, Color.White.copy(alpha = 0.9f), shape))
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { PlusIcon(Color.Black, 24.dp) }
            }
        }
        BasicText(
            app.label,
            modifier = Modifier.padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (focused) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

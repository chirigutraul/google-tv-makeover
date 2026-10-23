package dev.raul.tvhome

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** No button pressed for this long hides everything but the photo. */
private const val IDLE_MS = 120_000L
/** How long each background photo stays before the next fades in. */
private const val PHOTO_MS = 90_000L
/** Holding OK this long on a dock tile starts editing the dock. */
private const val LONG_PRESS_MS = 500L

private enum class Area { Dock, Card, Settings }

/** Tracks one OK press so a hold can be told apart from a tap. */
private class PressTracker {
    var start = 0L
    var longFired = false
}

@Composable
fun HomeScreen(
    apps: List<DockApp>,
    allApps: List<DockApp>,
    onDockChange: (List<String>) -> Unit,
    twitch: TwitchRepo,
    wakeSignal: Int,
) {
    val context = LocalContext.current
    var selected by remember { mutableIntStateOf(0) }
    var area by remember { mutableStateOf(Area.Dock) }
    var settingsOpen by remember { mutableStateOf(false) }
    var idle by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val twitchState by twitch.state.collectAsState()

    // Dock editing
    var editMode by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var moving by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val press = remember { PressTracker() }
    val tileCenters = remember { mutableStateMapOf<Int, Float>() }

    val dockPackages = apps.map { it.packageName }
    val dockFocus = remember(apps.size) { List(apps.size) { FocusRequester() } }
    val cardFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    var pendingFocus by remember { mutableStateOf<Int?>(null) }

    if (apps.isNotEmpty() && selected > apps.lastIndex) selected = apps.lastIndex
    val current = apps.getOrNull(selected)
    val onTwitch = current?.packageName == TWITCH_PACKAGE
    val showLive = onTwitch && !editMode && twitchState.live.isNotEmpty()
    val overlayOpen = menuFor != null || addOpen || settingsOpen

    LaunchedEffect(pendingFocus, apps.size) {
        val i = pendingFocus ?: return@LaunchedEffect
        delay(16) // let the dock recompose with its new order first
        if (apps.isNotEmpty()) runCatching { dockFocus[i.coerceIn(0, apps.lastIndex)].requestFocus() }
        pendingFocus = null
    }

    fun exitEdit() {
        editMode = false
        menuFor = null
        moving = false
        addOpen = false
        pendingFocus = selected
    }

    fun moveTile(from: Int, to: Int) {
        if (to !in apps.indices) return
        val list = dockPackages.toMutableList()
        list.add(to, list.removeAt(from))
        onDockChange(list)
        selected = to
        pendingFocus = to
    }

    fun removeTile(i: Int) {
        menuFor = null
        if (apps.size <= 1) return
        onDockChange(dockPackages.filterIndexed { j, _ -> j != i })
        selected = i.coerceAtMost(apps.size - 2)
        pendingFocus = selected
    }

    // Pressing Home while already home: wake up, close everything, go back to the dock.
    LaunchedEffect(wakeSignal) {
        idle = false
        settingsOpen = false
        editMode = false
        menuFor = null
        moving = false
        addOpen = false
        lastInput = SystemClock.uptimeMillis()
        pendingFocus = selected
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (!overlayOpen && !editMode && SystemClock.uptimeMillis() - lastInput > IDLE_MS) idle = true
        }
    }
    LaunchedEffect(onTwitch) { if (onTwitch) twitch.refreshIfStale() }
    LaunchedEffect(showLive) { if (!showLive && area == Area.Card) area = Area.Dock }

    BackHandler(enabled = true) {
        when {
            addOpen -> {
                addOpen = false
                runCatching { addFocus.requestFocus() }
            }
            menuFor != null -> {
                menuFor = null
                pendingFocus = selected
            }
            moving -> moving = false
            editMode -> exitEdit()
            settingsOpen -> settingsOpen = false
            else -> pendingFocus = selected
        }
    }

    val uiAlpha by animateFloatAsState(if (idle) 0f else 1f, tween(900), label = "ui")
    // The tint behind the dock fades between app colours instead of jumping.
    val accent by animateColorAsState(
        if (editMode) Color.Transparent else current?.accent ?: Color.Transparent,
        tween(500, easing = FastOutSlowInEasing),
        label = "accent",
    )

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput = SystemClock.uptimeMillis()
                if (idle) {
                    idle = false
                    true // the first press only wakes the screen
                } else {
                    false
                }
            }
    ) {
        PhotoBackground(idle = idle)

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = uiAlpha }) {
            // Shade for legibility, plus a tint in the selected app's colour rising behind the dock.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f), 0.22f to Color.Transparent,
                            0.48f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f),
                        )
                    )
                    // Same as the design: a flat ellipse (60% wide, 45% tall) at the bottom edge,
                    // fading out by 70% of its radius, so only the area around the dock is tinted.
                    val rx = size.width * 0.6f
                    val ry = size.height * 0.45f
                    val bottom = Offset(size.width / 2, size.height)
                    scale(scaleX = 1f, scaleY = ry / rx, pivot = bottom) {
                        val h = size.height * rx / ry
                        drawRect(
                            Brush.radialGradient(
                                0f to accent.copy(alpha = accent.alpha * 0.35f),
                                0.7f to Color.Transparent,
                                center = bottom,
                                radius = rx,
                            ),
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h),
                        )
                    }
                }
            )

            Clock(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 96.dp))

            val nameBottom by animateDpAsState(
                when {
                    showLive -> 772.dp
                    editMode -> 760.dp // above the tile menu
                    else -> 272.dp
                },
                tween(400, easing = FastOutSlowInEasing), label = "nameBottom",
            )
            val title = if (editMode) "Edit dock" else current?.label.orEmpty()
            val line = when {
                !editMode -> current?.let { taglineFor(it, onTwitch, twitchState) }.orEmpty()
                moving -> "Use left and right to choose a spot for ${current?.label}, then press OK"
                menuFor != null -> "Choose what to do with ${apps.getOrNull(menuFor!!)?.label}"
                else -> "Press OK on an app to move or remove it"
            }
            // Name and tagline cross-fade with a small rise when the selection changes.
            AnimatedContent(
                targetState = title to line,
                // Moved with offset { } so the slide only re-places the text instead of recomposing the screen each frame.
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 208.dp)
                    .offset { IntOffset(0, -nameBottom.roundToPx()) },
                transitionSpec = {
                    (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 })
                        .togetherWith(fadeOut(tween(160)))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.BottomStart,
                label = "name",
            ) { (t, l) ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        t,
                        style = TextStyle(color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-3.5).sp, lineHeight = 92.sp),
                    )
                    BasicText(l, style = TextStyle(color = Color.White.copy(alpha = 0.78f), fontSize = 28.sp, fontWeight = FontWeight.Medium))
                }
            }

            // Live streamers: one card each, left to right; ← → scroll through them when there are many.
            AnimatedVisibility(
                visible = showLive,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 268.dp),
                enter = fadeIn(tween(350)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 10 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 14 },
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 208.dp, end = 208.dp, top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    itemsIndexed(twitchState.live, key = { _, s -> s.login }) { i, stream ->
                        LiveCard(
                            stream = stream,
                            width = 760.dp,
                            modifier = Modifier
                                .animateItem()
                                .then(if (i == 0) Modifier.focusRequester(cardFocus) else Modifier)
                                .focusProperties {
                                    down = dockFocus.getOrNull(selected) ?: FocusRequester.Default
                                    up = FocusRequester.Cancel
                                    canFocus = !overlayOpen
                                }
                                .onFocusChanged { if (it.isFocused) area = Area.Card },
                            onOpen = { openTwitchStream(context, stream.login) },
                        )
                    }
                }
            }

            // The dock
            val showAdd = editMode && apps.size < MAX_DOCK
            val addWeight by animateFloatAsState(if (showAdd) 1f else 0.001f, tween(350, easing = FastOutSlowInEasing), label = "addSlot")
            // The glass sits behind the tiles as its own layer, so an enlarged tile can grow past its edge.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 208.dp, bottom = 72.dp)
                    .width(1440.dp)
            ) {
            Box(Modifier.matchParentSize().glass(RoundedCornerShape(44.dp)))
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(if (editMode) 18.dp else 22.dp),
            ) {
                apps.forEachIndexed { i, app ->
                    val active = menuFor == i || (moving && i == selected)
                    DockTile(
                        app = app,
                        selected = i == selected,
                        focused = i == selected && area == Area.Dock && !overlayOpen && !editMode,
                        editing = editMode,
                        active = active,
                        lifted = moving && i == selected,
                        wigglePhase = i % 2 == 1,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { c -> tileCenters[i] = c.positionInRoot().x + c.size.width / 2f }
                            .focusRequester(dockFocus[i])
                            .focusProperties {
                                canFocus = !overlayOpen
                                up = if (showLive && i == selected) cardFocus else FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                if (i == apps.lastIndex) right = if (showAdd) addFocus else settingsFocus
                            }
                            .onFocusChanged {
                                if (it.isFocused) {
                                    selected = i
                                    area = Area.Dock
                                }
                            }
                            .onPreviewKeyEvent { e ->
                                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                                if (moving && i == selected) {
                                    if (e.type == KeyEventType.KeyDown) {
                                        when (e.key) {
                                            Key.DirectionLeft -> moveTile(i, i - 1)
                                            Key.DirectionRight -> moveTile(i, i + 1)
                                            else -> {}
                                        }
                                    }
                                    if (ok && e.type == KeyEventType.KeyUp) moving = false
                                    return@onPreviewKeyEvent e.key != Key.Back
                                }
                                if (!ok) return@onPreviewKeyEvent false
                                when (e.type) {
                                    KeyEventType.KeyDown -> {
                                        val now = SystemClock.uptimeMillis()
                                        if (e.nativeKeyEvent.repeatCount == 0) {
                                            press.start = now
                                            press.longFired = false
                                        } else if (!press.longFired && (e.nativeKeyEvent.isLongPress || now - press.start >= LONG_PRESS_MS)) {
                                            press.longFired = true
                                            editMode = true
                                            menuFor = i
                                        }
                                        true
                                    }
                                    KeyEventType.KeyUp -> {
                                        if (!press.longFired) {
                                            if (editMode) menuFor = i else context.startActivity(app.launch)
                                        }
                                        press.longFired = false
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
                if (addWeight > 0.01f) {
                    AddSlot(
                        modifier = Modifier
                            .weight(addWeight)
                            .graphicsLayer { alpha = addWeight }
                            .focusRequester(addFocus)
                            .focusProperties {
                                canFocus = showAdd && !overlayOpen
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                right = settingsFocus
                            },
                        onClick = { addOpen = true },
                    )
                }
            }
            }

            SettingsButton(
                editMode = editMode,
                focused = area == Area.Settings && !overlayOpen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 96.dp, bottom = if (editMode) 116.dp else 100.dp)
                    .focusRequester(settingsFocus)
                    .focusProperties {
                        canFocus = !overlayOpen
                        left = if (showAdd) addFocus else dockFocus.lastOrNull() ?: FocusRequester.Default
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .onFocusChanged { if (it.isFocused) area = Area.Settings },
                onClick = { if (editMode) exitEdit() else settingsOpen = true },
            )

            menuFor?.let { i ->
                val app = apps.getOrNull(i)
                if (app != null) {
                    TileMenu(
                        label = app.label,
                        centerX = tileCenters[i] ?: 0f,
                        canRemove = apps.size > 1,
                        onMove = {
                            menuFor = null
                            moving = true
                            selected = i
                            pendingFocus = i
                        },
                        onRemove = { removeTile(i) },
                        onCancel = {
                            menuFor = null
                            pendingFocus = i
                        },
                    )
                }
            }
            if (moving) {
                MovePill(centerX = tileCenters[selected] ?: 0f)
            }
        }

        AnimatedVisibility(visible = idle, enter = fadeIn(tween(900)), exit = fadeOut(tween(300))) {
            Box(Modifier.fillMaxSize()) {
                Clock(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 96.dp))
            }
        }

        AddSheet(
            open = addOpen,
            apps = allApps.filter { it.packageName !in dockPackages },
            used = apps.size,
            onAdd = { pkg ->
                onDockChange(dockPackages + pkg)
                addOpen = false
                selected = apps.size
                pendingFocus = apps.size
            },
            onClose = {
                addOpen = false
                runCatching { addFocus.requestFocus() }
            },
        )

        SettingsPanel(
            open = settingsOpen,
            onClose = {
                settingsOpen = false
                runCatching { settingsFocus.requestFocus() }
            },
        )
    }

    // Start with the first dock tile focused, once the first frame is on screen.
    LaunchedEffect(Unit) {
        delay(300)
        pendingFocus = selected
    }
}

private fun taglineFor(app: DockApp, onTwitch: Boolean, state: TwitchState): String {
    if (!onTwitch || !state.configured || !state.loaded) return app.tagline
    val total = FAVOURITE_STREAMERS.size
    val live = state.live.size
    return if (live == 1) "1 of your $total streamers is live" else "$live of your $total streamers are live"
}

@Composable
private fun Clock(modifier: Modifier) {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var now by remember { mutableStateOf(LocalTime.now().format(fmt)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now().format(fmt)
            delay(10_000)
        }
    }
    BasicText(
        now,
        modifier = modifier,
        style = TextStyle(color = Color.White.copy(alpha = 0.95f), fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    )
}

/** Full-screen photo that drifts slowly and fades to the next one every [PHOTO_MS]. */
@Composable
private fun PhotoBackground(idle: Boolean) {
    val context = LocalContext.current
    val photos = remember { listPhotos() }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(photos.size) {
        if (photos.size < 2) return@LaunchedEffect
        while (true) {
            delay(PHOTO_MS)
            index = (index + 1) % photos.size
        }
    }
    val source = photos[index % photos.size]
    val bitmap by produceState<ImageBitmap?>(null, source) {
        value = withContext(Dispatchers.IO) { decodePhoto(context, source) }
    }

    // The slow drift only runs in screensaver mode. While you're using the dock the photo holds
    // still, so the TV only redraws when something actually moves (it can't keep up with both).
    val drift = remember { Animatable(0f) }
    LaunchedEffect(idle) {
        if (!idle) return@LaunchedEffect // leaving idle cancels this effect, which freezes the drift where it is
        while (true) {
            drift.animateTo(1f, tween(((1f - drift.value) * 60_000).toInt().coerceAtLeast(1), easing = LinearEasing))
            drift.animateTo(0f, tween(60_000, easing = LinearEasing))
        }
    }
    val t by drift.asState()

    Box(Modifier.fillMaxSize()) {
        // Shown until photos are pushed to the TV (see scripts/push-screensavers.sh).
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B2230), Color(0xFF0B0B0F)))))
        Crossfade(targetState = bitmap, animationSpec = tween(1500), label = "photo") { img ->
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val s = 1.06f + 0.08f * t
                        scaleX = s
                        scaleY = s
                        translationX = -36.dp.toPx() * t
                        translationY = -14.dp.toPx() * t
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = idle,
            enter = fadeIn(tween(900)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            BasicText(
                source.caption,
                modifier = Modifier.padding(start = 112.dp, bottom = 88.dp),
                style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun DockTile(
    app: DockApp,
    selected: Boolean,
    focused: Boolean,
    editing: Boolean,
    active: Boolean,
    lifted: Boolean,
    wigglePhase: Boolean,
    modifier: Modifier,
) {
    val scale by animateFloatAsState(
        when {
            lifted -> 1.14f
            active -> 1.1f
            focused -> 1.16f
            editing && selected -> 1.08f
            selected -> 1.04f
            else -> 1f
        },
        tween(260, easing = FastOutSlowInEasing), label = "scale",
    )
    val lift by animateDpAsState(
        when {
            lifted -> (-22).dp
            active -> (-10).dp
            focused -> (-14).dp
            selected -> (-4).dp
            else -> 0.dp
        },
        tween(260, easing = FastOutSlowInEasing), label = "lift",
    )
    val tilt by animateFloatAsState(if (focused) 6f else 0f, tween(260, easing = FastOutSlowInEasing), label = "tilt")
    val dim by animateFloatAsState(if (editing && !active && !selected) 0.6f else 1f, tween(250), label = "dim")
    // Only runs while editing, so the dock doesn't redraw every frame the rest of the time.
    val wiggle = if (editing && !active && !selected) rememberWiggle() else null
    val shape = RoundedCornerShape(26.dp)
    val fill = remember(app.accent) { Brush.verticalGradient(listOf(lerpWhite(app.accent, 0.15f), app.accent.darken(0.35f))) }

    Box(
        modifier
            .height(128.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift.toPx()
                rotationX = tilt
                rotationZ = wiggle?.value?.let { if (wigglePhase) -it else it } ?: 0f
                alpha = dim
                cameraDistance = 12 * density
            }
            .shadow(if (focused || active) 30.dp else 6.dp, shape)
            .clip(shape)
            .background(fill)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        val art = app.art
        when {
            art != null && app.artIsBanner -> Image(art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            art != null -> Image(art, app.label, Modifier.size(72.dp))
            else -> BasicText(app.label, style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        }
        // Top shine on every tile; sheen and edge when focused; a white ring while being edited.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.18f), 0.46f to Color.Transparent)))
        when {
            active || (editing && selected) -> Box(Modifier.fillMaxSize().border(3.dp, Color.White.copy(alpha = 0.9f), shape))
            focused -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.34f), 0.38f to Color.White.copy(alpha = 0.06f), 0.6f to Color.Transparent))
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), shape)
            )
            selected -> Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.5f), shape))
        }
    }
}

@Composable
private fun SettingsButton(editMode: Boolean, focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(200), label = "gear")
    val fill = if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (editMode) Modifier.height(76.dp).glass(RoundedCornerShape(38.dp), fill).padding(horizontal = 36.dp) else Modifier.size(108.dp).glass(CircleShape, fill))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (editMode) {
            BasicText("Done", style = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold))
        } else {
            GearIcon(color = if (focused) Color.Black else Color.White, size = 44.dp)
        }
    }
}

@Composable
private fun LiveCard(stream: LiveStream, width: Dp, modifier: Modifier, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, tween(260, easing = FastOutSlowInEasing), label = "card")
    val lift by animateDpAsState(if (focused) (-10).dp else 0.dp, tween(260, easing = FastOutSlowInEasing), label = "cardLift")
    val shape = RoundedCornerShape(36.dp)

    Column(
        modifier
            .width(width)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift.toPx()
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .shadow(if (focused) 40.dp else 24.dp, shape)
            .glass(shape, Color(0xFF1C1C20).copy(alpha = 0.55f))
            .then(if (focused) Modifier.border(4.dp, Color.White.copy(alpha = 0.92f), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((width - 40.dp) * 9 / 16)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF18181B))
        ) {
            stream.thumbnail?.let {
                Image(it, "Stream preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.38f to Color.Transparent, 0.66f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.88f))
                )
            )
            Pill("LIVE", LiveRed, Modifier.align(Alignment.TopStart).padding(18.dp), dot = true)
            Pill("${formatViewers(stream.viewers)} watching", Color.Black.copy(alpha = 0.55f), Modifier.align(Alignment.TopEnd).padding(18.dp))
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 22.dp).fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(stream.avatar, stream.displayName, 52.dp)
                        Column {
                            BasicText(stream.displayName, style = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
                            if (stream.game.isNotBlank()) {
                                BasicText(stream.game, style = TextStyle(color = Color(0xFFEBEBF5).copy(alpha = 0.72f), fontSize = 19.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                    BasicText(
                        stream.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Color.White.copy(alpha = 0.94f), fontSize = 25.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
                    )
                }
                if (focused) {
                    Row(
                        Modifier.height(52.dp).clip(CircleShape).background(Color.White).padding(start = 18.dp, end = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PlayIcon(Color.Black, 20.dp)
                        BasicText("Watch", style = TextStyle(color = Color.Black, fontSize = 21.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, fill: Color, modifier: Modifier, dot: Boolean = false) {
    Row(
        modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(fill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot) Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White))
        BasicText(text, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Avatar(image: ImageBitmap?, name: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .border(3.dp, LiveRed, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Color(0xFF2A2A30)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BasicText(name.take(1).uppercase(), style = TextStyle(color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun rememberWiggle(): State<Float> =
    rememberInfiniteTransition(label = "wiggle").animateFloat(
        -1.2f, 1.2f,
        infiniteRepeatable(tween(160, easing = LinearEasing), RepeatMode.Reverse),
        label = "angle",
    )

private fun formatViewers(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 1_000 -> String.format("%.1fK", n / 1_000f)
    else -> n.toString()
}

private fun lerpWhite(c: Color, amount: Float) = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
)

private fun Color.darken(amount: Float) = Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

package com.studio72f.eigen.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio72f.eigen.CategoryRules
import com.studio72f.eigen.MainActivity
import com.studio72f.eigen.ai.EigenAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.GlassContent
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinMotion
import studio72f.kelvin.glass
import studio72f.kelvin.litGlass
import kotlin.math.roundToInt

private val CardShape = ContinuousShape(16.dp)
private const val DOCK_SIZE = 4

/**
 * Home: the centred clock, the drawers as large centred names that open in place into vertical cards (several at
 * once), the dock, and one glass search capsule at the bottom. Pull down to open or close every drawer; pull up, or
 * drag the line above the dock, for the A–Z app drawer. Hold empty space or an app for pill actions at the finger.
 */
@Composable
fun HomePage(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spatial = Kelvin.motion.spatial<Float>()
    // Usage minutes reload whenever home comes back.
    LaunchedEffect(state.allApps) { withContext(Dispatchers.Default) { prewarmIcons(state.allApps) } }
    LaunchedEffect(state.resumeCount) {
        val (access, minutes) = withContext(Dispatchers.IO) { Usage.hasAccess(context) to Usage.todayMinutes(context) }
        state.usageAccess = access; state.usageMinutes = minutes
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val downThreshold = with(density) { 96.dp.toPx() }
        val upThreshold = with(density) { 110.dp.toPx() }
        // How far the finger has pulled past either end of home: + down (open or close all), − up (app drawer).
        var pull by remember { mutableFloatStateOf(0f) }
        val drawer = remember { Animatable(0f) } // 0 closed … 1 open
        LaunchedEffect(state.appDrawerOpen) { drawer.animateTo(if (state.appDrawerOpen) 1f else 0f, spatial) }
        LaunchedEffect(drawer) { androidx.compose.runtime.snapshotFlow { drawer.value }.collect { state.drawerProgress = it } }

        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        var armed by remember { mutableStateOf(false) }
        fun onPull(p: Float) {
            pull = p
            // A tick as the pull crosses the point where letting go will act.
            val nowArmed = p > downThreshold || p < -upThreshold
            if (nowArmed != armed) { armed = nowArmed; if (nowArmed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate) }
            if (p <= 0f && !state.appDrawerOpen) scope.launch { drawer.snapTo((-p / heightPx).coerceIn(0f, 1f)) }
        }
        fun onRelease(velocity: Float) {
            val p = pull
            when {
                p > downThreshold -> {
                    // Drawers open one after another, top to bottom, and close in reverse.
                    val names = state.drawers.map { it.name }
                    val closing = state.openDrawers.containsAll(names)
                    scope.launch {
                        state.bulkOpening = true
                        if (closing) for (n in names.reversed()) { state.openDrawers = state.openDrawers - n; delay(32) }
                        else for (n in names) { state.openDrawers = state.openDrawers + n; delay(48) }
                        delay(400); state.bulkOpening = false
                    }
                }
                p < 0f && (-p > upThreshold || velocity < -1800f) -> state.appDrawerOpen = true
                p < 0f -> scope.launch { drawer.animateTo(0f, spatial) }
            }
            scope.launch { animate(pull, 0f, animationSpec = spatial) { v, _ -> pull = v } }
        }
        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val p = pull
                    if ((p > 0f && available.y < 0f) || (p < 0f && available.y > 0f)) {
                        val next = if (p > 0f) (p + available.y).coerceAtLeast(0f) else (p + available.y).coerceAtMost(0f)
                        onPull(next); return Offset(0f, next - p)
                    }
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                    onPull(pull + available.y); return Offset(0f, available.y)
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (pull == 0f) return Velocity.Zero
                    onRelease(available.y); return available
                }
            }
        }

        // The drawer rises behind the dock and search capsule, which stay on top as in Ratio; home fades out above it.
        if (drawer.value > 0f || state.appDrawerOpen) AppDrawer(state, host, backdrop, drawer.value, heightPx) { closing, v ->
            if (closing == null) scope.launch { drawer.snapTo(v) } else if (closing) state.appDrawerOpen = false else scope.launch { drawer.animateTo(1f, spatial) }
        }
        // Home's content runs the full height and scrolls under the handle, dock and search capsule, which float on top.
        var barHeight by remember { mutableFloatStateOf(0f) }
        val bottomInset = with(density) { barHeight.toDp() }
        Box(Modifier.fillMaxSize().imePadding()) {
            val gentle = Kelvin.motion.gentle<Float>()
            AnimatedContent(
                state.searching, Modifier.fillMaxSize().statusBarsPadding().graphicsLayer { alpha = 1f - drawer.value },
                transitionSpec = { (fadeIn(gentle) togetherWith fadeOut(gentle)).using(SizeTransform(clip = false)) },
                label = "home or search",
            ) { searching ->
                if (searching) SearchResults(state, host, backdrop, bottomInset)
                else if (state.appDrawerOpen) Spacer(Modifier.fillMaxSize()) // the open drawer takes the touches
                else Drawers(state, host, Modifier.nestedScroll(connection), pull, downThreshold, backdrop, bottomInset)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onGloballyPositioned { barHeight = it.size.height.toFloat() }.navigationBarsPadding()) {
            if (!state.searching) {
                // The line bends into an up chevron as you pull up; drag or tap it for the app drawer.
                val bend = if (state.appDrawerOpen) 1f else (-pull / upThreshold).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .draggable(rememberDraggableState { d -> onPull(pull + d) }, Orientation.Vertical, onDragStopped = { v -> onRelease(v) })
                        .eigenPress({ state.appDrawerOpen = true }, label = "All apps").semantics { contentDescription = "All apps" },
                    contentAlignment = Alignment.Center,
                ) { Handle(bend) }
                Dock(state, host, backdrop)
            }
            SearchCapsule(state, host, backdrop, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp))
            }
        }
        PillMenuOverlay(state, host, backdrop)
    }
}

/** A short line that bends into an up chevron as [bend] goes 0 → 1. */
@Composable
private fun Handle(bend: Float) {
    val ink = Kelvin.colors.ink.copy(alpha = 0.5f + 0.3f * bend)
    Canvas(Modifier.size(40.dp, 14.dp)) {
        val w = size.width; val h = size.height; val mid = h * (0.55f + 0.2f * bend)
        val path = Path().apply { moveTo(w * 0.1f, mid); lineTo(w / 2f, mid - h * 0.55f * bend); lineTo(w * 0.9f, mid) }
        drawPath(path, ink, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun Drawers(state: EigenState, host: EigenHost, modifier: Modifier, pull: Float, threshold: Float, backdrop: KelvinBackdrop, bottomInset: androidx.compose.ui.unit.Dp) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Coming back from an app, home settles in: a touch small and soft, easing to rest.
    val settle = remember { Animatable(1f) }
    val spatial = Kelvin.motion.spatial<Float>()
    LaunchedEffect(state.returnPulse) { if (state.returnPulse > 0) { settle.snapTo(0f); settle.animateTo(1f, spatial) } }
    val allOpen = state.drawers.isNotEmpty() && state.openDrawers.containsAll(state.drawers.map { it.name })
    val density = LocalDensity.current
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var clockHeight by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { at -> state.pillMenu = homeMenu(state, host, origin.x + at.x, origin.y + at.y) }) }
            .then(modifier),
    ) {
        // A rubber-band pull with a quiet hint of what releasing will do.
        val shift = if (pull > 0f) KelvinMotion.rubberBand(pull, threshold * 2f) else 0f
        if (pull > 0f) KText(
            if (allOpen) "Release to close all" else "Release to open all",
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = (pull / threshold).coerceIn(0f, 1f) },
            Kelvin.type.label, Kelvin.colors.ink2,
        )
        val drawers = state.drawers.filter { it.apps.isNotEmpty() }
        // The drawer names sit in the optical centre below the clock while closed; opening drawers grows the list down.
        val titleHeight = 58.dp
        val centreGap = with(density) { ((maxHeight - bottomInset).toPx() - clockHeight - drawers.size * titleHeight.toPx()) / 2f }.coerceAtLeast(with(density) { 24.dp.toPx() })
        // Lazy, so only the cards on screen are built: opening every drawer stays smooth.
        LazyColumn(
            Modifier.fillMaxSize().graphicsLayer {
                translationY = shift
                val v = settle.value; scaleX = 0.94f + 0.06f * v; scaleY = scaleX; alpha = 0.4f + 0.6f * v
            },
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomInset + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "clock") {
                var clockBounds by remember { mutableStateOf(Rect.Zero) }
                HomeClock(
                    state.clockStyle, onTime = host::openAlarms, onDate = host::openCalendar,
                    modifier = Modifier.padding(top = 28.dp).onGloballyPositioned { clockBounds = it.boundsInRoot(); clockHeight = it.size.height.toFloat() },
                    onLongPress = { state.pillMenu = PillMenu(clockBounds.center.x - 80f, clockBounds.bottom, listOf("Change style" to { state.clockSheet = true }, "Launcher settings" to host::openSettings)) },
                )
            }
            item(key = "gap") { Spacer(Modifier.height(with(density) { centreGap.toDp() })) }
            var cardIndex = 0
            for (drawer in drawers) {
                val open = drawer.name in state.openDrawers
                item(key = "title:" + drawer.name) {
                    DrawerTitle(state, host, drawer, open, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = Kelvin.motion.spatial())) {
                        // A drawer opened near the bottom scrolls up so its cards show.
                        if (!open) scope.launch {
                            delay(120)
                            val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "title:" + drawer.name } ?: return@launch
                            if (info.offset > list.layoutInfo.viewportEndOffset * 0.55f) list.animateScrollToItem(info.index, -with(density) { 24.dp.roundToPx() })
                        }
                    }
                }
                if (open) {
                    val rows = rows(state, drawer.apps)
                    rows.forEachIndexed { r, row ->
                        val start = cardIndex
                        item(key = "row:" + drawer.name + ":" + r) {
                            CardRow(state, host, row, start, !state.bulkOpening, backdrop,
                                Modifier.animateItem(fadeInSpec = Kelvin.motion.gentle(), fadeOutSpec = Kelvin.motion.gentle(), placementSpec = Kelvin.motion.spatial()))
                        }
                        cardIndex += row.size
                    }
                    item(key = "end:" + drawer.name) { Spacer(Modifier.height(12.dp).animateItem()) }
                }
            }
        }
    }
}

/** One row of cards in an open drawer (a wide card takes two of the row's units). */
@Composable
private fun CardRow(state: EigenState, host: EigenHost, row: List<MainActivity.App>, start: Int, animate: Boolean, backdrop: KelvinBackdrop?, modifier: Modifier) {
    val columns = state.columns
    Row(modifier.fillMaxWidth()) {
        var used = 0
        row.forEachIndexed { i, app ->
            val wide = app.id in state.wideCards
            AppCard(state, host, app, start + i, Modifier.weight(if (wide) 2f else 1f), animate = animate, backdrop = backdrop)
            used += if (wide) 2 else 1
        }
        if (used < columns) Spacer(Modifier.weight((columns - used).toFloat()))
    }
}

private fun homeMenu(state: EigenState, host: EigenHost, x: Float, y: Float) = PillMenu(x, y, listOf(
    "Add drawer" to host::addDrawer,
    "Settings" to host::openSettings,
    "Wallpaper" to host::openWallpaper,
    "Widgets" to { state.showPage(EigenState.PAGE_WIDGETS) },
))

private fun appMenu(state: EigenState, host: EigenHost, app: MainActivity.App, bounds: Rect) = PillMenu(bounds.left, bounds.top, listOf(
    "Details" to { state.dashboardApp = app },
    (if (state.dockIds.contains(app.id)) "Remove from dock" else "Add to dock") to { host.toggleDock(app) },
    (if (host.isFavorite(app)) "Remove from favourites" else "Add to favourites") to { host.toggleFavorite(app) },
    "Move to drawer" to { host.moveApp(app) },
    "More" to { host.appMenu(app) },
), app, bounds)

@Composable
private fun DrawerTitle(state: EigenState, host: EigenHost, drawer: Drawer, open: Boolean, modifier: Modifier, onOpening: () -> Unit) {
    val c = Kelvin.colors
    val locale = LocalConfiguration.current.locales[0]
    val unread = remember(state.dotsVersion, drawer) { drawer.apps.any(host::hasDot) }
    val showUnread = unread && !open && state.dotStyle != "off"
    val base = if (state.openDrawers.isEmpty() || open) c.ink else c.ink.copy(alpha = 0.42f)
    val ink by animateColorAsState(if (showUnread && state.dotStyle == "colour") c.accent else base, Kelvin.motion.gentle(), label = "drawer ink")
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.onGloballyPositioned { titleBounds = it.boundsInRoot() }
                .eigenPress(
                    onClick = { onOpening(); state.openDrawers = if (open) state.openDrawers - drawer.name else state.openDrawers + drawer.name },
                    onLongClick = { state.pillMenu = homeMenu(state, host, titleBounds.left, titleBounds.bottom) },
                    shape = CardShape, label = if (open) "Close" else "Open",
                ).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            KText(drawer.name.replaceFirstChar { it.titlecase(locale) }, style = Kelvin.type.display.copy(fontSize = 31.sp, lineHeight = 38.sp), color = ink, maxLines = 1, align = TextAlign.Center)
            // Unread: a short accent dash or a dot, just after the name.
            if (showUnread && state.dotStyle == "dash") Box(Modifier.padding(start = 6.dp, top = 9.dp).size(16.dp, 6.dp).clip(CapsuleShape).background(c.accent))
            if (showUnread && state.dotStyle == "dot") Box(Modifier.padding(start = 6.dp, top = 8.dp).size(8.dp).clip(CircleShape).background(c.accent))
        }
    }
}

/** Packs cards into rows of five units; a wide card takes two. */
private fun rows(state: EigenState, apps: List<MainActivity.App>): List<List<MainActivity.App>> {
    val COLUMNS = state.columns
    val out = ArrayList<List<MainActivity.App>>(); var row = ArrayList<MainActivity.App>(); var used = 0
    for (a in apps) {
        val units = if (a.id in state.wideCards) 2 else 1
        if (used + units > COLUMNS) { out += row; row = ArrayList(); used = 0 }
        row += a; used += units
    }
    if (row.isNotEmpty()) out += row
    return out
}

@Composable
private fun AppGrid(state: EigenState, host: EigenHost, apps: List<MainActivity.App>, animate: Boolean, backdrop: KelvinBackdrop? = null) {
    val COLUMNS = state.columns
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp)) {
        var index = 0
        for (row in rows(state, apps)) {
            Row(Modifier.fillMaxWidth()) {
                var used = 0
                for (app in row) {
                    val wide = app.id in state.wideCards
                    AppCard(state, host, app, index++, Modifier.weight(if (wide) 2f else 1f), animate = animate, backdrop = backdrop)
                    used += if (wide) 2 else 1
                }
                if (used < COLUMNS) Spacer(Modifier.weight((COLUMNS - used).toFloat()))
            }
        }
    }
}

/**
 * A card: icon over label (or side by side when wide) in its material: Frost (frosted glass), Stained (glass stained
 * with the accent) or Lit (lit glass). Today's minutes show under the name once usage access is granted. Cards rise in one after
 * another as a drawer opens.
 */
@Composable
fun AppCard(state: EigenState, host: EigenHost, app: MainActivity.App, index: Int, modifier: Modifier, animate: Boolean = false, interactive: Boolean = true, backdrop: KelvinBackdrop? = null) {
    val c = Kelvin.colors
    val reduce = Kelvin.reduceMotion
    val appear = remember { Animatable(if (reduce || !animate) 1f else 0f) }
    val spatial = Kelvin.motion.spatial<Float>()
    LaunchedEffect(Unit) { if (appear.value < 1f) { delay(minOf(index, 15) * 22L); appear.animateTo(1f, spatial) } }
    val dot = remember(state.dotsVersion, app) { host.hasDot(app) }
    val style = state.cardStyles[app.id] ?: "classic"
    val wide = app.id in state.wideCards
    val fill = when (style) { "marker" -> c.accent; "bright" -> c.ink.copy(alpha = 0.20f); else -> c.ink.copy(alpha = 0.08f) }
    val ink = if (style == "marker") c.onAccent else c.ink
    val minutes = state.usageMinutes[app.component.packageName] ?: 0
    // The card's place on screen, read only while drawing or on a tap, so a card moving (a drawer opening) redraws
    // its frost without recomposing.
    val boundsState = remember { mutableStateOf(Rect.Zero) }
    // Real Kelvin glass over the wallpaper: it refracts through its rim, frosts at the Glass level, leans toward the
    // finger and thaws where it is pressed. Marker is stained accent glass; Bright is lit glass.
    val thaw = if (state.frost) null else studio72f.kelvin.rememberThaw()
    val press = if (state.frost) null else studio72f.kelvin.rememberGlassPress(thaw)
    val surface = when {
        backdrop == null -> Modifier.clip(CardShape).background(if (style == "marker") c.accent else fill)
        // Frost (the default): every card shows its slice of one frosted copy of the wallpaper, lined up behind it,
        // with a milky lift. One blur for the whole screen, so opening every drawer stays smooth.
        state.frost && style != "marker" -> Modifier.clip(CardShape).frosted(state, { boundsState.value }, lit = style == "bright")
        state.frost -> Modifier.clip(CardShape).background(c.accent)
        style == "marker" -> Modifier.glass(backdrop, CardShape, GlassContent.Colored, tint = c.accent, thaw = thaw, motion = press?.layerBlock, pebble = press)
        style == "bright" -> Modifier.litGlass(backdrop, CardShape, motion = press?.layerBlock)
        else -> Modifier.glass(backdrop, CardShape, GlassContent.Text, thaw = thaw, motion = press?.layerBlock, pebble = press)
    }
    Box(
        modifier.padding(3.dp).height(when (state.columns) { 4 -> 128.dp; 6 -> 104.dp; else -> 116.dp })
            .onGloballyPositioned { boundsState.value = it.boundsInRoot() }
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 22.dp.toPx()
                scaleX = 0.94f + 0.06f * v; scaleY = scaleX
            }
            .then(if (interactive) Modifier.glassCardClick({ val b = boundsState.value; host.launchFrom(app, b.left.roundToInt(), b.top.roundToInt(), b.width.roundToInt(), b.height.roundToInt()) }, { state.pillMenu = appMenu(state, host, app, boundsState.value) }, app.title()) else Modifier)
            .then(surface)
            .then(if (interactive && press != null) press.gestures else Modifier)
            .then(if (interactive && state.frost) Modifier.frostPress(CardShape) else Modifier),
    ) {
        val label = Kelvin.type.caption.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 44.dp, state.monoIcons)
            Column(Modifier.padding(start = 12.dp)) {
                KText(app.title(), style = label.copy(fontSize = 15.sp, lineHeight = 18.sp), color = ink, maxLines = 2)
                if (minutes > 0) KText(Usage.formatMinutes(minutes), style = Kelvin.type.caption, color = ink.copy(alpha = 0.6f), maxLines = 1)
            }
        } else Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // A fixed layout that fills the card: icon, two reserved lines for the name, one for today's minutes,
            // so every card in a row lines up whether or not it shows usage.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AppIcon(app, when (state.columns) { 4 -> 48.dp; 6 -> 36.dp; else -> 42.dp }, state.monoIcons)
            }
            Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.TopCenter) {
                KText(app.title(), style = label, color = ink, maxLines = 2, align = TextAlign.Center)
            }
            Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
                if (minutes > 0) KText(Usage.formatMinutes(minutes), style = Kelvin.type.caption.copy(fontSize = 11.sp, lineHeight = 13.sp), color = ink.copy(alpha = 0.6f), maxLines = 1, align = TextAlign.Center)
            }
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(if (style == "marker") c.onAccent else c.accent))
    }
}

/** Up to four apps in a glass bar above the search capsule, icon only, with today's minutes under each. */
@Composable
private fun Dock(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val apps = state.dockIds.mapNotNull { id -> state.allApps.firstOrNull { it.id == id } }
    if (apps.isEmpty()) return
    val c = Kelvin.colors
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(84.dp)
            // Frost: the same milky slice of the frosted wallpaper as the cards; Liquid glass keeps live glass.
            .then(if (state.frost) Modifier.frostSurface(state, ContinuousShape(28.dp), milk = 0.13f) else Modifier.glass(backdrop, ContinuousShape(28.dp), GlassContent.Text, elevated = true)),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        for (app in apps) {
            var bounds by remember { mutableStateOf(Rect.Zero) }
            val minutes = state.usageMinutes[app.component.packageName] ?: 0
            Column(
                Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
                    .eigenPress({ host.launch(app) }, { state.pillMenu = appMenu(state, host, app, bounds) }, CircleShape, app.title())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app, 46.dp, state.monoIcons)
                if (minutes > 0) KText(Usage.formatMinutes(minutes), style = Kelvin.type.caption.copy(fontSize = 11.sp), color = c.ink2, maxLines = 1)
            }
        }
    }
}

/**
 * The A–Z app drawer: rises over a frosted pane, letter headers, the same cards as home. Pull down at the top to
 * close. [onDrag] reports progress while dragging (closing == null), then whether the release closes it.
 */
@Composable
private fun AppDrawer(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop, progress: Float, heightPx: Float, onDrag: (closing: Boolean?, progress: Float) -> Unit) {
    val c = Kelvin.colors
    val groups = remember(state.allApps) {
        state.allApps.sortedBy { it.title().lowercase() }.groupBy { a -> a.title().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }.toList()
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drag > 0f && available.y < 0f) {
                    val next = (drag + available.y).coerceAtLeast(0f); val used = next - drag; drag = next
                    onDrag(null, 1f - drag / heightPx); return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                drag += available.y; onDrag(null, 1f - drag / heightPx); return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag == 0f) return Velocity.Zero
                val close = drag > heightPx * 0.18f || available.y > 1800f
                drag = 0f; onDrag(close, 0f); return available
            }
        }
    }
    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, ((1f - progress) * heightPx).roundToInt()) },
    ) {
        // A frosted pane: it blurs the wallpaper behind it (not home's drawers, which fade out) and stays lined up
        // with the wallpaper as its edge rises with the finger.
        FrostedPane(state, (1f - progress) * heightPx, Modifier.fillMaxSize())
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().nestedScroll(connection).statusBarsPadding().padding(horizontal = 12.dp), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 180.dp)) {
            item { KText("All apps", Modifier.padding(start = 8.dp, bottom = 8.dp), Kelvin.type.display) }
            items(groups, key = { it.first }) { (letter, apps) ->
                Column {
                    KText(letter.toString(), Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp), Kelvin.type.title)
                    AppGrid(state, host, apps, animate = false, backdrop = backdrop)
                }
            }
        }
        EdgeScroller(list, groups.map { it.first }, Modifier.align(Alignment.CenterEnd).statusBarsPadding().padding(top = 72.dp, bottom = 190.dp))
    }
}

/**
 * The drawer's scroller: a slim thumb hugging the right edge that shows where you are. Drag it to move through the
 * drawer; it widens into the accent while held and the current letter rises in a pebble beside it, with a tick each
 * time the letter changes.
 */
@Composable
private fun EdgeScroller(list: androidx.compose.foundation.lazy.LazyListState, letters: List<Char>, modifier: Modifier) {
    if (letters.isEmpty()) return
    val c = Kelvin.colors
    val density = LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var trackHeight by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shownLetter by remember { mutableStateOf(-1) }
    val total = list.layoutInfo.totalItemsCount.coerceAtLeast(2)
    // Where the list is, 0 … 1: the first visible item plus how far into it we are.
    val scrolled = run {
        val first = list.firstVisibleItemIndex
        val size = list.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
        ((first + list.firstVisibleItemScrollOffset / size.toFloat()) / (total - 1)).coerceIn(0f, 1f)
    }
    val fraction = if (dragging) dragFraction else scrolled
    val width by androidx.compose.animation.core.animateDpAsState(if (dragging) 10.dp else 5.dp, Kelvin.motion.snappy(), label = "thumb width")
    val ink by animateColorAsState(if (dragging) c.accent else c.ink.copy(alpha = 0.55f), Kelvin.motion.gentle(), label = "thumb ink")
    val thumbH = with(density) { 56.dp.toPx() }
    fun seek(y: Float) {
        dragFraction = ((y - thumbH / 2f) / (trackHeight - thumbH)).coerceIn(0f, 1f)
        val item = (dragFraction * (total - 1)).roundToInt().coerceIn(0, total - 1)
        list.requestScrollToItem(item)
        val letter = (item - 1).coerceIn(0, letters.lastIndex) // item 0 is the title
        if (letter != shownLetter) { shownLetter = letter; haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    }
    Box(
        modifier.width(48.dp).fillMaxHeight()
            .onGloballyPositioned { trackHeight = it.size.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { o -> dragging = true; seek(o.y) },
                    onDragEnd = { dragging = false; shownLetter = -1 }, onDragCancel = { dragging = false; shownLetter = -1 },
                ) { change, _ -> change.consume(); seek(change.position.y) }
            }
            .semantics { contentDescription = "Scroll the app drawer" },
    ) {
        val top = fraction * (trackHeight - thumbH)
        Box(Modifier.align(Alignment.TopEnd).offset { IntOffset(-4.dp.roundToPx(), top.roundToInt()) }.size(width, 56.dp).clip(CapsuleShape).background(ink))
        if (dragging && shownLetter >= 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset { IntOffset(-64.dp.roundToPx(), (top + thumbH / 2f - 28.dp.toPx()).roundToInt()) }
                    .size(56.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { KText(letters[shownLetter].toString(), style = Kelvin.type.headline, color = c.onAccent) }
        }
    }
}

/**
 * Actions at the finger: everything else dims, a held app's card stays lit in place, and a Kelvin glass menu of pills
 * grows out of the anchor. Tap outside or press Back to dismiss.
 */
@Composable
private fun PillMenuOverlay(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val menu = state.pillMenu
    val dim by androidx.compose.animation.core.animateFloatAsState(if (menu != null) 1f else 0f, Kelvin.motion.gentle(), label = "pill scrim")
    if (menu == null && dim == 0f) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }; val h = with(density) { maxHeight.toPx() }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { state.pillMenu = null } })
        if (menu == null) return@BoxWithConstraints
        val b = menu.cardBounds
        if (menu.app != null && b != null) {
            Box(Modifier.offset { IntOffset(b.left.roundToInt(), b.top.roundToInt()) }.size(with(density) { b.width.toDp() }, with(density) { b.height.toDp() })) {
                AppCard(state, host, menu.app, 0, Modifier.fillMaxSize(), interactive = false, backdrop = backdrop)
            }
        }
        val pillH = with(density) { 50.dp.toPx() }; val gap = with(density) { 8.dp.toPx() }
        val stack = menu.items.size * (pillH + gap)
        // Above a held card when there is room, else below it; at the finger for a long-press on space.
        val top = when {
            b != null && b.top - stack > with(density) { 40.dp.toPx() } -> b.top - stack
            b != null -> b.bottom + gap
            else -> menu.y.coerceAtMost(h - stack - with(density) { 120.dp.toPx() })
        }
        val left = (b?.left ?: menu.x).coerceIn(with(density) { 12.dp.toPx() }, w - with(density) { 220.dp.toPx() })
        Column(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            menu.items.forEachIndexed { i, (label, action) ->
                val appear = remember(menu) { Animatable(0f) }
                val spatial = Kelvin.motion.spatial<Float>()
                LaunchedEffect(menu) { delay(i * 28L); appear.animateTo(1f, spatial) }
                KText(
                    label,
                    Modifier.graphicsLayer { alpha = appear.value.coerceIn(0f, 1f); scaleX = 0.85f + 0.15f * appear.value; scaleY = scaleX; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .height(50.dp).litGlass(backdrop, CapsuleShape)
                        .eigenPress({ state.pillMenu = null; action() }, shape = CapsuleShape)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    Kelvin.type.label.copy(fontSize = 15.sp), Kelvin.colors.ink, maxLines = 1,
                )
            }
        }
    }
}

/**
 * The one capsule at the bottom. At rest it reads "Tap to search" with the assistant spark and a magnifier; tapped,
 * it becomes the field itself, in place, with the keyboard up.
 */
@Composable
private fun SearchCapsule(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop, modifier: Modifier) {
    val c = Kelvin.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.searching) {
        if (state.searching) { delay(60); runCatching { focus.requestFocus() }; keyboard?.show() }
        else { focusManager.clearFocus(); keyboard?.hide() }
    }
    Row(
        modifier.fillMaxWidth().height(56.dp)
            .then(if (state.frost) Modifier.frostSurface(state, CapsuleShape, milk = 0.13f) else Modifier.glass(backdrop, CapsuleShape, GlassContent.Text, elevated = true))
            .then(if (!state.searching) Modifier.eigenPress({ state.searching = true }, { state.openAssistant(null) }, CapsuleShape, "Search") else Modifier)
            .padding(start = 22.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (state.searching) {
                BasicTextField(
                    state.query, { state.query = it },
                    Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = Kelvin.type.body.copy(color = c.ink, fontSize = 17.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitSearch(state, host) }),
                )
                if (state.query.isEmpty()) KText("Search apps, or ask anything", style = Kelvin.type.body.copy(fontSize = 17.sp), color = c.ink.copy(alpha = 0.78f), maxLines = 1)
            } else {
                KText("Tap to search", style = Kelvin.type.body.copy(fontSize = 17.sp), color = c.ink.copy(alpha = 0.78f), maxLines = 1)
            }
        }
        if (state.searching) {
            Box(Modifier.size(48.dp).eigenPress({ state.searching = false; state.query = "" }, shape = CircleShape, label = "Close search").semantics { contentDescription = "Close search" }, contentAlignment = Alignment.Center) {
                KIcon(KelvinIcons.X, null, size = 20.dp, color = c.ink)
            }
        } else {
            Box(Modifier.size(48.dp).eigenPress({ state.openAssistant(null) }, shape = CircleShape, label = "Ask the assistant").semantics { contentDescription = "Ask the assistant" }, contentAlignment = Alignment.Center) {
                KIcon(KelvinIcons.Sparkles, null, size = 22.dp, color = c.accent)
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { KIcon(KelvinIcons.Search, null, size = 22.dp, color = c.ink) }
        }
    }
}

private fun results(state: EigenState): List<MainActivity.App> {
    val q = CategoryRules.normalized(state.query.trim())
    if (q.isEmpty()) return emptyList()
    return state.allApps.filter { CategoryRules.normalized(it.title()).contains(q) || (it.label != null && CategoryRules.normalized(it.label).contains(q)) }
        .sortedBy { if (CategoryRules.normalized(it.title()).startsWith(q)) 0 else 1 }
        .take(24)
}

private fun naturalLanguage(q: String) = q.endsWith("?") || q.trim().split(Regex("\\s+")).size >= 3

private fun submitSearch(state: EigenState, host: EigenHost) {
    val first = results(state).firstOrNull()
    if (first != null) { host.launch(first); state.searching = false; state.query = "" }
    else if (state.query.isNotBlank()) state.openAssistant(state.query.trim())
}

@Composable
private fun SearchResults(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop, bottomInset: androidx.compose.ui.unit.Dp) {
    val c = Kelvin.colors
    val q = state.query.trim()
    val found = remember(q, state.allApps) { results(state) }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = bottomInset), verticalArrangement = Arrangement.Bottom) {
        if (q.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                KText("Type an app's name, or ask a question.\nAnswers come from the model on this phone.", style = Kelvin.type.body, color = c.ink2, align = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
                itemsIndexed(found, key = { _, a -> a.id }) { i, app ->
                    Row(
                        Modifier.fillMaxWidth().clip(ContinuousShape(18.dp))
                            .eigenPress({ host.launch(app); state.searching = false; state.query = "" }, { host.appMenu(app) }, ContinuousShape(18.dp), app.title())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 40.dp, state.monoIcons)
                        Spacer(Modifier.size(16.dp))
                        KText(app.title(), style = Kelvin.type.rowTitle.copy(fontSize = 18.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
                    }
                }
            }
            if (naturalLanguage(q) || found.isEmpty()) AskCard(state, q, backdrop)
        }
    }
}

/**
 * The on-device answer under search. Natural-language queries are asked automatically after a short pause; the
 * answer streams in. Tapping the card carries the question into the Assistant tab.
 */
@Composable
private fun AskCard(state: EigenState, q: String, backdrop: KelvinBackdrop) {
    val c = Kelvin.colors
    val context = LocalContext.current
    val scope = state.scope
    var asked by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun ask(question: String) {
        asked = question; busy = true; answer = null; source = null
        scope.launch {
            val a = EigenAssistant.ask(context, question, conversational = false) { if (asked == question) answer = it }
            if (asked == question) { answer = a.text; source = a.source; busy = false }
        }
    }
    LaunchedEffect(q) { if (naturalLanguage(q) && q != asked) { delay(900); ask(q) } }
    val showing = asked == q
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .glass(backdrop, ContinuousShape(24.dp), GlassContent.Text)
            .eigenPress({ if (showing && !busy) state.openAssistant(q) else ask(q) }, shape = ContinuousShape(24.dp), label = "Ask Eigen")
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KIcon(KelvinIcons.Sparkles, null, size = 18.dp, color = c.accent)
            Spacer(Modifier.size(8.dp))
            KText(if (showing) "Eigen" else "Ask Eigen “$q”", style = Kelvin.type.label, maxLines = 1)
        }
        if (showing) KText(answer ?: "Thinking…", style = Kelvin.type.body.copy(fontSize = 16.sp), color = if (answer == null) c.ink2 else c.ink, maxLines = 8)
        val note = when {
            showing && !busy && source != null -> "$source · tap to continue"
            !showing -> EigenAssistant.statusLabel()
            else -> null
        }
        if (note != null) KText(note, style = Kelvin.type.caption, color = c.ink2, maxLines = 1)
    }
}

/** Click and long-press for a glass card; its press motion comes from the glass itself (lean, swell, thaw). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.glassCardClick(onClick: () -> Unit, onLongClick: () -> Unit, label: String): Modifier = composed {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    combinedClickable(
        interactionSource = null, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label,
        onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() },
        onClick = onClick,
    )
}

/**
 * Frost for a card: its slice of the frosted wallpaper (blurred once, kept by the activity), placed where the real
 * wallpaper sits behind it, then a smoky shade and a milky lift so the card glows a little brighter than the dimmed
 * wallpaper around it. [lit] lifts it further. Without a photo wallpaper it falls back to a milky fill.
 */
@Composable
private fun Modifier.frosted(state: EigenState, bounds: () -> Rect, lit: Boolean): Modifier {
    val soft = state.softWallpaper
    val milk = if (lit) 0.20f else 0.11f
    if (state.wallpaperMode != "photo" || soft == null) return background(Color.White.copy(alpha = milk + 0.04f))
    val image = remember(soft) { soft.asImageBitmap() }
    val root = androidx.compose.ui.platform.LocalView.current.rootView
    val filter = remember(state.greyscale) {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(if (state.greyscale) 0f else 1.35f) })
    }
    return drawBehind {
        val w = root.width.toFloat(); val h = root.height.toFloat()
        if (w <= 0f || h <= 0f) return@drawBehind
        val b = bounds() // a read in the draw phase: moving redraws, it never recomposes
        val scale = maxOf(w / image.width, h / image.height)
        val dw = image.width * scale; val dh = image.height * scale
        drawImage(
            image,
            dstOffset = IntOffset(((w - dw) / 2f - b.left).roundToInt(), ((h - dh) / 2f - b.top).roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(dw.roundToInt(), dh.roundToInt()),
            colorFilter = filter,
        )
        drawRect(Color.Black.copy(alpha = (state.dim * 0.45f + 0.08f).coerceAtMost(0.6f)))
        drawRect(Color.White.copy(alpha = milk))
    }
}

/** Frost has no lean or thaw; it presses like Kelvin's other matte surfaces (warm-wax squash and a sheen). */
private fun Modifier.frostPress(shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
    val press = remember { Animatable(0f) }
    val snappy = Kelvin.motion.snappy<Float>(); val wobble = Kelvin.motion.wobble<Float>()
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val down = e.changes.any { it.pressed }
                scope.launch { press.animateTo(if (down) 1f else 0f, if (down) snappy else wobble) }
            }
        }
    }.graphicsLayer { val p = press.value; scaleX = 1f + 0.02f * p; scaleY = 1f - 0.035f * p }
        .drawWithContent { drawContent(); if (press.value > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = 0.12f * press.value)) }
}

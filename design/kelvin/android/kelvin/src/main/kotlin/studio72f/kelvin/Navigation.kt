package studio72f.kelvin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** "72F Gallery" with the brand half in the accent. */
@Composable
fun kelvinWordmark(brand: String, name: String): AnnotatedString {
    val accent = Kelvin.colors.accent
    return buildAnnotatedString {
        withStyle(SpanStyle(color = accent)) { append(brand) }
        append(" ")
        append(name)
    }
}

/**
 * Top-level header: large title over the Horizon, up to two actions on the right, an optional
 * summary below. Use [KelvinDetailHeader] on pages that are not top level.
 */
@Composable
fun KelvinPageHeader(
    title: AnnotatedString,
    modifier: Modifier = Modifier,
    summary: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().horizon().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                title, Modifier.weight(1f).semantics { heading() },
                style = Kelvin.type.display.copy(color = Kelvin.colors.ink), maxLines = 1,
            )
            actions()
        }
        if (summary != null) KText(summary, Modifier.padding(top = 2.dp), Kelvin.type.summary, Kelvin.colors.ink2)
    }
}

/**
 * The masthead of a top-level page, after One UI's one-handed opening: a [KelvinReachHeader]
 * opens the page with a tall, empty-feeling area (the aurora, the summary) in which a massive
 * title sits centred; as the page scrolls up, [KelvinReachBar] shrinks the title and slides it
 * to the top-start corner, where it settles as the bar's title on the veil. [progress] runs 0
 * at rest to 1 once the title has landed; [collapsed] is true from then on.
 */
@Stable
class KelvinReach internal constructor() {
    /** 0 at rest, 1 once the title has landed in the top bar. */
    var progress by androidx.compose.runtime.mutableFloatStateOf(0f)
        internal set

    /** The title has landed in the top bar (or the opening has left a lazy list). */
    val collapsed: Boolean get() = progress >= 1f

    /** The opening's height in pixels, which sets where the title rests. */
    internal var openingPx by androidx.compose.runtime.mutableFloatStateOf(0f)
}

@Composable
fun rememberKelvinReach(): KelvinReach = remember { KelvinReach() }

/** The masthead's weight at rest and once it has landed in the bar. */
private const val MastheadBlack = 900
private const val MastheadLanded = 600

/** The top bar's row under the status bar. */
private val ReachBarHeight = 52.dp

/**
 * The opening of a one-handed top-level page: the first thing in what scrolls, about a third of
 * the screen tall (at least 240dp) with the aurora behind it. It holds no title itself; the
 * massive title is drawn by the [KelvinReachBar] on top, centred in this space at rest. An
 * optional [summary] sits centred near its foot and fades as the title starts to move. Do not
 * pad the page by [LocalVeilInset].
 */
@Composable
fun KelvinReachHeader(
    reach: KelvinReach,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val density = LocalDensity.current
    val screen = with(density) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
    val barPx = WindowInsets.statusBars.getTop(density) + with(density) { ReachBarHeight.toPx() }
    androidx.compose.runtime.DisposableEffect(reach) { onDispose { reach.progress = 1f } }
    // The aurora drifts while the opening is on screen and holds still once it has scrolled away.
    val showing by remember { androidx.compose.runtime.derivedStateOf { reach.progress < 1f } }
    Box(
        modifier
            .fillMaxWidth()
            // Grows with large text so the summary never climbs into the centred title.
            .height(maxOf(screen * 0.34f, 240.dp).withTextScale(Kelvin.type.summary.lineHeight * 6))
            .horizon(animate = showing)
            .onGloballyPositioned {
                val h = it.size.height.toFloat()
                reach.openingPx = h
                val travel = (h - barPx).coerceAtLeast(1f)
                reach.progress = (-it.positionInRoot().y / travel).coerceIn(0f, 1f)
            },
    ) {
        if (summary != null) KText(
            summary,
            Modifier.align(Alignment.BottomCenter).padding(start = 32.dp, end = 32.dp, bottom = 20.dp)
                .graphicsLayer { alpha = 1f - smoothstep(0f, 0.3f, reach.progress) },
            Kelvin.type.summary, Kelvin.colors.ink2, align = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * The top bar of a one-handed page, laid over the page (outside what scrolls) with the page's
 * [backdrop]. It draws the [title]: massive (`masthead`), Black (900) and centred in the
 * [KelvinReachHeader] at rest, shrinking, slimming to 600 and sliding to the top-start corner as
 * the page scrolls, where it lands at `title` size on the veil, which fades in under it. [actions] (up to two circles)
 * stay at the top-end throughout.
 */
@Composable
fun KelvinReachBar(
    title: AnnotatedString,
    reach: KelvinReach,
    backdrop: KelvinBackdrop,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    val big = Kelvin.type.masthead
    val small = Kelvin.type.title
    val scaleTo = small.fontSize.value / big.fontSize.value
    var textSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // The masthead is Black (900) at rest and slims as it folds, to 600 in the bar, on Onest's
    // variable weight axis, in steps of 25 so a fold re-sets the text a dozen times, not every frame.
    val weight by remember {
        androidx.compose.runtime.derivedStateOf {
            val p = reach.progress.let { it * it * (3f - 2f * it) }
            ((MastheadBlack + (MastheadLanded - MastheadBlack) * p) / 25f).roundToInt() * 25
        }
    }
    val family = Kelvin.type.displayAt(weight)
    KelvinVeiledHeader(backdrop, modifier.fillMaxWidth(), veilAlpha = { smoothstep(0.55f, 1f, reach.progress) }) {
        BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().height(ReachBarHeight)) {
            val width = constraints.maxWidth.toFloat()
            val ink = LocalVeilInk.current.takeOrElse { Kelvin.colors.ink }
            androidx.compose.foundation.text.BasicText(
                title,
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .onSizeChanged { textSize = it }
                    .semantics { heading() }
                    .graphicsLayer {
                        val tw = textSize.width.toFloat()
                        val th = textSize.height.toFloat()
                        if (tw <= 0f) return@graphicsLayer
                        val p = reach.progress.let { it * it * (3f - 2f * it) }
                        val edge = 16.dp.toPx()
                        // At rest: as large as fits, centred in the opening (below the status bar).
                        val restScale = minOf(1f, (width - 2 * edge) / tw)
                        val restX = (width - tw * restScale) / 2f
                        val restY = reach.openingPx * 0.46f - statusBar - th * restScale / 2f
                        // Landed: title size, at the start of the bar, centred in its row.
                        val barY = (ReachBarHeight.toPx() - th * scaleTo) / 2f
                        val s = restScale + (scaleTo - restScale) * p
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        scaleX = s
                        scaleY = s
                        translationX = restX + (edge - restX) * p
                        translationY = restY + (barY - restY) * p
                    },
                style = big.copy(color = ink, fontFamily = family, fontWeight = androidx.compose.ui.text.font.FontWeight(weight)),
                maxLines = 1,
                softWrap = false,
            )
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/**
 * Detail page header: back and actions as circles, then the title in headline. As a screen's title
 * bar it goes in a [KelvinVeiledScreen]'s header with its `chrome` as [backdrop], so the back
 * circle is glass on the veil; without one it is a raised circle on the canvas.
 */
@Composable
fun KelvinDetailHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    backdrop: KelvinBackdrop? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KelvinIconButton(KelvinIcons.Back, backLabel, onBack, kind = if (backdrop != null) IconButtonKind.Glass else IconButtonKind.Raised, backdrop = backdrop)
            Spacer(Modifier.weight(1f))
            actions()
        }
        KText(title, Modifier.padding(start = 8.dp, top = 8.dp).semantics { heading() }, Kelvin.type.headline, LocalVeilInk.current.takeOrElse { Kelvin.colors.ink })
        if (summary != null) KText(summary, Modifier.padding(start = 8.dp, top = 2.dp), Kelvin.type.summary, Kelvin.colors.ink2)
    }
}

/**
 * Bar that fades in at the top once the large title has scrolled away, with the title centred.
 * Like every title bar it is a veil, not a pane: the page blurs under it and fades out below it.
 * Keep it composed and drive [visible].
 */
@Composable
fun KelvinCompactBar(
    title: String,
    visible: Boolean,
    backdrop: KelvinBackdrop,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, Kelvin.motion.gentle(), label = "compact")
    if (shown <= 0.01f) return
    KelvinVeiledHeader(
        backdrop,
        modifier.fillMaxWidth().graphicsLayer { alpha = shown; translationY = (1f - shown) * -12.dp.toPx() },
    ) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 4.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { leading() }
            KText(title, Modifier.align(Alignment.Center), Kelvin.type.name,
                LocalVeilInk.current.takeOrElse { Kelvin.colors.ink }, maxLines = 1)
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

data class DockItem(val label: String, val icon: ImageVector, val badge: Boolean = false)

// The droplet sits the same 6dp from the dock's rim on every side.
private val DockSidePad = 6.dp

// Each tab's width in the compact dock, when the screen has room for it.
private val DockSlot = 64.dp

/**
 * The floating glass dock, compact and centred: it shrink-wraps its tabs (64dp each) rather than
 * spanning the screen, with the action orb budding out beside it. The selection is a solid
 * `accent` droplet with the tab's icon and label in `on-accent`, so the current tab stays plain at
 * every Glass level, Clear included. It slides between
 * tabs and stretches with speed. When the person drags along the dock it lifts into a clear
 * pane of Kelvin glass over the icons, bending them at its edges, follows the finger and lands
 * on the tab underneath, thawing the dock's frost as it goes. [action] adds the orb beside the
 * dock for the screen's one creation action.
 */
@Composable
fun KelvinDock(
    items: List<DockItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    labels: DockLabels = DockLabels.Auto,
) {
    val c = Kelvin.colors
    val g = Kelvin.glass
    // The names give way as room runs out (a small phone, a large Display size, large text):
    // every tab named from 420dp, only the current one from 360dp, icons alone below that. The
    // names stay for TalkBack either way.
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val mode = when (labels) {
        DockLabels.Auto -> when {
            largeText() || width < DockLabelsActiveWidth -> DockLabels.Never
            width < DockLabelsAllWidth -> DockLabels.Active
            else -> DockLabels.Always
        }
        else -> labels
    }
    val showLabels = mode == DockLabels.Always
    // Each tab's width when it is current in Active mode: its name and a margin either side.
    val dockMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val dockDensity = LocalDensity.current
    val labelStyle = Kelvin.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    val labelSlots = remember(items, labelStyle, dockDensity) {
        items.map { item ->
            val w = with(dockDensity) { dockMeasurer.measure(item.label, labelStyle, maxLines = 1).size.width.toDp() }
            maxOf(w + 24.dp, DockNarrow + 10.dp)
        }
    }
    // The resting grey, already as light as KIcon draws a neutral filled glyph, so blending it
    // toward white never crosses KIcon's softening and flickers light and dark mid-drag.
    val restInk = androidx.compose.ui.graphics.lerp(c.glassInk2, c.canvas, 0.6f).legibleOn(c.group, 3f)
    val spatial = Kelvin.motion.spatial<Float>()
    val snappy = Kelvin.motion.snappy<Float>()
    val wobble = Kelvin.motion.wobble<Float>()
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onSelect)
    val pos = remember { Animatable(selected.toFloat()) }
    val lift = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableStateOf(0f) }
    val icons = rememberKelvinBackdrop()
    val thaw = rememberThaw()
    LaunchedEffect(selected) { if (!dragging) pos.animateTo(selected.toFloat(), spatial) }
    // A segment tick as the selection lands on a new tab.
    var firstSelect by remember { mutableStateOf(true) }
    val haptics = rememberKelvinHaptics()
    LaunchedEffect(selected) {
        if (firstSelect) { firstSelect = false; return@LaunchedEffect }
        haptics.tick()
    }

    // Compact and centred, as ColorOS 17 docks are: the dock shrink-wraps its tabs instead of
    // spanning the screen, and gives way only when the tabs would not fit.
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        val orbRoom = if (action != null) OrbReach else 0.dp
        // Active: icon-only tabs are narrow and the current one widens to fit its name; every
        // width follows the droplet, so room flows from tab to tab as it moves. Widths scale down
        // together when even that does not fit.
        val activeWidths: List<Dp>? = if (mode == DockLabels.Active) {
            val p = pos.value
            // Every tab widens to the same size (the longest name's), so the droplet's share always
            // adds up to one wide slot and the dock keeps one width while the droplet moves.
            // Capped, so a dock of two tabs with long names never grows absurdly wide; a name
            // longer than the cap ends in an ellipsis.
            val wide = (labelSlots.maxOrNull() ?: DockNarrow).coerceAtMost(DockLabelMax)
            val raw = items.indices.map { i -> val near = 1f - abs(p - i).coerceAtMost(1f); DockNarrow + (wide - DockNarrow) * near }
            val room = maxWidth - orbRoom - DockSidePad * 2
            val total = raw.fold(0.dp) { a, w -> a + w }
            if (total > room) raw.map { it * (room / total) } else raw
        } else null
        val dockWidth = if (activeWidths != null) activeWidths.fold(0.dp) { a, w -> a + w } + DockSidePad * 2
            else minOf(maxWidth - orbRoom, DockSlot * items.size + DockSidePad * 2)
        // The orb buds out of the dock, as on HyperOS 4: it starts inside the dock's end and
        // springs out to its place, a neck of the same glass stretching between them until it
        // snaps. 1 is fully inside, 0 at rest beside the dock.
        val hasAction = action != null
        val reduce = Kelvin.reduceMotion
        val bud = remember { Animatable(if (hasAction && !reduce) 1f else 0f) }
        LaunchedEffect(hasAction, reduce) {
            if (!hasAction || reduce) { bud.snapTo(0f); return@LaunchedEffect }
            bud.snapTo(1f)
            kotlinx.coroutines.delay(160)
            bud.animateTo(0f, spring(0.55f, 240f))
        }
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        val b = bud.value
        // Vitro draws only rounded rectangles, so under Liquid glass the orb buds without a neck.
        if (hasAction && b > 0.01f && (g.isFrost || g.isSolid || backdrop == null)) {
            // From the centre of the dock's end cap to the orb's resting centre.
            val capCentre = -(dockWidth + OrbReach) / 2 + dockWidth - 32.dp
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (capCentre + OrbReach / 2) * (if (rtl) -1f else 1f))
                    .size(OrbReach, 64.dp)
                    .glass(backdrop, remember(b) { DockNeckShape(b) }),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(
                Modifier
                    .width(dockWidth)
                    .height(64.dp)
                    .glass(backdrop, CapsuleShape, thaw = thaw, elevated = true)
                    .padding(horizontal = DockSidePad, vertical = 6.dp),
            ) {
                val slot = maxWidth / items.size
                val slotPx = with(LocalDensity.current) { slot.toPx() }
                val padPx = with(LocalDensity.current) { DockSidePad.toPx() }
                val padYPx = with(LocalDensity.current) { 6.dp.toPx() }
                // The lifted lens is liquid glass; under Frost the droplet stays a solid drop.
                val lens = g.deform > 0f && !g.isSolid && !g.isFrost
                Box(
                    Modifier.fillMaxSize().pointerInput(items.size) {
                        detectHorizontalDragGestures(
                            onDragStart = { o ->
                                dragging = true
                                dragTarget = (o.x / slotPx - 0.5f).coerceIn(0f, items.lastIndex.toFloat())
                                thaw.touch(Offset(o.x + padPx, o.y + padYPx))
                                scope.launch { lift.animateTo(1f, snappy) }
                                scope.launch { pos.animateTo((o.x / slotPx - 0.5f).coerceIn(0f, items.lastIndex.toFloat()), spatial) }
                            },
                            onDragEnd = {
                                dragging = false
                                thaw.touch(null)
                                val i = pos.value.roundToInt().coerceIn(0, items.lastIndex)
                                scope.launch { lift.animateTo(0f, wobble) }
                                scope.launch { pos.animateTo(i.toFloat(), wobble) }
                                pick(i)
                            },
                            onDragCancel = { dragging = false; thaw.touch(null); scope.launch { lift.animateTo(0f, wobble) } },
                            onHorizontalDrag = { ch, dx ->
                                // Follow the finger on a stiff spring rather than snapping, so the
                                // droplet keeps a velocity to stretch with.
                                ch.consume()
                                thaw.touch(Offset(ch.position.x + padPx, ch.position.y + padYPx))
                                dragTarget = (dragTarget + dx / slotPx).coerceIn(-0.25f, items.lastIndex + 0.25f)
                                scope.launch { pos.animateTo(dragTarget, spring(0.85f, 1400f)) }
                            },
                        )
                    },
                ) {
                    val p = pos.value
                    val l = lift.value
                    val speed = (abs(pos.velocity) * 0.06f).coerceAtMost(0.45f) * g.deform
                    val dropX: Dp
                    val dropW: Dp
                    if (activeWidths != null) {
                        val from = kotlin.math.floor(p).toInt().coerceIn(0, items.lastIndex)
                        val to = (from + 1).coerceAtMost(items.lastIndex)
                        val f = (p - from).coerceIn(0f, 1f)
                        val xs = activeWidths.runningFold(0.dp) { a, w -> a + w }
                        dropX = xs[from] + (xs[to] - xs[from]) * f
                        dropW = activeWidths[from] + (activeWidths[to] - activeWidths[from]) * f
                    } else { dropX = slot * p; dropW = slot }
                    val droplet = Modifier
                        .offset(x = dropX)
                        .width(dropW)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val pop = 1f + 0.16f * l * (0.3f + 0.7f * g.deform)
                            scaleX = pop * (1f + speed)
                            scaleY = pop * (1f - speed * 0.35f)
                        }
                    // Resting droplet sits under the icons: solid accent, never thinned by the glass.
                    // It is Kelvin's mark: where iOS greys the current tab, Kelvin fills it. It is
                    // the same pebble as the action orb (same fill, finish and white content), so
                    // the current tab and the screen's action read as one colour.
                    val lifted = if (lens) l else 0f
                    Box(droplet.graphicsLayer { alpha = 1f - lifted }.pebble(CapsuleShape, c.accent, glow = false))
                    Row(Modifier.fillMaxSize().kelvinBackdrop(icons)) {
                        items.forEachIndexed { i, item ->
                            val near = 1f - abs(p - i).coerceAtMost(1f)
                            // On the droplet the tab is white, like the orb's icon (every accent fill
                            // is deep enough for white to clear 3:1); under the lifted lens, where the
                            // fill has gone, it is the accent itself.
                            val iconOn = androidx.compose.ui.graphics.lerp(c.accent.iconInk(), c.accent, lifted)
                            val labelOn = androidx.compose.ui.graphics.lerp(c.onAccent, c.accent, lifted)
                            val tint = androidx.compose.ui.graphics.lerp(restInk, iconOn, near)
                            val labelTint = androidx.compose.ui.graphics.lerp(restInk, labelOn, near)
                            val itemModifier = if (activeWidths != null) Modifier.width(activeWidths[i]) else Modifier.weight(1f)
                            if (activeWidths != null) Box(
                                itemModifier
                                    .fillMaxHeight()
                                    .semantics { this.selected = i == selected; contentDescription = item.label }
                                    .kelvinPress({ pick(i) }, role = Role.Tab, shape = CapsuleShape),
                            ) {
                                // Placed, not stacked: as the droplet arrives the icon glides up and the
                                // name fades in beneath it, so nothing reflows and nothing jumps.
                                Box(Modifier.align(Alignment.Center).offset(y = (-8).dp * smoothstep(0.6f, 1f, near))) {
                                    KIcon(item.icon, null, size = 24.dp, color = tint)
                                    if (item.badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(8.dp).clip(CircleShape).background(c.accent))
                                }
                                // The name shows only while the droplet mostly covers its tab, and only in
                                // the droplet's own ink: no grey half-names on the tab it is leaving.
                                val shown = smoothstep(0.78f, 1f, near)
                                if (shown > 0.01f) KText(
                                    item.label,
                                    Modifier.align(Alignment.BottomCenter).padding(start = 6.dp, end = 6.dp, bottom = 5.dp).graphicsLayer { alpha = shown; translationY = (1f - shown) * 6.dp.toPx() },
                                    labelStyle, labelOn, maxLines = 1,
                                )
                            } else Column(
                                itemModifier
                                    .fillMaxHeight()
                                    .semantics {
                                        this.selected = i == selected
                                        if (!showLabels) contentDescription = item.label
                                    }
                                    .kelvinPress({ pick(i) }, role = Role.Tab, shape = CapsuleShape),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box {
                                    KIcon(item.icon, null, size = if (mode == DockLabels.Never) 26.dp else 24.dp, color = tint)
                                    if (item.badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(8.dp).clip(CircleShape).background(c.accent))
                                }
                                // Active: only the tab under the droplet is named, its name fading in as
                                // the droplet arrives and out as it leaves.
                                if (showLabels) KText(item.label, style = Kelvin.type.micro, color = labelTint, maxLines = 1)
                                else if (mode == DockLabels.Active && near > 0.02f) KText(
                                    item.label, Modifier.graphicsLayer { alpha = near }, Kelvin.type.micro, labelTint, maxLines = 1,
                                )
                            }
                        }
                    }
                    // Lifted droplet: a clear pane of glass over the icons.
                    if (lens && l > 0.01f) {
                        Box(
                            droplet.graphicsLayer { alpha = l }
                                .glass(icons, CapsuleShape, GlassContent.Decorative, tint = c.glassTint, tintAlpha = 0.12f),
                        )
                    }
                }
            }
            if (action != null) Box(
                Modifier.size(64.dp).graphicsLayer {
                    val v = bud.value
                    translationX = -v * OrbReach.toPx() * (if (rtl) -1f else 1f)
                    val sc = 1f - 0.4f * v
                    scaleX = sc
                    scaleY = sc
                    alpha = 1f - smoothstep(0.8f, 1f, v)
                },
                contentAlignment = Alignment.Center,
            ) { action() }
        }
    }
}

/** How far the orb's centre sits from the dock's end cap centre: its 64dp and the 12dp gap. */
private val OrbReach = 64.dp + 12.dp

/**
 * The neck of glass between the dock's end cap (a circle at the left of this box) and the orb
 * budding out of it ([bud] 1 inside, 0 at rest at the right of the box). It thins as the gap
 * opens and snaps once the gap passes 10dp, so at rest there is none.
 */
private class DockNeckShape(private val bud: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val empty = Outline.Generic(Path())
        val h = size.height
        val cy = h / 2f
        val capR = h / 2f
        val orbR = capR * (1f - 0.4f * bud)
        val ox = size.width * (1f - bud)
        val gap = ox - orbR - capR
        val maxGap = with(density) { 10.dp.toPx() }
        if (gap > maxGap) return empty
        val ax = capR * 0.55f
        val ay = capR * 0.83f
        val bx = ox - orbR * 0.55f
        val by = orbR * 0.83f
        if (bx <= ax) return empty
        // The waist: nearly full while the two overlap, thinning to a thread as the gap opens.
        val t = (gap / maxGap).coerceIn(0f, 1f)
        val waist = minOf(capR, orbR) * (0.9f - 0.75f * t)
        val mx = (ax + bx) / 2f
        val ctrl = (waist - 0.25f * (ay + by)) / 0.5f
        fun x(v: Float) = if (layoutDirection == LayoutDirection.Rtl) size.width - v else v
        return Outline.Generic(Path().apply {
            moveTo(x(ax), cy - ay)
            quadraticTo(x(mx), cy - ctrl, x(bx), cy - by)
            lineTo(x(bx), cy + by)
            quadraticTo(x(mx), cy + ctrl, x(ax), cy + ay)
            close()
        })
    }
}

/**
 * The orb beside the dock for the screen's one creation action: a solid, vivid accent drop on
 * its own-colour glow with a white glyph, so it invites a touch, whatever is behind it. It buds
 * out of the dock when it appears. [backdrop] is kept for compatibility.
 */
@Composable
fun KelvinActionOrb(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: KelvinBackdrop? = null,
) {
    val c = Kelvin.colors
    val press = rememberGlassPress()
    Box(
        modifier
            .size(64.dp)
            .kelvinClick(onClick, onClickLabel = contentDescription)
            .then(
                // Always a solid, vivid accent drop on its own-colour glow: frosted or stained glass
                // would dilute it, and it has to invite a touch.
                Modifier.pebbleMotion(press).pebble(CircleShape, c.accent, press).then(press.gestures),
            ),
        contentAlignment = Alignment.Center,
    ) { KIcon(icon, contentDescription, size = 26.dp, color = c.accent.iconInk()) }
}

/**
 * Text tabs that filter the content below. Every resting tab is a [kelvinPill]: lit glass over
 * content ([backdrop] given), a [lift] pill in a group, with `ink` text. The selection is a quiet
 * `accent-soft` pill sliding and stretching over them, with `on-accent-soft` text.
 */
@Composable
fun KelvinTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: KelvinBackdrop? = null,
) {
    val bounds = remember(tabs.size) { mutableStateListOf(*Array(tabs.size) { 0f to 0f }) }
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    val spatial = Kelvin.motion.spatial<Float>()
    val deform = Kelvin.glass.deform
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, placed) {
        val (bx, bw) = bounds.getOrNull(selected) ?: return@LaunchedEffect
        if (!placed) return@LaunchedEffect
        if (w.value == 0f) { x.snapTo(bx); w.snapTo(bw) } else { launch { x.animateTo(bx, spatial) }; launch { w.animateTo(bw, spatial) } }
    }
    val density = LocalDensity.current
    // Every tab is its own pill, as in ColorOS headers, and the selection an accent pebble
    // sliding over them.
    val scroll = rememberScrollState()
    // A carousel: room for the chosen pill's glow, and a fade at whichever end has more.
    Box(modifier.fillMaxWidth().glowRoom().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = GlowRoom)) {
        val speed = (abs(x.velocity) / 4000f).coerceAtMost(0.3f) * deform
        Box(
            Modifier
                .offset(x = with(density) { x.value.toDp() })
                .width(with(density) { w.value.toDp() })
                .height(36.dp.withTextScale(Kelvin.type.label.lineHeight))
                .graphicsLayer { scaleX = 1f + speed; scaleY = 1f - speed * 0.4f }
                .kelvinPill(selected = true, backdrop, CapsuleShape),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                // How much of this tab the sliding accent pill covers right now: its words take
                // the accent's ink as the pill arrives and give it back as it leaves, instead of
                // flipping when the selection changes.
                val (bx, bw) = bounds[i]
                val cover = if (bw <= 0f || w.value <= 0f) (if (on) 1f else 0f)
                    else ((minOf(x.value + w.value, bx + bw) - maxOf(x.value, bx)) / bw).coerceIn(0f, 1f)
                KText(
                    t,
                    Modifier
                        .height(36.dp.withTextScale(Kelvin.type.label.lineHeight))
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat(); if (i == tabs.lastIndex) placed = true }
                        .semantics { this.selected = on }
                        .kelvinPull({ onSelect(i) }, role = Role.Tab, shape = CapsuleShape, wash = false)
                        .then(if (!on) Modifier.kelvinPill(selected = false, backdrop, CapsuleShape) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    style = Kelvin.type.label.copy(fontWeight = if (cover > 0.5f) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                    color = androidx.compose.ui.graphics.lerp(kelvinPillInk(false), kelvinPillInk(true), cover),
                    maxLines = 1,
                )
            }
        }
    }
}


/** Two to four views of the same content on a grey track with a white thumb that slides; tap an option or drag the thumb to it. The label under the thumb is semibold ink. */
@Composable
fun KelvinSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val pos = remember { Animatable(selected.toFloat()) }
    val spatial = Kelvin.motion.spatial<Float>()
    val deform = Kelvin.glass.deform
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!dragging) pos.animateTo(selected.toFloat(), spatial) }
    val scope = rememberCoroutineScope()
    val pick by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(selected)
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp.withTextScale(Kelvin.type.label.lineHeight)).clip(KelvinShapes.md).background(c.sunken).padding(3.dp)) {
        val slot: Dp = maxWidth / options.size
        val slotPx = constraints.maxWidth.toFloat() / options.size
        // The thumb can be dragged as well as tapped: it follows the finger, ticks as it crosses
        // into each option, and on release settles on the nearest one and selects it.
        val dragInput = Modifier.pointerInput(options.size, slotPx) {
            var over = 0
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; over = pos.value.roundToInt() },
                onDragEnd = {
                    dragging = false
                    val target = pos.value.roundToInt().coerceIn(0, options.lastIndex)
                    scope.launch { pos.animateTo(target.toFloat(), spatial) }
                    if (target != current) pick(target)
                },
                onDragCancel = { dragging = false; scope.launch { pos.animateTo(current.toFloat(), spatial) } },
                onHorizontalDrag = { ch, dx ->
                    ch.consume()
                    val next = (pos.value + dx / slotPx).coerceIn(0f, options.lastIndex.toFloat())
                    scope.launch { pos.snapTo(next) }
                    val now = next.roundToInt()
                    if (now != over) { over = now; haptics.tick() }
                },
            )
        }
        val speed = (abs(pos.velocity) * 0.05f).coerceAtMost(0.3f) * deform
        Box(
            Modifier
                .offset(x = slot * pos.value)
                .width(slot)
                .fillMaxHeight()
                .graphicsLayer { scaleX = 1f + speed; scaleY = 1f - speed * 0.3f }
                // Seated in a clipped well: no glow, which the track would cut into a hard band.
                // A quiet thumb: a white stone on the grey track (a step lighter in dark).
                .lift(ContinuousShape(13.dp)),
        )
        Row(Modifier.fillMaxSize().then(dragInput)) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().semantics { this.selected = i == selected }
                        .kelvinPress({ if (i != selected) haptics.tick(); onSelect(i) }, role = Role.Tab, shape = ContinuousShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // The label under the thumb is ink and semibold; the others are ink-2. Both
                    // follow the thumb as it slides, so the words change as it passes over them.
                    val near = (1f - abs(pos.value - i)).coerceIn(0f, 1f)
                    KText(o, style = Kelvin.type.label.copy(fontWeight = if (near > 0.5f) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium), color = androidx.compose.ui.graphics.lerp(c.ink2, c.ink, near), maxLines = 1)
                }
            }
        }
    }
}

/** Search capsule. The keyboard's search key closes the keyboard; a clear button appears with text. */
@Composable
fun KelvinSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    backdrop: KelvinBackdrop? = null,
) {
    val c = Kelvin.colors
    val focus = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().height(44.dp.withTextScale(Kelvin.type.body.lineHeight)),
        singleLine = true,
        textStyle = Kelvin.type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(); focus.clearFocus() }),
        decorationBox = { field ->
            Row(
                // A sunken well inside groups; a glass capsule when it floats over content.
                Modifier.fillMaxSize()
                    .then(if (backdrop != null) Modifier.glass(backdrop, CapsuleShape) else Modifier.background(c.sunken, CapsuleShape))
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KIcon(KelvinIcons.Search, null, size = 20.dp, color = c.ink2)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) KText(placeholder, style = Kelvin.type.body, color = c.ink2, maxLines = 1)
                    field()
                }
                if (query.isNotEmpty()) KelvinIconButton(KelvinIcons.X, clearLabel, { onQueryChange("") }, kind = IconButtonKind.Plain)
            }
        },
    )
}

/**
 * A pale ink tint for a selection inside glass that must not take the accent: `ink` at a low
 * alpha. The dock's droplet is the accent itself (see [KelvinDock]).
 */
val KelvinColors.selectionDroplet: Color get() = ink.copy(alpha = 0.13f)

/** Whether [KelvinDock] names its tabs under their icons. */
enum class DockLabels {
    /**
     * By room: every tab named from 420dp wide, only the current tab from 360dp, icons alone below
     * 360dp or with text above 130%.
     */
    Auto,
    Always,
    /** Only the current tab is named, its name following the droplet. */
    Active,
    Never,
}

/** An icon-only tab's width in [DockLabels.Active]: close to square on the 52dp-tall droplet. */
private val DockNarrow = 46.dp

/** The widest the current tab grows in [DockLabels.Active], whatever its name. */
private val DockLabelMax = 104.dp

/** The narrowest screens, in dp, on which the current tab's name, and every tab's, fit. */
private const val DockLabelsActiveWidth = 360
private const val DockLabelsAllWidth = 420

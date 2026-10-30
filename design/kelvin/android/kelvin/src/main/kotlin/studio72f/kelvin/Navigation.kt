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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
            KText(title, Modifier.align(Alignment.Center), Kelvin.type.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                LocalVeilInk.current.takeOrElse { Kelvin.colors.ink }, maxLines = 1)
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

data class DockItem(val label: String, val icon: ImageVector, val badge: Boolean = false)

// The droplet sits the same 6dp from the dock's rim on every side.
private val DockSidePad = 6.dp

/**
 * The floating glass dock. The selection is a liquid droplet: a solid `accent` pill with the
 * tab's icon and label in `on-accent` on it, so the current tab stays plain at every Glass level,
 * Clear included. It slides between
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
) {
    val c = Kelvin.colors
    val g = Kelvin.glass
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

    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
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
                val droplet = Modifier
                    .offset(x = slot * p)
                    .width(slot)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val pop = 1f + 0.16f * l * (0.3f + 0.7f * g.deform)
                        scaleX = pop * (1f + speed)
                        scaleY = pop * (1f - speed * 0.35f)
                    }
                // Resting droplet sits under the icons: solid accent, never thinned by the glass.
                val lifted = if (lens) l else 0f
                Box(droplet.graphicsLayer { alpha = 1f - lifted }.background(c.accent, CapsuleShape))
                Row(Modifier.fillMaxSize().kelvinBackdrop(icons)) {
                    items.forEachIndexed { i, item ->
                        val near = 1f - abs(p - i).coerceAtMost(1f)
                        // On the droplet the tab is on-accent; under the lifted lens, where the
                        // fill has gone, it is the accent itself.
                        val onFill = androidx.compose.ui.graphics.lerp(c.onAccent, c.accent, lifted)
                        val tint = androidx.compose.ui.graphics.lerp(c.glassInk2, onFill, near)
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { this.selected = i == selected }
                                .kelvinPress({ pick(i) }, role = Role.Tab, shape = CapsuleShape),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box {
                                KIcon(item.icon, null, size = 24.dp, color = tint)
                                if (item.badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(8.dp).clip(CircleShape).background(c.accent))
                            }
                            KText(item.label, style = Kelvin.type.micro, color = tint, maxLines = 1)
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
        action?.invoke()
    }
}

/** The accent orb beside the dock for the screen's one creation action. [backdrop] is unused: the orb is solid. */
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
    // The same solid accent pebble as the primary button, so the call to action and the button
    // never differ in colour (stained glass is brightened and tinted by the page behind it).
    Box(
        modifier
            .size(64.dp)
            .kelvinClick(onClick, onClickLabel = contentDescription)
            .pebbleMotion(press)
            .pebble(CircleShape, c.accent, press),
        contentAlignment = Alignment.Center,
    ) { KIcon(icon, contentDescription, size = 26.dp, color = c.onAccent) }
}

/**
 * Text tabs that filter the content below. Every resting tab is a [kelvinPill]: lit glass over
 * content ([backdrop] given), a [lift] pill in a group, with `ink` text. The selection is a solid
 * accent pebble sliding and stretching over them, with `on-accent` text.
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
    Box(modifier.fillMaxWidth().glowRoom().carouselFade(scroll).horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = GlowRoom)) {
        val speed = (abs(x.velocity) / 4000f).coerceAtMost(0.3f) * deform
        Box(
            Modifier
                .offset(x = with(density) { x.value.toDp() })
                .width(with(density) { w.value.toDp() })
                .height(36.dp)
                .graphicsLayer { scaleX = 1f + speed; scaleY = 1f - speed * 0.4f }
                .kelvinPill(selected = true, backdrop, CapsuleShape),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                KText(
                    t,
                    Modifier
                        .height(36.dp)
                        .onGloballyPositioned { bounds[i] = it.positionInParent().x to it.size.width.toFloat(); if (i == tabs.lastIndex) placed = true }
                        .semantics { this.selected = on }
                        .kelvinPress({ onSelect(i) }, role = Role.Tab, shape = CapsuleShape)
                        .then(if (!on) Modifier.kelvinPill(selected = false, backdrop, CapsuleShape) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    style = Kelvin.type.label.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
                    color = kelvinPillInk(on),
                    maxLines = 1,
                )
            }
        }
    }
}


/** Two to four views of the same content on a grey track with an accent thumb that slides, as a chosen [kelvinPill]. */
@Composable
fun KelvinSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    val pos = remember { Animatable(selected.toFloat()) }
    val spatial = Kelvin.motion.spatial<Float>()
    val deform = Kelvin.glass.deform
    LaunchedEffect(selected) { pos.animateTo(selected.toFloat(), spatial) }
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).clip(KelvinShapes.md).background(c.sunken).padding(3.dp)) {
        val slot: Dp = maxWidth / options.size
        val speed = (abs(pos.velocity) * 0.05f).coerceAtMost(0.3f) * deform
        Box(
            Modifier
                .offset(x = slot * pos.value)
                .width(slot)
                .fillMaxHeight()
                .graphicsLayer { scaleX = 1f + speed; scaleY = 1f - speed * 0.3f }
                // Seated in a clipped well: no glow, which the track would cut into a hard band.
                .pebble(ContinuousShape(13.dp), c.accent, glow = false),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().semantics { this.selected = i == selected }
                        .kelvinPress({ onSelect(i) }, role = Role.Tab, shape = ContinuousShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { KText(o, style = Kelvin.type.label, color = if (i == selected) c.onAccent else c.ink, maxLines = 1) }
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
        modifier = modifier.fillMaxWidth().height(44.dp),
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

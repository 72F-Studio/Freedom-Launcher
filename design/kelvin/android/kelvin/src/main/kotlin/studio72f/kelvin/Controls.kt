package studio72f.kelvin
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.Immutable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.nativeCanvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ButtonKind { Primary, Tinted, Secondary, Text, Danger, Glass }

/**
 * Capsule button. One [ButtonKind.Primary] per screen; pair Cancel ([ButtonKind.Secondary]) with
 * the exact action in dialogs; destructive actions are [ButtonKind.Danger]. [ButtonKind.Glass]
 * floats over media or busy content and needs the [backdrop] it sits on; given a [backdrop],
 * [ButtonKind.Primary] becomes stained accent glass.
 */
@Composable
fun KelvinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    large: Boolean = false,
    backdrop: KelvinBackdrop? = null,
) {
    val c = Kelvin.colors
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.accent to c.onAccent
        ButtonKind.Tinted -> c.accentSoft to c.onAccentSoft
        // White on a soft shadow in light mode, like the glass buttons; a grey step in dark.
        ButtonKind.Secondary -> (if (Kelvin.dark) c.sunken else c.lift) to c.ink
        ButtonKind.Text -> Color.Transparent to c.accent
        ButtonKind.Danger -> c.criticalSoft to c.critical
        ButtonKind.Glass -> Color.Transparent to c.ink
    }
    val thaw = rememberThaw()
    val press = rememberGlassPress(thaw)
    // Over a backdrop, Primary is stained accent glass with the same press physics as Glass.
    val stained = kind == ButtonKind.Primary && backdrop != null
    // Every button but Text is a pebble: glass ones domed glass, the rest solid domed stones.
    val surface = if (kind == ButtonKind.Glass) {
        Modifier.glass(backdrop, CapsuleShape, thaw = thaw, motion = press.layerBlock, pebble = press).then(press.gestures)
    } else if (stained) {
        Modifier.glass(backdrop, CapsuleShape, GlassContent.Colored, tint = c.accent, motion = press.layerBlock, pebble = press).then(press.gestures)
    } else if (kind == ButtonKind.Text) {
        Modifier.clip(CapsuleShape)
    } else {
        Modifier.pebbleMotion(press).pebble(CapsuleShape, bg, press)
    }
    Row(
        modifier
            .height(if (large) 56.dp else 48.dp)
            .then(if (kind != ButtonKind.Text) Modifier.kelvinClick(onClick, enabled) else Modifier.kelvinPress(onClick, enabled, shape = CapsuleShape))
            .then(surface)
            .padding(horizontal = if (kind == ButtonKind.Text) 12.dp else 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) KIcon(icon, null, size = 18.dp, color = fg)
        KText(text, style = if (large) Kelvin.type.label.copy(fontSize = Kelvin.type.rowTitle.fontSize) else Kelvin.type.label, color = fg, maxLines = 1)
    }
}

enum class IconButtonKind { Raised, Glass, Plain, Accent }

/**
 * A 40dp circle in a 48dp touch target. Raised on plain canvas, Glass over content (pass the
 * [backdrop]), Plain inside groups, Accent for the one action over media.
 */
@Composable
fun KelvinIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: IconButtonKind = IconButtonKind.Raised,
    backdrop: KelvinBackdrop? = null,
    enabled: Boolean = true,
) {
    val c = Kelvin.colors
    val thaw = rememberThaw()
    val press = rememberGlassPress(thaw)
    val glassy = kind == IconButtonKind.Glass
    Box(
        modifier.size(48.dp).then(
            if (kind != IconButtonKind.Plain) Modifier.kelvinClick(onClick, enabled, contentDescription)
            else Modifier.kelvinPress(onClick, enabled, onClickLabel = contentDescription, shape = CircleShape),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val disc = when (kind) {
            IconButtonKind.Raised -> Modifier.pebbleMotion(press).pebble(CircleShape, c.raised, press)
            IconButtonKind.Glass -> Modifier.glass(backdrop, CircleShape, thaw = thaw, motion = press.layerBlock, pebble = press).then(press.gestures)
            IconButtonKind.Plain -> Modifier
            IconButtonKind.Accent -> Modifier.pebbleMotion(press).pebble(CircleShape, c.accent, press)
        }
        Box(Modifier.size(40.dp).then(disc), contentAlignment = Alignment.Center) {
            KIcon(icon, contentDescription, size = if (kind == IconButtonKind.Plain) 22.dp else 24.dp, color = if (kind == IconButtonKind.Accent) c.onAccent else c.ink)
        }
    }
}

/**
 * On/off switch with a soft knob. Held or dragged, the knob lifts (its shadow deepens), widens
 * toward where it is going, and pulls like taffy past either end before springing home. Tapping anywhere toggles; TalkBack reads it as a switch.
 */
@Composable
fun KelvinSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Kelvin.colors
    val g = Kelvin.glass
    val deform = g.deform
    val wobble = Kelvin.motion.wobble<Float>()
    val snappy = Kelvin.motion.snappy<Float>()
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    val lift = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onCheckedChange)
    val scope = rememberCoroutineScope()
    val source = remember { MutableInteractionSource() }
    val travelPx = with(LocalDensity.current) { 20.dp.toPx() }

    LaunchedEffect(checked) { if (!dragging) pos.animateTo(if (checked) 1f else 0f, wobble) }
    LaunchedEffect(source) {
        source.interactions.collect {
            when (it) {
                is PressInteraction.Press -> scope.launch { lift.animateTo(1f, snappy) }
                else -> if (!dragging) scope.launch { lift.animateTo(0f, wobble) }
            }
        }
    }

    Box(
        modifier
            .size(60.dp, 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else KelvinMotion.DISABLED_ALPHA }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // raw follows the finger; the knob follows it on the rubber band past either end.
                var raw = 0f
                val tracker = VelocityTracker1D(isDataDifferential = false)
                val flick = KelvinMotion.FLICK_VELOCITY.dp.toPx() / travelPx
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; raw = pos.value; tracker.resetTracking(); scope.launch { lift.animateTo(1f, snappy) } },
                    onDragEnd = {
                        dragging = false
                        // A flick picks the side it was thrown toward; a slow release, the nearer one.
                        // The knob lands carrying the finger's speed.
                        val v = tracker.calculateVelocity()
                        val target = if (abs(v) > flick) v > 0f else pos.value > 0.5f
                        scope.launch { lift.animateTo(0f, wobble) }
                        scope.launch { pos.animateTo(if (target) 1f else 0f, wobble, initialVelocity = v) }
                        if (target != current) change(target)
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { lift.animateTo(0f, wobble) }
                        scope.launch { pos.animateTo(if (current) 1f else 0f, wobble) }
                    },
                    onHorizontalDrag = { ch, dx ->
                        ch.consume()
                        raw += dx / travelPx
                        val inside = raw.coerceIn(0f, 1f)
                        val shown = inside + KelvinMotion.rubberBand(raw - inside, 0.6f)
                        tracker.addDataPoint(ch.uptimeMillis, shown)
                        scope.launch { pos.snapTo(shown) }
                    },
                )
            }
            .toggleable(checked, source, indication = null, enabled = enabled, role = Role.Switch) { change(it) },
        contentAlignment = Alignment.Center,
    ) {
        val p = pos.value
        val l = lift.value
        Box(Modifier.size(52.dp, 32.dp)) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight().clip(CapsuleShape)
                    .background(lerp(c.hairline, c.accent, p.coerceIn(0f, 1f))),
            )
            val over = when { p < 0f -> -p; p > 1f -> p - 1f; else -> 0f }
            val knobW = 28.dp + 10.dp * l * (0.4f + 0.6f * deform) + 18.dp * over * deform
            val x = 2.dp + (52.dp - 4.dp - knobW) * p.coerceIn(0f, 1f)
            MoltenKnob(Modifier.offset(x = x, y = 2.dp).size(knobW, 28.dp), heat = l * (0.3f + 0.7f * deform), pop = 1f + 0.12f * l * deform)
        }
    }
}

/**
 * Slider. Thin (a track and a white knob) for settings rows; [thick] (a 52dp control-centre
 * slab with the [icon] inside the fill) for a value people change often. While dragged the knob
 * lifts and stretches with speed; dragging past either end pulls the whole slider like
 * elastic. TalkBack can set it directly.
 */
@Composable
fun KelvinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    thick: Boolean = false,
    icon: ImageVector? = null,
    valueText: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = Kelvin.colors
    val g = Kelvin.glass
    val deform = g.deform
    val wobble = Kelvin.motion.wobble<Float>()
    val snappy = Kelvin.motion.snappy<Float>()
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val knob = remember { Animatable(value) }
    val update by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) knob.animateTo(value, snappy) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(if (thick) 52.dp else 44.dp)
            .semantics {
                this.contentDescription = contentDescription
                valueText?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> update(v.coerceIn(0f, 1f)); true }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val full = maxWidth
        fun fractionAt(x: Float) = x / widthPx
        // Returns the stretch, so a drag can track how fast it is changing.
        fun drag(raw: Float): Float {
            val clamped = raw.coerceIn(0f, 1f)
            val s = KelvinMotion.rubberBand(raw - clamped, 0.1f) * (0.3f + 0.7f * deform)
            scope.launch { stretch.snapTo(s) }
            scope.launch { knob.animateTo(clamped, spring(1f, 2500f)) }
            update(clamped)
            return s
        }
        val input = Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = { o ->
                scope.launch { lift.animateTo(1f, snappy) }
                drag(fractionAt(o.x))
                tryAwaitRelease()
                scope.launch { lift.animateTo(0f, wobble) }
                finished?.invoke()
            })
        }.pointerInput(Unit) {
            var raw = 0f
            val tracker = VelocityTracker1D(isDataDifferential = false)
            detectHorizontalDragGestures(
                onDragStart = { o -> dragging = true; raw = fractionAt(o.x); tracker.resetTracking(); scope.launch { lift.animateTo(1f, snappy) } },
                onDragEnd = {
                    dragging = false
                    // The stretch snaps back carrying the speed it had under the finger.
                    val v = tracker.calculateVelocity()
                    scope.launch { lift.animateTo(0f, wobble) }
                    scope.launch { stretch.animateTo(0f, wobble, initialVelocity = v) }
                    finished?.invoke()
                },
                onDragCancel = { dragging = false; scope.launch { lift.animateTo(0f, wobble) }; scope.launch { stretch.animateTo(0f, wobble) } },
                onHorizontalDrag = { ch, dx -> ch.consume(); raw += dx / widthPx; tracker.addDataPoint(ch.uptimeMillis, drag(raw)) },
            )
        }
        val s = stretch.value
        val elastic = Modifier.graphicsLayer {
            scaleX = 1f + abs(s)
            scaleY = 1f - abs(s) * 0.5f
            transformOrigin = TransformOrigin(if (s < 0f) 1f else 0f, 0.5f)
        }
        val f = knob.value
        if (thick) {
            Box(Modifier.fillMaxWidth().height(52.dp).then(elastic).then(input).clip(KelvinShapes.md).background(c.sunken)) {
                Box(Modifier.fillMaxHeight().width(full * f).background(c.accent))
                if (icon != null) KIcon(icon, null, Modifier.align(Alignment.CenterStart).padding(start = 16.dp), 24.dp, if (f > 0.12f) c.onAccent else c.ink2)
                if (valueText != null) KText(valueText, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), Kelvin.type.label, if (f > 0.88f) c.onAccent else c.ink2)
            }
        } else {
            Box(Modifier.fillMaxWidth().height(44.dp).then(elastic).then(input), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(c.sunken, CapsuleShape))
                    Box(Modifier.width(full * f).height(6.dp).background(c.accent, CapsuleShape))
                }
                val l = lift.value
                val speed = (abs(knob.velocity) / 6f).coerceAtMost(0.4f) * deform
                MoltenKnob(
                    Modifier
                        .offset(x = (full - 28.dp) * f)
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = 1f + speed
                            scaleY = 1f - speed * 0.4f
                        },
                    heat = l * (0.3f + 0.7f * deform),
                    pop = 1f + 0.3f * l * (0.3f + 0.7f * deform),
                )
            }
        }
    }
}

/** End labels for the Glass slider, and the note shown as it nears Clear. */
data class GlassRangeLabels(
    val opaque: String,
    val clear: String,
    val clearWarning: String,
)

/**
 * The Glass setting: one stepless slider, near-opaque glass at the left end and fully clear at
 * the right, with the ends named under it. Every position is valid; nothing snaps. As it nears
 * Clear, where the contrast floor gives way, it shows [GlassRangeLabels.clearWarning]. Persist the
 * level in [onLevelChangeFinished] when saving on every change would be costly.
 */
@Composable
fun KelvinGlassControl(
    level: Float,
    onLevelChange: (Float) -> Unit,
    labels: GlassRangeLabels,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onLevelChangeFinished: (() -> Unit)? = null,
) {
    val percent = "${(level * 100).roundToInt()}%"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KelvinSlider(level, onLevelChange, contentDescription, valueText = "${labels.clear} $percent", onValueChangeFinished = onLevelChangeFinished)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            KText(labels.opaque, Modifier.weight(1f), Kelvin.type.caption, Kelvin.colors.ink2)
            KText(labels.clear, style = Kelvin.type.caption, color = Kelvin.colors.ink2)
        }
        if (level >= GlassLevel.Liquid + 0.08f) {
            KText(labels.clearWarning, Modifier.padding(top = 4.dp), Kelvin.type.summary, Kelvin.colors.caution)
        }
    }
}

/**
 * A white pebble knob that swells by [pop] while held, lifting on a soft shadow that deepens with
 * [heat]. The shadow and the pebble's rim keep it visible on a light track; it has no glow of its own,
 * which haloed it in dark mode.
 */
@Composable
private fun MoltenKnob(modifier: Modifier, heat: Float, pop: Float) {
    Box(
        modifier
            .graphicsLayer { scaleX *= pop; scaleY *= pop }
            .drawBehind {
                // Drawn rather than elevated: system shadows are capped too light to hold a white
                // knob against a white group.
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.TRANSPARENT
                    setShadowLayer((4f + 3f * heat).dp.toPx(), 0f, (1.5f + 1.5f * heat).dp.toPx(),
                        android.graphics.Color.argb(((0.24f + 0.08f * heat) * 255).toInt(), 0, 0, 0))
                }
                val r = size.height / 2f
                drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            }
            .pebble(CapsuleShape, Color.White, glow = false),
    )
}

internal fun Dp.roundPx(density: androidx.compose.ui.unit.Density) = with(density) { toPx().roundToInt() }

/** One swatch on the [KelvinAccentDial]: its colour and the name TalkBack reads for it. */
@Immutable
data class AccentSwatch(val color: Color, val label: String)

/**
 * The accent dial: 40dp swatches 12dp apart, the chosen one ringed and ticked. Swatches never
 * shrink to fit: when there are more than the row holds it scrolls sideways like a carousel,
 * fading at whichever edge has more, and brings the chosen swatch into view. Dims while not
 * [enabled] (for example while wallpaper colours stand in for it). [contentPadding] is the inset
 * at either end; scrolled swatches pass under it to the edge of the row.
 */
@Composable
fun KelvinAccentDial(
    swatches: List<AccentSwatch>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: Dp = 0.dp,
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selected) {
        val visible = state.layoutInfo.visibleItemsInfo
        val fully = visible.any { it.index == selected && it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }
        if (selected in swatches.indices && !fully) state.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .carouselFade(state),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = enabled,
    ) {
        items(swatches.size) { i ->
            val s = swatches[i]
            val on = i == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .then(if (on) Modifier.border(2.dp, s.color, CircleShape).padding(4.dp) else Modifier)
                    .clip(CircleShape).background(s.color)
                    .semantics { this.selected = on; role = Role.RadioButton }
                    .kelvinPress({ onSelect(i) }, enabled, role = Role.RadioButton, onClickLabel = s.label, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // A tick that reads on the swatch, whether it is pale (Graphite in dark mode) or not.
                if (on) KIcon(KelvinIcons.Check, s.label, size = 18.dp, color = if (s.color.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
    }
}

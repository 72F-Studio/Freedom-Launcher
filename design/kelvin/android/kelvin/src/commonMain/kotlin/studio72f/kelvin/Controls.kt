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
 * the exact action in dialogs; destructive actions are [ButtonKind.Danger], a solid red pill
 * with white text. [ButtonKind.Glass]
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
    // Flat has no bevel or glow to carry a soft tint, and the soft fill is nearly the group grey's
    // lightness, so there Tinted is the accent's pastel: a step deeper, its words held to 4.5:1.
    val tinted = if (Kelvin.finish == KelvinFinish.Flat) pastelOf(c.accent).let { it.fill to it.ink } else c.accentSoft to c.onAccentSoft
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.accent to c.onAccent
        ButtonKind.Tinted -> tinted
        // White on a soft shadow in light mode, like the glass buttons; a grey step in dark. Flat has
        // no shadow or outline to hold a white button, so there it is a grey that stands by its fill
        // (the pebble makes a page-white fill the sunken grey in light).
        ButtonKind.Secondary -> (if (!Kelvin.dark) c.lift else if (Kelvin.finish == KelvinFinish.Flat) c.raised else c.sunken) to c.ink
        ButtonKind.Text -> Color.Transparent to c.accent.legibleOn(c.group)
        // Destructive: solid true red with white text, never a soft wash. It is a warning and
        // must read as one at a glance, apart from the accent.
        ButtonKind.Danger -> c.critical to Color.White
        ButtonKind.Glass -> Color.Transparent to c.ink
    }
    val thaw = rememberThaw()
    val press = rememberGlassPress(thaw)
    // Over a backdrop, every filled button is glass stained in its fill, with the same press
    // physics as Glass. Primary is the exception: it carries a white label, and stained glass lifts
    // its fill (measured #5F9FFF for an accent of #3487FE, about 2.6:1 for the label), so it stays a
    // solid pebble in the true accent wherever it sits.
    val stained = kind != ButtonKind.Glass && kind != ButtonKind.Text && kind != ButtonKind.Primary && backdrop != null
    // Every button but Text is a pebble: glass ones domed glass, the rest solid domed stones.
    val surface = if (kind == ButtonKind.Glass) {
        Modifier.glass(backdrop, KelvinShapes.control, thaw = thaw, motion = press.layerBlock, pebble = press).then(press.gestures)
    } else if (stained) {
        Modifier.glass(backdrop, KelvinShapes.control, GlassContent.Colored, tint = bg, motion = press.layerBlock, pebble = press).then(press.gestures)
    } else if (kind == ButtonKind.Text) {
        Modifier.clip(KelvinShapes.control)
    } else {
        Modifier.pebbleMotion(press).pebble(KelvinShapes.control, bg, press)
    }
    Row(
        modifier
            .height(if (large) KelvinSizes.buttonLarge else KelvinSizes.button)
            .then(if (kind != ButtonKind.Text) Modifier.kelvinClick(onClick, enabled) else Modifier.kelvinPull(onClick, enabled, shape = KelvinShapes.control))
            .then(surface)
            .padding(horizontal = if (kind == ButtonKind.Text) KelvinSizes.textButtonPadding else KelvinSizes.buttonPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A call to action (a solid primary or danger button) carries a filled glyph; quieter buttons a line.
        if (icon != null) KIcon(icon, null, size = 18.dp, color = fg, style = IconStyle.of(kind == ButtonKind.Primary || kind == ButtonKind.Danger))
        KText(text, style = if (large) Kelvin.type.action.copy(fontSize = Kelvin.type.rowTitle.fontSize) else Kelvin.type.action, color = fg, maxLines = 1)
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
            // Plain buttons (a control capsule's play and skip) move like the pebbles do.
            else Modifier.kelvinPull(onClick, enabled, onClickLabel = contentDescription, shape = CircleShape),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val disc = when (kind) {
            IconButtonKind.Raised -> if (backdrop != null) Modifier.glass(backdrop, CircleShape, GlassContent.Colored, tint = c.raised, motion = press.layerBlock, pebble = press).then(press.gestures)
                else Modifier.pebbleMotion(press).pebble(CircleShape, c.raised, press)
            IconButtonKind.Glass -> Modifier.glass(backdrop, CircleShape, thaw = thaw, motion = press.layerBlock, pebble = press).then(press.gestures)
            IconButtonKind.Plain -> Modifier
            IconButtonKind.Accent -> if (backdrop != null) Modifier.glass(backdrop, CircleShape, GlassContent.Colored, tint = c.accent, motion = press.layerBlock, pebble = press).then(press.gestures)
                else Modifier.pebbleMotion(press).pebble(CircleShape, c.accent, press)
        }
        Box(Modifier.size(40.dp).then(disc), contentAlignment = Alignment.Center) {
            KIcon(icon, contentDescription, size = if (kind == IconButtonKind.Plain) 22.dp else 24.dp, color = if (kind == IconButtonKind.Accent) c.accent.iconInk() else c.ink, style = IconStyle.of(kind == IconButtonKind.Accent))
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
    contentDescription: String? = null,
) {
    val label = contentDescription ?: LocalControlLabel.current
    val c = Kelvin.colors
    val g = Kelvin.glass
    val deform = g.deform
    val wobble = Kelvin.motion.wobble<Float>()
    val snappy = Kelvin.motion.snappy<Float>()
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    val lift = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(checked)
    val haptics = rememberKelvinHaptics()
    val commit by rememberUpdatedState(onCheckedChange)
    val change: (Boolean) -> Unit = { on -> haptics.toggle(on); commit(on) }
    val scope = rememberCoroutineScope()
    val source = remember { MutableInteractionSource() }
    val travelPx = with(LocalDensity.current) { 18.dp.toPx() }

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
            .size(56.dp, 48.dp)
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
            .then(if (label != null) Modifier.semantics { this.contentDescription = label } else Modifier)
            .toggleable(checked, source, indication = null, enabled = enabled, role = Role.Switch) { change(it) },
        contentAlignment = Alignment.Center,
    ) {
        val p = pos.value
        val l = lift.value
        Box(Modifier.size(KelvinSizes.switchWidth, KelvinSizes.switchHeight)) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight().clip(CapsuleShape)
                    .background(lerp(c.hairline, c.accent, p.coerceIn(0f, 1f))),
            )
            val over = when { p < 0f -> -p; p > 1f -> p - 1f; else -> 0f }
            val knobW = 24.dp + 8.dp * l * (0.4f + 0.6f * deform) + 18.dp * over * deform
            val x = 2.dp + (KelvinSizes.switchWidth - 4.dp - knobW) * p.coerceIn(0f, 1f)
            MoltenKnob(Modifier.offset(x = x, y = 2.dp).size(knobW, 24.dp), heat = l * (0.3f + 0.7f * deform), pop = 1f + 0.12f * l * deform)
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
    val ember = remember(c.accent, c.glassHeat) { thermalColor(0.12f, c.accent, c.glassHeat) }
    val g = Kelvin.glass
    val deform = g.deform
    val wobble = Kelvin.motion.wobble<Float>()
    val snappy = Kelvin.motion.snappy<Float>()
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    // Which way the knob is being slid: -1 left, 1 right, 0 at rest or on a tap. It bends the
    // grip into chevrons and stretches the knob into a pill trailing behind the drag.
    val slide = remember { Animatable(0f) }
    val knob = remember { Animatable(value) }
    val update by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dragging) knob.animateTo(value, snappy) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(if (thick) 48.dp else 44.dp)
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
            // The value is set on a tap, not on touch-down, so a scroll that starts on the slider
            // does not change it.
            detectTapGestures(onTap = { o -> drag(fractionAt(o.x)) }, onPress = { o ->
                scope.launch { lift.animateTo(1f, snappy) }
                val released = tryAwaitRelease()
                // A press that became a drag is cancelled here; the drag owns the lift from then on.
                if (released || !dragging) {
                    scope.launch { lift.animateTo(0f, wobble) }
                    if (released) finished?.invoke()
                }
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
                    scope.launch { slide.animateTo(0f, wobble) }
                    finished?.invoke()
                },
                onDragCancel = { dragging = false; scope.launch { lift.animateTo(0f, wobble) }; scope.launch { stretch.animateTo(0f, wobble) }; scope.launch { slide.animateTo(0f, wobble) } },
                onHorizontalDrag = { ch, dx ->
                    ch.consume(); raw += dx / widthPx; tracker.addDataPoint(ch.uptimeMillis, drag(raw))
                    if (abs(dx) > 0.5f) {
                        val way = if (dx > 0f) 1f else -1f
                        if (slide.targetValue != way) scope.launch { slide.animateTo(way, snappy) }
                    }
                },
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
            Box(Modifier.fillMaxWidth().height(48.dp).then(elastic).then(input).clip(KelvinShapes.md).background(c.sunken)) {
                // Thermal: a filament, an ember at the start heating to the accent at the edge.
                Box(Modifier.fillMaxHeight().width(full * f).background(Brush.horizontalGradient(listOf(ember, c.accent))))
                // The icon and the value change ink as the fill's edge actually passes over them,
                // by how much of each it covers, never at a fixed threshold.
                val edge = full * f
                val iconCover = ((edge - 16.dp) / 24.dp).coerceIn(0f, 1f)
                val valueCover = ((edge - (full - 60.dp)) / 44.dp).coerceIn(0f, 1f)
                val valueInk = lerp(c.ink2, c.onAccent, valueCover)
                val valueMark = lerp(c.figureAccent, c.onAccent, valueCover)
                if (icon != null) KIcon(icon, null, Modifier.align(Alignment.CenterStart).padding(start = 16.dp), 22.dp, lerp(c.ink2, c.accent.iconInk(), iconCover))
                if (valueText != null) KelvinFigure(valueText, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), Kelvin.type.figure, valueInk, mark = valueMark, unit = valueMark, unitWord = valueInk)
            }
        } else {
            Box(Modifier.fillMaxWidth().height(44.dp).then(elastic).then(input), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(c.sunken, CapsuleShape))
                    // Thermal: a filament from an ember through the accent to a white-hot tip at the
                    // knob, which glows brighter while it is held.
                    Box(
                        Modifier.width(full * f).height(6.dp).drawBehind {
                            if (size.width <= 0f) return@drawBehind
                            val tip = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f)
                            val glowR = 14.dp.toPx()
                            drawCircle(
                                Brush.radialGradient(listOf(c.glassHeat.copy(alpha = 0.25f + 0.35f * lift.value), c.glassHeat.copy(alpha = 0f)), center = tip, radius = glowR),
                                radius = glowR, center = tip,
                            )
                            drawRoundRect(
                                Brush.horizontalGradient(0f to ember, 0.55f to c.accent, 1f to c.glassHeat, endX = size.width),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                            )
                        },
                    )
                }
                val l = lift.value
                // A circle at rest and on a tap; while sliding it widens into a pill. Its leading
                // edge stays on the value and the extra width trails away from the drag.
                val way = slide.value
                val extra = 18.dp * abs(way).coerceAtMost(1.2f)
                val trail = if (way > 0f) extra else 0.dp
                MoltenKnob(
                    Modifier
                        .offset(x = (full - 24.dp) * f - trail)
                        .size(24.dp + extra, 24.dp),
                    heat = l * (0.3f + 0.7f * deform),
                    pop = 1f + 0.22f * l * (0.3f + 0.7f * deform),
                    grip = c.onAccentSoft.legibleOn(Color.White, 3f),
                    morph = way,
                )
            }
        }
    }
}

/**
 * End labels for the Glass slider, and the note shown as it nears Clear. Frost's range ends at
 * Sheer and keeps its contrast floor, so it has no warning: pass null.
 */
data class GlassRangeLabels(
    val opaque: String,
    val clear: String,
    val clearWarning: String?,
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
        if (labels.clearWarning != null && level >= GlassLevel.Liquid + 0.08f) {
            KelvinNote(labels.clearWarning, Tone.Caution, Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * A white pebble knob that swells by [pop] while held, lifting on a soft shadow that deepens with
 * [heat]. The shadow and the pebble's rim keep it visible on a light track; it has no glow of its own,
 * which haloed it in dark mode.
 */
@Composable
private fun MoltenKnob(modifier: Modifier, heat: Float, pop: Float, grip: Color? = null, morph: Float = 0f) {
    Box(
        modifier
            .graphicsLayer { scaleX *= pop; scaleY *= pop }
            .drawBehind {
                // Drawn rather than elevated: system shadows are capped too light to hold a white
                // knob against a white group.
                drawCapsuleShadow((4f + 3f * heat).dp.toPx(), (1.5f + 1.5f * heat).dp.toPx(), 0.24f + 0.08f * heat)
            }
            .pebble(CapsuleShape, Color.White, glow = false)
            .drawWithContent {
                drawContent()
                if (grip == null) return@drawWithContent
                // A grip in the accent: two vertical bars at rest ("this moves"), bending into two
                // chevrons pointing the way the knob is being slid as [morph] goes to 1 (right)
                // or -1 (left). Each stroke bends at its middle and stays centred on its place.
                val cx = size.width / 2f
                val cy = size.height / 2f
                val h = 4.2.dp.toPx()
                val bend = 2.8.dp.toPx() * morph.coerceIn(-1f, 1f)
                val gap = 2.6.dp.toPx()
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                for (x0 in floatArrayOf(cx - gap, cx + gap)) {
                    val x = x0 - bend / 2f
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(x, cy - h); lineTo(x + bend, cy); lineTo(x, cy + h) }
                    drawPath(path, grip, style = stroke)
                }
            },
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
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
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
                    .kelvinPull({ onSelect(i) }, enabled, role = Role.RadioButton, onClickLabel = s.label, shape = CircleShape, wash = false),
                contentAlignment = Alignment.Center,
            ) {
                // A tick that reads on the swatch, whether it is pale (Graphite in dark mode) or not.
                if (on) KIcon(KelvinIcons.Check, s.label, size = 18.dp, color = if (s.color.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
    }
}

/**
 * Related controls in one pebble capsule (skip back, play, skip forward), as HyperOS 4 groups a
 * recorder's transport, so a cluster reads as one object instead of a row of loose circles. Put
 * [KelvinIconButton]s of kind Plain inside, and flank it with raised circles in a
 * [KelvinControlBar]. Over content pass the [backdrop] and it is glass in the same tone.
 */
@Composable
fun KelvinControlCapsule(
    modifier: Modifier = Modifier,
    backdrop: KelvinBackdrop? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val c = Kelvin.colors
    // The whole capsule is one pebble: pressed or dragged anywhere, on a button or between them,
    // it swells, is pulled toward the finger and stretches along the drag, then wobbles back.
    // Its buttons still take their taps.
    val press = rememberGlassPress()
    androidx.compose.runtime.CompositionLocalProvider(LocalInControlCapsule provides true) {
        androidx.compose.foundation.layout.Row(
            modifier
                .height(56.dp)
                .then(
                    if (backdrop != null) Modifier.glass(backdrop, KelvinShapes.control, GlassContent.Colored, tint = c.raised, isPebble = true, motion = press.layerBlock, pebble = press).then(press.gestures)
                    else Modifier.pebbleMotion(press).pebble(KelvinShapes.control, c.raised, press),
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * The one-handed control bar at the foot of a screen: a [center] cluster (a [KelvinControlCapsule]
 * or the one primary action) between a [leading] and a [trailing] circle, all within the thumb's
 * reach. Place it at the foot of the screen with `navigationBarsPadding()`; screens with a dock
 * use the dock instead.
 */
@Composable
fun KelvinControlBar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    center: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Box(Modifier.align(Alignment.Center)) { center() }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

package studio72f.kelvin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Signatures: three components that carry Kelvin's identity at sizes people meet constantly.
 * Layered glass tiles for settings icons, a thermal bar chart whose chosen bar is a heated
 * filament, and a slide-to-confirm capsule for consequential actions.
 */

/**
 * A layered glass icon tile, after HyperOS 3 and ColorOS 16 icons: a vivid [color] shape set
 * back toward the top-start, and over it, offset toward the bottom-end, a pane of frosted glass
 * tinted by what it covers, with a light top edge and the white glyph. The back shape shows
 * sharp where the glass does not cover it, so the tile reads as two materials, not a flat fill.
 * Replaces the flat settings tile at [size] (32dp in rows). A glyph on a coloured tile is always filled:
 * the solid shape sits with the tile's colour.
 */
@Composable
fun KelvinGlassTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = KelvinSizes.tile, glyph: Color = Color.White, backAlpha: Float = 1f, frontAlpha: Float = 0.92f) {
    val dark = Kelvin.dark
    val vivid = remember(color) { color.vivid(lift = 0f) }
    Box(
        modifier.size(size).drawBehind {
            val s = this.size.minDimension
            val back = s * 0.74f
            val front = s * 0.8f
            val backShape = ContinuousShape((back * 0.3f).toDp())
            val frontShape = ContinuousShape((front * 0.3f).toDp())
            // The coloured shape, set back toward the top-start.
            drawOutline(backShape.createOutline(Size(back, back), layoutDirection, this), vivid, alpha = backAlpha)
            translate(left = s - front, top = s - front) {
                val glass = frontShape.createOutline(Size(front, front), layoutDirection, this)
                // A soft shadow of the glass falling onto the colour behind it.
                translate(top = s * 0.03f) { drawOutline(glass, Color.Black.copy(alpha = if (dark) 0.22f else 0.08f)) }
                // Frost: the colour it covers, lightened and diffused toward the far corner.
                drawOutline(
                    glass,
                    Brush.linearGradient(
                        0f to lerp(vivid, Color.White, if (dark) 0.28f else 0.38f).copy(alpha = frontAlpha),
                        1f to lerp(vivid, Color.White, if (dark) 0.08f else 0.14f).copy(alpha = frontAlpha),
                        start = Offset.Zero, end = Offset(front, front),
                    ),
                )
                // The glass's top edge catching the light.
                drawOutline(
                    glass,
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.18f to Color.White.copy(alpha = 0f), endY = front),
                )
            }
        },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(Modifier.size(size * 0.8f), contentAlignment = Alignment.Center) {
            KIcon(icon, null, size = size * 0.48f, color = glyph, style = IconStyle.Filled)
        }
    }
}

/** One bar in a [KelvinBarChart]: its [value] (0 to 1 of the chart's height) and its [label]. */
data class KelvinBar(val value: Float, val label: String)

/**
 * A bar chart that leads with its figure, as OriginOS and HyperOS set their data cards. The
 * chosen bar's value sits above the chart as a [KelvinFigure] (`71 AQI`: the number large and
 * light, the unit small) with its [caption] beside it, so the chart answers before it is read.
 * Bars are plain: wide, round-topped, a neutral ink tint at rest and solid `accent` when chosen, rising
 * from a shared floor; an optional [average] (0 to 1, like the bars) is a dashed line across them,
 * labelled at its end with [averageText] ("avg 45").
 * No glow, no gradient, no frost. Tapping a bar picks it with a segment tick; heights grow on the
 * spatial spring and the chosen colour moves on the gentle one.
 */
@Composable
fun KelvinBarChart(
    bars: List<KelvinBar>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    valueText: (Int) -> String = { "" },
    caption: (Int) -> String = { bars[it].label },
    average: Float? = null,
    averageText: String? = null,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KelvinFigure(valueText(selected), Modifier.alignByBaseline(), style = Kelvin.type.numeral)
            KText(caption(selected), Modifier.alignByBaseline(), Kelvin.type.label, c.ink2, maxLines = 1)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(height)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                bars.forEachIndexed { i, bar ->
                    val on = i == selected
                    val grown by animateFloatAsState(bar.value.coerceIn(0.04f, 1f), Kelvin.motion.spatial(), label = "bar")
                    val fill by androidx.compose.animation.animateColorAsState(if (on) c.accent else c.ink.copy(alpha = if (Kelvin.dark) 0.16f else 0.09f), Kelvin.motion.gentle(), label = "bar fill")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                this.selected = on
                                contentDescription = "${bar.label}: ${valueText(i)}"
                            }
                            .kelvinPress({ if (!on) haptics.tick(); onSelect(i) }, role = Role.Tab, shape = KelvinShapes.sm),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.56f)
                                .fillMaxHeight(grown)
                                .drawBehind {
                                    val r = CornerRadius(minOf(size.width / 2f, 10.dp.toPx()))
                                    val foot = CornerRadius(3.dp.toPx())
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r, r, foot, foot))
                                    }
                                    drawPath(path, fill)
                                },
                        )
                    }
                }
            }
            if (average != null) {
                val ink = c.ink3
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val y = size.height * (1f - average.coerceIn(0f, 1f))
                        drawLine(
                            ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        )
                    },
                )
                // The line says what it is where it sits: a dashed line alone is a mark to decode.
                if (averageText != null) {
                    KText(
                        averageText,
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = height * (1f - average.coerceIn(0f, 1f)) - 9.dp)
                            .background(c.group, KelvinShapes.pill)
                            .padding(horizontal = 6.dp),
                        Kelvin.type.caption.copy(fontWeight = FontWeight.SemiBold), c.ink2, maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEachIndexed { i, bar ->
                val on = i == selected
                val labelInk by androidx.compose.animation.animateColorAsState(if (on) c.ink else c.ink2, Kelvin.motion.gentle(), label = "day")
                KText(
                    // With large text a day keeps its first letter, so the row never truncates.
                    if (largeText()) bar.label.take(1) else bar.label, Modifier.weight(1f), Kelvin.type.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    labelInk, align = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

/**
 * Slide to confirm, for consequential actions where a dialog feels flimsy (delete an album,
 * sign out, reset). A white knob at the start of a grey capsule carries the action's [icon];
 * dragged toward the end it heats the track behind it (an ember through the accent, or
 * `critical` for [destructive] actions, to a glowing edge at the knob) while [text] fades.
 * Released past 85% it runs to the end, confirms with a haptic and calls [onConfirm]; short of
 * that it springs home, carrying the finger's speed, and pulled past the end it stretches on
 * Kelvin's rubber band. TalkBack offers it as a single action.
 */
@Composable
fun KelvinSlideToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = KelvinIcons.ChevronRight,
    destructive: Boolean = false,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val scope = rememberCoroutineScope()
    val confirm by rememberUpdatedState(onConfirm)
    val travel = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    val wobble = Kelvin.motion.wobble<Float>()
    val spatial = Kelvin.motion.spatial<Float>()
    val hot = if (destructive) c.critical else c.accent
    val ember = remember(hot, c.glassHeat) { thermalColor(0.12f, hot, c.glassHeat) }
    val knobSize = 48.dp
    val dark = Kelvin.dark
    // The track follows the theme: the sunken grey on a light page, the raised grey on a dark one.
    // The knob starts in the action's colour (accent, or red when destructive) and the colours
    // trade places as it travels: the knob blends toward white while the track behind it blends
    // from grey to the action's colour. Halfway both are a midway tone; at the end the knob is white
    // on a track that is all colour. The same in both themes.
    val track = if (dark) c.lift else c.sunken
    val iconOnHot = hot.iconInk()
    val iconOnWhite = hot.legibleOn(Color.White, 3f)
    val onTrack = if (dark) Color.White else c.ink
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(56.dp.withTextScale(Kelvin.type.action.lineHeight))
            .semantics(mergeDescendants = true) {
                contentDescription = text
                onClick(label = text) { haptics.confirm(); confirm(); true }
            }
            .drawBehind { drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f)) },
    ) {
        val density = LocalDensity.current
        val pad = with(density) { 4.dp.toPx() }
        val span = (constraints.maxWidth - with(density) { knobSize.toPx() } - 2 * pad).coerceAtLeast(1f)
        val t = travel.value
        // How far into the slide: the knob has turned to ink within the first few percent.
        val p = t.coerceIn(0f, 1f)
        val knobFill = lerp(hot, Color.White, p)
        // The icon is white on the coloured knob and the action's colour on the white one,
        // crossing over around the middle of the slide.
        val iconInk = lerp(iconOnHot, iconOnWhite, smoothstep(0.35f, 0.65f, p))
        // The heated track behind the knob.
        Box(
            Modifier.fillMaxHeight().fillMaxWidth().drawBehind {
                val w = pad * 2 + with(density) { knobSize.toPx() } + span * t.coerceIn(0f, 1f)
                if (t <= 0.001f) return@drawBehind
                drawRoundRect(lerp(track, hot, p), size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2f))
            },
        )
        Row(
            // Clear of the knob, and ellipsised rather than clipped when text is large.
            Modifier.align(Alignment.Center).padding(start = 60.dp, end = 16.dp).graphicsLayer { alpha = 1f - smoothstep(0.05f, 0.5f, t) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KText(text, Modifier.weight(1f, fill = false), Kelvin.type.action, onTrack, maxLines = 1)
            KText("›››", style = Kelvin.type.action, color = onTrack.copy(alpha = 0.6f), maxLines = 1)
        }
        Box(
            Modifier
                // Centred on the track, which grows taller with large text.
                .align(Alignment.CenterStart)
                .padding(4.dp)
                .offset { androidx.compose.ui.unit.IntOffset((span * t).roundToInt(), 0) }
                .size(knobSize)
                .pebble(CircleShape, knobFill)
                .pointerInput(span) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (travel.value >= 0.85f && !done) {
                                    done = true
                                    travel.animateTo(1f, spatial)
                                    haptics.confirm()
                                    confirm()
                                    kotlinx.coroutines.delay(600)
                                    travel.animateTo(0f, wobble)
                                    done = false
                                } else travel.animateTo(0f, wobble)
                            }
                        },
                        onDragCancel = { scope.launch { travel.animateTo(0f, wobble) } },
                        onHorizontalDrag = { ch, dx ->
                            ch.consume()
                            val raw = travel.value + dx / span
                            // Past the end it stretches on the rubber band instead of stopping dead.
                            val next = if (raw > 1f) 1f + KelvinMotion.rubberBand(raw - 1f, 0.08f) else raw.coerceAtLeast(0f)
                            scope.launch { travel.snapTo(next) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // The knob's icon is coloured from the start (accent, or red when destructive) so the knob
            // reads as the thing to drag, and keeps 3:1 on the knob as it turns.
            KIcon(icon, null, size = 22.dp, color = iconInk)
        }
    }
}

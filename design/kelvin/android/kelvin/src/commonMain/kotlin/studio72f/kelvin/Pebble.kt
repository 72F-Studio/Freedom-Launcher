package studio72f.kelvin

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/*
 * Pebbles, after HyperOS 4's: frosted, matte stones whose light lives in the bevel. The face is
 * true to its colour; light soaks into the rounded edge and glows softly just inside it, a little
 * more along the top, under a faint broad sheen. Kelvin's own trace is a thread of the accent's
 * colour temperature (`glass-heat`) along the bottom rim, warm for Ember, cool for Glacier. A
 * solid pebble's colour thins toward its edge (Beer-Lambert). Beneath it, a whisper of shadow
 * falls straight down, a faint haze of its own colour under a vivid fill.
 */

/** AGSL: a drop's thickness profile. Shared by glass and solid pebbles. */
internal const val PebbleLight = """
// Relative thickness of the drop at distance [d] inside its edge, for a rim [width] wide: a
// quarter circle that meets the page vertically (the tucked-in edge), flat beyond the rim.
float dropThickness(float d, float width) {
    float x = 1.0 - clamp(d / max(width, 0.5), 0.0, 1.0);
    return sqrt(max(1.0 - x * x, 0.0));
}

// Fixed fine grain, so a pebble reads as fired matter rather than a flat fill.
float kGrain(float2 p) {
    p = fract(p * float2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

// Kelvin's pebble finish, after HyperOS 4's frosted pebbles: a matte face true to its colour,
// with all the light in the bevel. Light soaks into the rounded edge and glows softly just inside
// it, a little more along the top; a faint, broad sheen sits over the upper face. Heat is only a
// thread: light at the accent's colour temperature ([heat]) leaking in along the bottom rim
// ([heatAmt] 0 to leave it out). [d] is the distance inside the edge, [g] points out through it,
// [y] runs 0 at the top to 1 at the bottom, [h] is the smaller side, [px] one dp, [strength]
// gentler in dark mode. [rimLight] 1 draws the liquid-glass rim stroke, for the Liquid texture
// only.
float3 pebbleFinish(float3 c, float d, float2 g, float y, float h, float px, float strength, float rimLight,
                    float3 heat, float heatAmt, float2 coord) {
    float bevel = 1.0 - smoothstep(0.0, min(h * 0.22, px * 9.0), d);
    // The bevel glow: light gathered in the rounded edge, stronger toward the top.
    float glow = bevel * bevel * (0.035 + 0.045 * max(-g.y, 0.0));
    float sheen = 0.025 * (1.0 - smoothstep(0.0, 0.55, y)) * (1.0 - bevel);
    float stroke = rimLight * 0.62 / max(strength, 0.01) * abs(dot(g, float2(0.7071, 0.7071))) * (1.0 - smoothstep(px * 0.25, px * 1.0, d));
    c = 1.0 - (1.0 - c) * (1.0 - (glow + sheen + stroke) * strength);
    // A barely-there settle where the bottom edge tucks under.
    c *= 1.0 - 0.025 * max(g.y, 0.0) * bevel * strength;
    // Heat: a thread of the accent's temperature along the bottom rim.
    float thread = pow(max(g.y, 0.0), 1.5) * exp(-d / max(px * 2.5, 1.0));
    c = mix(c, heat, clamp(heatAmt * thread, 0.0, 1.0));
    c += (kGrain(floor(coord / max(px * 0.75, 1.0))) - 0.5) * 0.006 * strength;
    return clamp(c, 0.0, 1.0);
}
"""

private const val PEBBLE_AGSL = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float rimWidth;
uniform float px;
uniform float strength;
uniform float rim;
uniform float3 heat;
uniform float heatAmt;

$PaneSdf
$PebbleLight

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float r = min(radiusAt(p, cornerRadii), min(halfSize.x, halfSize.y));
    float d = -min(sdPane(p, halfSize, r), 0.0);
    // Beer-Lambert: opacity follows the path through the drop, opaque in the full middle and
    // thinning toward the edge, where the page behind shows through the colour.
    float path = 0.35 + 0.65 * dropThickness(d, rimWidth);
    float a = 1.0 - pow(0.004, path);
    float2 g = normalAt(p, halfSize, min(r * 1.5, min(halfSize.x, halfSize.y)));
    float3 c = pebbleFinish(float3(color.rgb), d, g, coord.y / size.y, min(size.x, size.y), px, strength, 0.0, heat, heatAmt, coord);
    // The rim light sits on the drop's surface, over its translucent edge, so it reads on vivid
    // fills too rather than fading into the page with the edge.
    float light = rim * 0.62 * abs(dot(g, float2(0.7071, 0.7071))) * (1.0 - smoothstep(px * 0.25, px * 0.8, d));
    float3 lit = 1.0 - (1.0 - c) * (1.0 - light);
    float outA = a + light * (1.0 - a);
    return half4(half3(lit * a + light * (1.0 - a)), half(outA)) * color.a;
}
"""

/** How wide a pebble's rim is: a fifth of its smaller side, at most 7dp. */
internal fun pebbleRim(minSide: Float, density: Float): Float = minOf(minSide * 0.2f, 7f * density)

/**
 * Draws this element as a solid Kelvin pebble in [color], clipped to [shape]: a drop of coloured
 * matter whose colour thins toward its tucked-in edge. A vivid fill is drawn in its vivid version
 * on a glow in its own colour, the solid twin of the stained action orb; a pale or neutral one sits
 * on a faint neutral shadow instead, since a pale glow reads as haze on any ground but white. Pass
 * [glow] false for parts of a control (switch and slider knobs) that should not glow at all. Add
 * [pebbleMotion] for the press physics. Below Android 13 it is a plain fill on the shadow.
 *
 * A pale or neutral fill (white, grey, a soft tint) has a plain face: the bevel's light has nothing
 * to show against on a near-white stone, where it read as a puffy, smudged edge. It is a clean fill
 * with a fine edge on the same whisper of shadow. Under [KelvinFinish.Flat] every fill is plain,
 * with no shadow and no outline: a neutral one as light as the page takes the sunken grey, so it
 * stands by its fill on the page and in a group, and a press dims it in place.
 */
fun Modifier.pebble(shape: Shape, color: Color, press: GlassPress? = null, glow: Boolean = true): Modifier = composed {
    val dark = Kelvin.dark
    val colors = Kelvin.colors
    val heat = colors.glassHeat
    val flat = Kelvin.finish == KelvinFinish.Flat
    // Frost is matte: its pebbles keep their body and shadow but draw no rim-light stroke.
    val matte = Kelvin.glass.isFrost
    // How colourful the fill is, 0 for greys and white to 1 for the accent: it decides between the
    // coloured glow and the neutral shadow, and how much finish the face takes in dark mode.
    val vividness = remember(color) { smoothstep(0.05f, 0.12f, color.chroma()) }
    // Chroma pushed to the edge of sRGB at the same lightness: brightening a light accent such as
    // Glacier would wash out the white label.
    // Flat is strokeless: a neutral stone that would vanish on a light page is the sunken grey.
    val neutral = glow && color.chroma() < 0.02f
    val sunk = colors.sunken
    val fill = remember(color, vividness, flat, dark, sunk) {
        when {
            flat && neutral && !dark && color.luminance() > sunk.luminance() -> sunk
            vividness >= 1f -> color.vivid(lift = 0f)
            else -> color
        }
    }
    // One shader per pebble for its whole life: parsing the AGSL source is the expensive part, so
    // it is never rebuilt when the pebble resizes or its colour changes; only uniforms update.
    // Only a coloured stone is lit in the bevel; pale and neutral ones, and every Flat one, are plain.
    val plain = flat || vividness <= 0f
    val shader = remember(plain) { if (plain) null else pebblePainter(PEBBLE_AGSL) }
    // A grey or white fill needs an edge to stand on a page of nearly its own tone. Knobs (no
    // glow) sit on a track that already shows them.
    val edge = when {
        !neutral || flat -> null
        plain -> colors.glassEdge
        else -> null
    }
    this.then(if (!glow || flat) Modifier else Modifier.drawWithCache {
        // The light comes from the top-left, so the glow falls to the south-east.
        // A whisper of shadow, as under HyperOS 4's white buttons: light from straight above, so
        // it falls straight down; a vivid fill's is a faint haze of its own colour.
        val glowColor = if (vividness > 0f) fill.copy(alpha = (if (dark) 0.3f else 0.22f) * vividness)
            else Color.Black.copy(alpha = if (dark) 0.3f else 0.055f)
        val radius = if (vividness > 0f) 8.dp.toPx() else 10.dp.toPx()
        val glowPath = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val glow = pathShadowPainter(glowPath, radius, 3.dp.toPx(), glowColor)
        onDrawBehind { glow() }
    })
    .drawWithCache {
        val color = fill
        // Light adds up toward white, so a pale fill takes much less of it than a vivid one.
        val lift = 1f - 0.85f * color.luminance()
        val outline = shape.createOutline(size, layoutDirection, this)
        val radii = shape.paneRadii(size, layoutDirection, this)
        onDrawBehind {
            if (plain) {
                drawOutline(outline, color)
                if (edge != null) drawInsetEdge(shape, edge, 0.5.dp.toPx())
                press?.run { if (flat) drawPressDim(outline, colors.ink) else drawPressLight(outline, lift) }
                return@onDrawBehind
            }
            if (shader == null) { drawOutline(outline, color); press?.run { drawPressLight(outline, lift) }; return@onDrawBehind }
            // Gentler on dark, where light carries further; stronger on light, where it competes
            // with a light page. Grey stones in dark mode take least: their top glow read as a
            // lit edge against the dark group. Knobs (no glow) take only a hint of heat, so a switch
            // never reads as lit.
            with(shader) {
                draw(
                    outline = outline,
                    radii = radii,
                    color = color,
                    rimWidth = pebbleRim(size.minDimension, density),
                    strength = if (dark) 0.25f + 0.3f * vividness else 1f,
                    rim = if (matte) 0f else if (dark) 0.2f + 0.3f * vividness else 0.8f,
                    heat = heat,
                    heatAmount = heatThread(dark) * (if (glow) 1f else 0f),
                )
            }
            press?.run { drawPressLight(outline, lift) }
        }
    }
}

/**
 * How much of the accent's temperature shows along a control's bottom rim: a hint that finishes
 * the stone, never the first thing seen on it. Dark takes more than light because the thread is
 * the only warm thing on a dark control, but not so much that a row of buttons reads as lit.
 */
internal fun heatThread(dark: Boolean): Float = if (dark) 0.16f else 0.09f

/** The press physics of [press] applied to this element, for pebbles without glass. */
fun Modifier.pebbleMotion(press: GlassPress): Modifier = graphicsLayer(press.layerBlock).then(press.gestures)

/**
 * How far a chosen pill's glow reaches past it: its 10dp blur plus the 4dp it falls south-east,
 * with a little to spare, so it fades out before any edge rather than being cut off.
 */
val GlowRoom = 16.dp

/**
 * Room for a carousel's edges: the row is laid out [vertical] taller above and below and
 * [horizontal] wider either side but takes only its original space, so the glow under a chosen
 * pill is never cut off and the carousel can run past its container's padding to the edge of
 * the card or screen. Pad the row's content by the same amounts (LazyRow `contentPadding`, or
 * `padding` inside a `horizontalScroll`).
 */
fun Modifier.glowRoom(vertical: androidx.compose.ui.unit.Dp = GlowRoom, horizontal: androidx.compose.ui.unit.Dp = 0.dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val ev = vertical.roundToPx()
        val eh = horizontal.roundToPx()
        val loose = constraints.copy(
            minWidth = constraints.minWidth + 2 * eh,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * eh else constraints.maxWidth,
            minHeight = constraints.minHeight + 2 * ev,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * ev else constraints.maxHeight,
        )
        val p = measurable.measure(loose)
        layout((p.width - 2 * eh).coerceAtLeast(0), (p.height - 2 * ev).coerceAtLeast(0)) { p.place(-eh, -ev) }
    },
)

/**
 * A sideways row of chips, pills or swatches as a Kelvin carousel: scrolls with [scroll] (never faded at
 * its edges: a chip cut off by the edge is what says the row scrolls), leaves room for a chosen pill's glow, and insets its content by
 * [inset]. [bleed] lets it run that far past its container's padding on either side (pass the
 * container's horizontal padding so the carousel meets the card or screen edge).
 */
fun Modifier.kelvinCarousel(
    scroll: androidx.compose.foundation.ScrollState,
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    bleed: androidx.compose.ui.unit.Dp = 0.dp,
): Modifier = this
    .glowRoom(GlowRoom, bleed)
    .then(Modifier.horizontalScroll(scroll))
    .padding(horizontal = inset, vertical = GlowRoom)

/**
 * Click plus the buttons' press physics, for anything pressable that is not a filled pebble: plain
 * icon buttons (a control capsule's play and skip), Text buttons, action bar items, filter pills
 * and swatches. Under the finger it swells, is pulled toward it and stretches along the pull, then
 * springs back with a wobble on release, exactly as a pebble does. [wash] lays a faint ink tint in
 * [shape] while held, for controls with no fill of their own to press into.
 */
fun Modifier.kelvinPull(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    shape: Shape = CircleShape,
    wash: Boolean = true,
): Modifier = composed {
    val press = rememberGlassPress()
    // Inside a control capsule the capsule moves as one pebble; its buttons only wash.
    val moves = !LocalInControlCapsule.current
    val ink = Kelvin.colors.ink
    this
        .clickable(interactionSource = null, indication = null, enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = onClick)
        .graphicsLayer { alpha = if (enabled) 1f else KelvinMotion.DISABLED_ALPHA }
        .then(if (enabled && moves) Modifier.graphicsLayer(press.layerBlock).then(press.gestures) else if (enabled) press.gestures else Modifier)
        .then(
            if (wash) Modifier.drawBehind {
                val p = press.value
                if (p > 0.01f) drawOutline(shape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.07f * p))
            } else Modifier,
        )
}

/** Inside a [KelvinControlCapsule]: its buttons leave the press physics to the capsule. */
internal val LocalInControlCapsule = androidx.compose.runtime.staticCompositionLocalOf { false }

package studio72f.kelvin

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidPath
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
 * Pebbles. Kelvin's buttons sit on the page like drops of water: a flat face true to its colour,
 * with all the depth at the edge, where the surface rolls over and tucks under. Nothing is
 * reflects on the face; a soft shine, a light inner glow and fine stroke on the top rim and an
 * inner shadow along the bottom give it body. On glass the steep, tucked rim bends the real
 * page behind it (Snell in OPTIC_AGSL). A solid pebble is a drop of coloured matter: its colour builds up
 * with the thickness of the drop (Beer-Lambert), so the thinning rim turns translucent over
 * whatever is really behind it. A soft glow beneath lifts it off the page.
 */

/** AGSL: a drop's thickness profile. Shared by glass and solid pebbles. */
internal const val PebbleLight = """
// Relative thickness of the drop at distance [d] inside its edge, for a rim [width] wide: a
// quarter circle that meets the page vertically (the tucked-in edge), flat beyond the rim.
float dropThickness(float d, float width) {
    float x = 1.0 - clamp(d / max(width, 0.5), 0.0, 1.0);
    return sqrt(max(1.0 - x * x, 0.0));
}

// The finish that lifts a pebble off the page: a faint shine over the upper face, a little inner
// glow in the top-left, the liquid-glass rim light ([rimLight] 1 to draw it here; glass gets it
// from Backdrop's highlight), and an inner shadow toward the bottom-right. [d] is the distance inside the edge, [g] points out through it, [y] runs 0 at
// the top to 1 at the bottom, [px] is one dp, [strength] is gentler in dark mode.
float3 pebbleFinish(float3 c, float d, float2 g, float y, float h, float px, float strength, float rimLight) {
    float shine = 0.035 * (1.0 - smoothstep(0.0, 0.6, y));
    // A little inner glow tucked into the top-left, where the light comes from.
    float topLeft = max(dot(g, float2(-0.7071, -0.7071)), 0.0);
    float glow = 0.07 * pow(topLeft, 1.5) * (1.0 - smoothstep(0.0, h * 0.3, d));
    // Liquid-glass rim light: a fine line along the edge, brightest where the edge faces the
    // 45-degree light or directly away from it (two opposite corners), as in Kyant's Backdrop.
    float stroke = rimLight * 0.62 / max(strength, 0.01) * abs(dot(g, float2(0.7071, 0.7071))) * (1.0 - smoothstep(px * 0.25, px * 1.0, d));
    // ...and the inner shadow sits opposite, toward the bottom-right.
    float bottomRight = max(dot(g, float2(0.7071, 0.7071)), 0.0);
    float shadow = 0.025 * bottomRight * (1.0 - smoothstep(0.0, h * 0.3, d));
    c *= 1.0 - shadow * strength;
    return 1.0 - (1.0 - c) * (1.0 - (shine + glow + stroke) * strength);
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
    float3 c = pebbleFinish(float3(color.rgb), d, g, coord.y / size.y, min(size.x, size.y), px, strength, 0.0);
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
 */
fun Modifier.pebble(shape: Shape, color: Color, press: GlassPress? = null, glow: Boolean = true): Modifier = composed {
    val dark = Kelvin.dark
    // How colourful the fill is, 0 for greys and white to 1 for the accent: it decides between the
    // coloured glow and the neutral shadow, and how much finish the face takes in dark mode.
    val vividness = remember(color) { smoothstep(0.05f, 0.12f, color.chroma()) }
    // Chroma pushed to the edge of sRGB at the same lightness: brightening a light accent such as
    // Glacier would wash out the white label.
    val fill = remember(color, vividness) { if (vividness >= 1f) color.vivid(lift = 0f) else color }
    this.then(if (!glow) Modifier else Modifier.drawWithCache {
        // The light comes from the top-left, so the glow falls to the south-east.
        val glowColor = if (vividness > 0f) fill.copy(alpha = (if (dark) 0.5f else 0.42f) * vividness)
            else Color.Black.copy(alpha = if (dark) 0.3f else 0.1f)
        val radius = if (vividness > 0f) 10.dp.toPx() else 5.dp.toPx()
        val glowPath = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }.asAndroidPath()
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(radius, if (vividness > 0f) 2.dp.toPx() else 0f, if (vividness > 0f) 4.dp.toPx() else 2.dp.toPx(), glowColor.toArgb())
        }
        onDrawBehind { drawIntoCanvas { it.nativeCanvas.drawPath(glowPath, glowPaint) } }
    })
    .drawWithCache {
        val color = fill
        // Light adds up toward white, so a pale fill takes much less of it than a vivid one.
        val lift = 1f - 0.85f * color.luminance()
        val outline = shape.createOutline(size, layoutDirection, this)
        val radii = shape.paneRadii(size, layoutDirection, this)
        val shader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(PEBBLE_AGSL) else null
        onDrawBehind {
            if (Build.VERSION.SDK_INT < 33 || shader == null) { drawOutline(outline, color); press?.run { drawPressLight(outline, lift) }; return@onDrawBehind }
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("cornerRadii", radii)
            shader.setColorUniform("color", android.graphics.Color.valueOf(color.red, color.green, color.blue, color.alpha))
            shader.setFloatUniform("rimWidth", pebbleRim(size.minDimension, density))
            shader.setFloatUniform("px", density)
            // Gentler on dark, where light carries further; stronger on light, where it competes
            // with a light page. Grey stones in dark mode take least: their top glow read as a
            // lit edge against the dark group.
            shader.setFloatUniform("strength", if (dark) 0.25f + 0.3f * vividness else 1f)
            shader.setFloatUniform("rim", if (dark) 0.2f + 0.3f * vividness else 0.8f)
            drawOutline(outline, ShaderBrush(shader))
            press?.run { drawPressLight(outline, lift) }
        }
    }
}

/** The press physics of [press] applied to this element, for pebbles without glass. */
fun Modifier.pebbleMotion(press: GlassPress): Modifier = graphicsLayer(press.layerBlock).then(press.gestures)

/**
 * Room for a carousel's edges: the row is laid out [vertical] taller above and below and
 * [horizontal] wider either side but takes only its original space, so the glow under a chosen
 * pill is never cut off and the carousel can run past its container's padding to the edge of
 * the card or screen. Pad the row's content by the same amounts (LazyRow `contentPadding`, or
 * `padding` inside a `horizontalScroll`).
 */
fun Modifier.glowRoom(vertical: androidx.compose.ui.unit.Dp = 12.dp, horizontal: androidx.compose.ui.unit.Dp = 0.dp): Modifier = this.then(
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

/** A sideways carousel's edges: fades out at whichever end has more to scroll to. */
fun Modifier.carouselFade(state: androidx.compose.foundation.ScrollState): Modifier =
    carouselFade({ state.canScrollBackward }, { state.canScrollForward })

/** A sideways carousel's edges: fades out at whichever end has more to scroll to. */
fun Modifier.carouselFade(state: androidx.compose.foundation.lazy.LazyListState): Modifier =
    carouselFade({ state.canScrollBackward }, { state.canScrollForward })

private fun Modifier.carouselFade(start: () -> Boolean, end: () -> Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fs = start()
        val fe = end()
        if (!fs && !fe) return@drawWithContent
        val f = (32.dp.toPx() / size.width).coerceAtMost(0.5f)
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to if (fs) Color.Transparent else Color.Black, f to Color.Black,
                1f - f to Color.Black, 1f to if (fe) Color.Transparent else Color.Black,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/**
 * A sideways row of chips, pills or swatches as a Kelvin carousel: scrolls with [scroll], fades
 * at the end that has more, leaves room for a chosen pill's glow, and insets its content by
 * [inset]. [bleed] lets it run that far past its container's padding on either side (pass the
 * container's horizontal padding so the carousel meets the card or screen edge).
 */
fun Modifier.kelvinCarousel(
    scroll: androidx.compose.foundation.ScrollState,
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    bleed: androidx.compose.ui.unit.Dp = 0.dp,
): Modifier = this
    .glowRoom(12.dp, bleed)
    .carouselFade(scroll)
    .then(Modifier.horizontalScroll(scroll))
    .padding(horizontal = inset, vertical = 12.dp)

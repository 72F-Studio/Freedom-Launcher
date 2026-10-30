package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The person's Glass setting, a stepless 0 to 1: near-opaque frosted glass at 0, fully
 * transparent at 1. The named points are reference marks on that range, not stops. Frosted is
 * the accessible default; Clear gives up the contrast floor and is labelled as hard to read.
 * Fully opaque panes come only from accessibility (high-contrast text), never from the slider.
 */
object GlassLevel {
    const val Opaque = 0f
    @Deprecated("The slider is stepless; its left end is near-opaque glass.", ReplaceWith("GlassLevel.Opaque"))
    const val Solid = 0f
    const val Frosted = 0.3f
    const val Glass = 0.55f
    const val Liquid = 0.8f
    const val Clear = 1f
}

/**
 * Everything Kelvin glass needs, derived from the Glass level after accessibility caps. The
 * pane is modelled as real glass: [thicknessDp] and [bevelDp] shape it (flat on top, rounding
 * to the rim), [ior] and [dispersion] set how it bends light, [frostDp] how rough its surface
 * is, [tintAlpha] how much `glass-tint` coats it. [floor] protects text contrast (1 up to
 * Liquid, 0 at Clear); [horizon] is light carried in through the bottom edge and scattered by
 * the frost; [deform] is how far controls stretch and wobble.
 */
@Immutable
data class GlassParams(
    val level: Float,
    val frostDp: Float,
    val tintAlpha: Float,
    val thicknessDp: Float,
    val bevelDp: Float,
    val ior: Float,
    val dispersion: Float,
    val floor: Float,
    val horizon: Float,
    val saturation: Float,
    val deform: Float,
    /** Opacity of the stain in the thick middle of coloured glass. */
    val stainAlpha: Float = 1f,
    val solid: Boolean = false,
    /** How milky light glass stays over dark content: 1 toward the opaque end, 0 from Liquid. */
    val milk: Float = 0f,
) {
    /** An opaque pane with no glass, forced by high-contrast text. */
    val isSolid: Boolean get() = solid
}

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Piecewise smooth curve through (level, value) stops. */
private fun curve(level: Float, vararg stops: Pair<Float, Float>): Float {
    for (i in 0 until stops.size - 1) {
        val (a, va) = stops[i]
        val (b, vb) = stops[i + 1]
        if (level <= b) return va + (vb - va) * smoothstep(a, b, level)
    }
    return stops.last().second
}

fun Float.resolve(a11y: KelvinAccessibility): GlassParams {
    var level = coerceIn(0f, 1f)
    if (a11y.powerSave) level = level.coerceAtMost(GlassLevel.Frosted)
    val f = GlassLevel.Frosted
    val g = GlassLevel.Glass
    val l = GlassLevel.Liquid
    // One continuous range: from the left end (a dense coat over well-ground glass) the coat
    // thins and the frost clears while the pane thickens and bends more, out to bare glass.
    return GlassParams(
        level = level,
        frostDp = curve(level, 0f to 24f, f to 20f, g to 10f, l to 4f, 1f to 0f),
        tintAlpha = curve(level, 0f to 0.94f, f to 0.70f, g to 0.42f, l to 0.16f, 1f to 0f),
        thicknessDp = curve(level, 0f to 3f, f to 6f, g to 12f, l to 18f, 1f to 22f),
        bevelDp = curve(level, 0f to 8f, f to 10f, g to 14f, l to 16f, 1f to 18f),
        ior = curve(level, 0f to 1.35f, f to 1.4f, g to 1.47f, l to 1.5f, 1f to 1.52f),
        dispersion = curve(level, 0f to 0f, f to 0f, g to 0.01f, l to 0.02f, 1f to 0.03f),
        floor = curve(level, 0f to 1f, l to 1f, 1f to 0f),
        horizon = curve(level, 0f to 0.3f, f to 0.55f, l to 0.9f, 1f to 1f),
        // Frost scatters colour as well as light; like HyperOS and ColorOS glass, what shows
        // through stays vivid rather than greying out.
        saturation = curve(level, 0f to 1.25f, f to 1.35f, l to 1.25f, 1f to 1.15f),
        milk = curve(level, 0f to 1f, f to 1f, g to 0.4f, l to 0f, 1f to 0f),
        // Coloured controls keep a stain to the very end, so a CTA still reads as one.
        stainAlpha = curve(level, 0f to 0.99f, f to 0.97f, g to 0.94f, l to 0.9f, 1f to 0.84f),
        deform = if (a11y.reduceMotion) 0f else 0.15f + 0.85f * smoothstep(f, l, level),
        solid = a11y.highContrastText,
    )
}


/**
 * Content that glass can see through: the page under the shelf, a photo under a badge. Kelvin
 * glass is drawn by the Backdrop library (Kyant, Apache 2.0), which records this content with
 * padding and keeps it aligned while glass moves or deforms; Kelvin's own shaders run on top.
 */
@Stable
class KelvinBackdrop internal constructor(
    internal val layer: LayerBackdrop,
    internal val graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    private val groundState: androidx.compose.runtime.State<Color>,
) {
    /** What lies beyond the recorded page: its own ground, so glass never sees empty black. */
    internal val ground: Color get() = groundState.value
}

@Composable
fun rememberKelvinBackdrop(ground: Color = Kelvin.colors.canvas): KelvinBackdrop {
    // The page's ground is recorded under its content: where a list is padded or scrolled short,
    // glass must see the canvas, not the transparent (black) layer behind it.
    val groundState = rememberUpdatedState(ground)
    val graphics = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val layer = rememberLayerBackdrop(graphics) {
        drawRect(groundState.value)
        drawContent()
    }
    return remember(layer, graphics) { KelvinBackdrop(layer, graphics, groundState) }
}

/** Put this on the container that draws the page, not on the glass itself. */
fun Modifier.kelvinBackdrop(backdrop: KelvinBackdrop): Modifier = layerBackdrop(backdrop.layer)

/**
 * What the glass carries. [Text] keeps the contrast floor. [Decorative] has none. [Colored] is
 * stained glass for coloured controls: the tint is in the body of the pane and its opacity
 * follows Beer-Lambert absorption along the light's path, so the thick middle is dense enough
 * for an `on-accent` glyph while the thin rim lets the refracted backdrop through.
 */
/** The clearest a dialog or sheet ([GlassContent.Pane]) gets. */
const val PaneGlassCap = GlassLevel.Glass

enum class GlassContent {
    Text, Decorative, Colored,
    /**
     * Dialogs and sheets: a large, evenly coated pane, so no contrast floor; a lighter blur and a
     * thinner coat keep what is behind it visible (the web's `.k-glass` at the same level).
     */
    Pane,
}

/**
 * Warmth from a finger: frost clears within [radius] of [position] by [amount] (0–1), then
 * fogs back over when the finger lifts. Drive it with [rememberThaw].
 */
@Stable
class Thaw internal constructor(private val scope: CoroutineScope) {
    var position by mutableStateOf(Offset.Unspecified)
        private set
    internal val amount = Animatable(0f)
    val radius: Dp = 64.dp

    /** Feed this from a press callback: a position while touched, null on release. */
    fun touch(at: Offset?) {
        if (at != null) {
            position = at
            scope.launch { amount.animateTo(1f, tween(140)) }
        } else {
            scope.launch { amount.animateTo(0f, tween(900)) }
        }
    }
}

@Composable
fun rememberThaw(): Thaw {
    val scope = rememberCoroutineScope()
    return remember(scope) { Thaw(scope) }
}

/**
 * Draws this element as Kelvin glass over [backdrop]: a pane that refracts what is behind it
 * through its rounded rim, reflects the scene at grazing angles, frosts it to the chosen level
 * and clears the frost under a [thaw]. No highlight is painted on; all light comes from the
 * shader. [motion] deforms the pane while the backdrop stays put behind it. Android 13+ runs the full shader; Android 12 gets blur and tint;
 * older versions and Solid get an opaque pane. Content is clipped to [shape].
 */
fun Modifier.glass(
    backdrop: KelvinBackdrop?,
    shape: Shape,
    content: GlassContent = GlassContent.Text,
    tint: Color? = null,
    tintAlpha: Float? = null,
    thaw: Thaw? = null,
    elevated: Boolean = false,
    motion: (GraphicsLayerScope.() -> Unit)? = null,
    pebble: GlassPress? = null,
    isPebble: Boolean = pebble != null,
): Modifier = composed {
    // Dialogs and sheets carry reading text over whatever is behind them, so they never get
    // clearer than the Glass mark, however far the slider goes.
    val a11y = LocalKelvinAccessibility.current
    val params = Kelvin.glass.let { g ->
        if (content == GlassContent.Pane && g.level > PaneGlassCap) remember(a11y) { PaneGlassCap.resolve(a11y) } else g
    }
    val colors = Kelvin.colors
    val dark = Kelvin.dark
    val colored = content == GlassContent.Colored
    // Coloured controls are stained in the vivid version of their colour, as on ColorOS and vivo.
    val plainTint = tint ?: colors.glassTint
    val baseTint = remember(plainTint, colored) { if (colored) plainTint.vivid() else plainTint }
    val pane = content == GlassContent.Pane
    val alpha = tintAlpha ?: when {
        colored -> params.stainAlpha
        // Sheets and dialogs carry reading text over whatever is behind them (the dock, a dimmed
        // page): a steady milky coat, so bright shapes behind never show through as smears.
        pane -> maxOf(params.tintAlpha, 0.86f)
        else -> params.tintAlpha
    }
    val heat = colors.glassHeat
    val ground = backdrop?.ground ?: colors.canvas
    val text = content == GlassContent.Text
    val shader = isRuntimeShaderSupported()
    val reduceMotion = Kelvin.reduceMotion
    // A pebble with nothing to refract (no backdrop, high-contrast text, or no shader support)
    // is still a pebble: a solid domed stone in the glass's colour.
    if (isPebble && (backdrop == null || params.isSolid || !shader)) {
        return@composed Modifier
            .then(if (motion != null) Modifier.graphicsLayer(motion) else Modifier)
            .then(if (colored || elevated) Modifier.shadow(8.dp, shape, ambientColor = if (colored) baseTint else Color.Black, spotColor = if (colored) baseTint else Color.Black) else Modifier)
            .pebble(shape, if (colored) plainTint else tint ?: colors.raised, pebble)
    }

    drawBackdrop(
        backdrop = backdrop?.layer ?: emptyBackdrop(),
        shape = { shape },
        effects = {
            if (params.isSolid || backdrop == null) return@drawBackdrop
            val frost = params.frostDp.dp.toPx()
            if (!shader) {
                blur(maxOf(frost * 0.6f, 1f))
                return@drawBackdrop
            }
            // A pane's rim and thickness scale with the pane: a chip is a thin sliver of glass
            // with a narrow rounded edge and a true, flat middle; only large panes get the full
            // rim. Otherwise small controls would be all bevel and bend everything behind them.
            // A pebble is a drop: a deeper body and a wider rounded rim than a flat pane.
            val thickness = if (pane) maxOf(params.thicknessDp, 16f).dp.toPx() else if (isPebble) maxOf(minOf(params.thicknessDp.dp.toPx(), size.minDimension * 0.3f), size.minDimension * 0.22f)
                else minOf(params.thicknessDp.dp.toPx(), size.minDimension * 0.3f)
            val bevel = if (isPebble) size.minDimension * 0.3f else minOf(params.bevelDp.dp.toPx(), size.minDimension * 0.24f)
                .let { if (pane) maxOf(it, 22.dp.toPx()) else it }
            // Most of the frost is a smooth pre-blur; the ground-glass scatter on top is kept
            // small so its grain reads as texture, not noise.
            val pre = if (pane) frost * 1.2f else minOf(frost * 1.25f, 30.dp.toPx())
            // Cover the blur's full reach (about 1.8x its radius) and the rim's reflection, so
            // the pane never samples outside what was recorded.
            padding = maxOf(padding, pre * 1.8f + bevel + thickness * 1.6f + 4.dp.toPx())
            if (pre > 0.5f) blur(pre)
            val radii = shape.paneRadii(size, layoutDirection, this)
            val t = thaw?.takeIf { it.position.isSpecified }
            runtimeShaderEffect("kelvin-optic", OPTIC_AGSL, "content") {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("offset", -padding, -padding)
                setFloatUniform("cornerRadii", radii)
                setFloatUniform("thickness", thickness)
                setFloatUniform("bevel", bevel)
                setFloatUniform("ior", params.ior)
                // A dialog's thick rim splits light a little, as liquid glass does.
                setFloatUniform("dispersion", if (pane) maxOf(params.dispersion, 0.015f) else params.dispersion)
                setFloatUniform("frost", frost * 0.15f)
                setFloatUniform("tint", baseTint.red, baseTint.green, baseTint.blue, alpha)
                // Light glass over dark content stays milky white (sRGB ~0.9) toward the opaque
                // end, easing to the 4.5:1 floor (0.5 linear) by Liquid; dark glass mirrors it.
                setFloatUniform("floorMin", if (text && !dark) (0.5f + 0.28f * params.milk) * params.floor else 0f)
                setFloatUniform("floorMax", if (text && dark) 1f - (0.93f + 0.04f * params.milk) * params.floor else 1f)
                setFloatUniform("heat", heat.red, heat.green, heat.blue)
                setFloatUniform("horizon", params.horizon)
                setFloatUniform("thaw", t?.position?.x ?: -1e4f, t?.position?.y ?: -1e4f)
                setFloatUniform("thawRadius", t?.radius?.toPx() ?: 1f)
                setFloatUniform("thawAmount", t?.amount?.value ?: 0f)
                setFloatUniform("saturation", params.saturation)
                setFloatUniform("body", if (colored) 1f else 0f)
                setFloatUniform("ground", ground.red, ground.green, ground.blue)
                // A pebble's rim meets the page almost vertically (tucked in, like a drop).
                setFloatUniform("steep", if (isPebble) 1f else 0f)
                setFloatUniform("px", density)
                setFloatUniform("finish", if (dark) 0.55f else 1f)
            }
        },
        // Backdrop's highlight is the liquid-glass rim light, brightest on two opposite corners.
        // Every floating pane carries the same one as the buttons, so the dock, the search
        // capsule, pills, menus and badges read as one material. Edge-to-edge bars (a rectangle
        // spanning the screen) and the dock's clear lens do not: there it would rule a line.
        highlight = if (!params.isSolid && shape != androidx.compose.ui.graphics.RectangleShape && content != GlassContent.Decorative) {
            { Highlight(width = if (dark) 0.6.dp else 0.35.dp, alpha = if (dark) 0.6f else 0.8f) }
        } else null,
        shadow = when {
            // Light through stained glass lands on the page in the glass's colour: a soft
            // coloured shadow under coloured controls, deepest where the pane is thickest.
            colored -> { { Shadow(radius = 22.dp, offset = DpOffset(if (isPebble) 4.dp else 0.dp, 8.dp), color = baseTint, alpha = if (dark) 0.6f else 0.5f) } }
            // Pebbles float on a soft glow beneath them.
            isPebble -> { { Shadow(radius = 12.dp, offset = DpOffset(2.dp, 3.dp), color = Color.Black, alpha = if (dark) 0.4f else 0.07f) } }
            elevated -> { { Shadow(radius = 24.dp, offset = DpOffset(0.dp, 4.dp), color = Color.Black, alpha = if (dark) 0.5f else 0.1f) } }
            else -> null
        },
        layerBlock = motion,
        onDrawSurface = {
            when {
                params.isSolid || backdrop == null -> drawRect(baseTint)
                // No shader means no contrast floor: keep the tint dense enough for text.
                !shader -> drawRect(baseTint.copy(alpha = if (text) maxOf(alpha, 0.72f * params.floor) else alpha))
            }
        },
        onDrawFront = {
            val front = this; pebble?.run { front.drawPressLight(shape.createOutline(front.size, front.layoutDirection, front)) }
            // Without refraction there is nothing to show the pane's edge, so draw it.
            if (!colored && (params.isSolid || !shader || backdrop == null)) {
                drawOutline(shape.createOutline(size, layoutDirection, this), colors.glassEdge, style = Stroke(0.5.dp.toPx()))
            }
        },
    )
}

/**
 * A resting pill among glass in floating chrome: the same glass, as a pebble coated in
 * `glass-lit`. In light mode that is exactly the glass buttons' white glass; in dark mode a coat
 * a step lighter that never thins below 86%, so pills stay readable up to Clear.
 */
fun Modifier.litGlass(backdrop: KelvinBackdrop?, shape: Shape, motion: (GraphicsLayerScope.() -> Unit)? = null): Modifier = composed {
    val alpha = if (Kelvin.dark) maxOf(Kelvin.glass.tintAlpha, 0.86f) else null
    glass(backdrop, shape, tint = Kelvin.colors.glassLit, tintAlpha = alpha, motion = motion, isPebble = true)
}

/**
 * A filter pill's surface. Resting pills look like the buttons around them: [litGlass] over
 * content, a [lift] pill on the page, and inside a [KelvinGroup] card (light mode) a well of the
 * page's `canvas` tone, since a white pill would vanish on a white card. The chosen one is a solid accent pebble, the same fill as a
 * primary button, so a filter and a call to action never disagree on the colour. Put
 * [kelvinPillInk] on top.
 */
fun Modifier.kelvinPill(selected: Boolean, backdrop: KelvinBackdrop?, shape: Shape = CapsuleShape): Modifier = composed {
    when {
        selected -> pebble(shape, Kelvin.colors.accent)
        backdrop != null -> litGlass(backdrop, shape)
        // On a white card a white lift pill vanishes: there it is a well of the page's own tone.
        LocalInKelvinGroup.current && !Kelvin.dark -> clip(shape).background(Kelvin.colors.canvas, shape)
        else -> lift(shape)
    }
}

/** Text and icon colour on a [kelvinPill]. */
@Composable
@ReadOnlyComposable
fun kelvinPillInk(selected: Boolean): Color = if (selected) Kelvin.colors.onAccent else Kelvin.colors.ink

/**
 * The selection inside a container that is not glass (a segmented thumb, a tab in a group): a
 * solid `lift` fill, a step lighter than the track in dark mode and white on a soft shadow in light.
 */
fun Modifier.lift(shape: Shape): Modifier = composed {
    val c = Kelvin.colors
    this.then(if (Kelvin.dark) Modifier else Modifier.shadow(2.dp, shape, ambientColor = c.glassEdge, spotColor = c.glassEdge))
        .then(Modifier.drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), c.lift) })
}

/** Corner radii (top-left, top-right, bottom-right, bottom-left) for the pane's distance field. */
internal fun Shape.paneRadii(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray {
    val max = size.minDimension / 2f
    return when (this) {
        is ContinuousShape -> {
            val r = with(density) { radius.toPx() }.coerceAtMost(max)
            if (flatBottom) floatArrayOf(r, r, 0f, 0f) else floatArrayOf(r, r, r, r)
        }
        is NotchedShape -> with(density) { radius.toPx() }.coerceAtMost(max).let { floatArrayOf(it, it, it, it) }
        is CornerBasedShape -> {
            val ltr = layoutDirection == LayoutDirection.Ltr
            val ts = topStart.toPx(size, density).coerceAtMost(max)
            val te = topEnd.toPx(size, density).coerceAtMost(max)
            val be = bottomEnd.toPx(size, density).coerceAtMost(max)
            val bs = bottomStart.toPx(size, density).coerceAtMost(max)
            if (ltr) floatArrayOf(ts, te, be, bs) else floatArrayOf(te, ts, bs, be)
        }
        else -> floatArrayOf(0f, 0f, 0f, 0f)
    }
}

internal const val PaneSdf = """
float radiusAt(float2 p, float4 radii) {
    if (p.x >= 0.0) return p.y <= 0.0 ? radii.y : radii.z;
    return p.y <= 0.0 ? radii.x : radii.w;
}

float sdPane(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 normalAt(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) return sign(p) * normalize(max(q, float2(0.0001)));
    float onX = step(q.y, q.x);
    return sign(p) * float2(onX, 1.0 - onX);
}
"""

/**
 * Kelvin optic glass. Everything light does here comes from the geometry: the view ray is
 * refracted by Snell's law at the pane's curved surface (flat top, quarter-circle bevel) and
 * carried through its thickness, red, green and blue with slightly different indices
 * (dispersion), and the rim reflects the scene beyond it by Schlick's Fresnel term. Ground-glass
 * frost scatters samples to fixed random points; the thaw clears it; the contrast floor keeps
 * text readable up to Liquid; light carried in through the bottom edge is scattered out by the
 * frost. Runs on Backdrop's padded layer, so refracted samples beyond the pane are real backdrop.
 */
private const val OPTIC_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float thickness;
uniform float bevel;
uniform float ior;
uniform float dispersion;
uniform float frost;
uniform float4 tint;
uniform float floorMin;
uniform float floorMax;
uniform float3 heat;
uniform float horizon;
uniform float2 thaw;
uniform float thawRadius;
uniform float thawAmount;
uniform float saturation;
uniform float body;
uniform float3 ground;
uniform float steep;
uniform float px;
uniform float finish;

$PaneSdf
$PebbleLight

float luminance(float3 c) {
    float3 l = pow(max(c, float3(0.0)), float3(2.2));
    return dot(l, float3(0.2126, 0.7152, 0.0722));
}

float hash(float2 p) {
    p = fract(p * float2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

// The page as seen at p. Beyond what was recorded (off screen, or where blur pulled in empty
// pixels) lies the page's own ground, never black.
float3 see(float2 p) {
    half4 s = content.eval(p);
    return float3(s.rgb) + (1.0 - float(s.a)) * ground;
}

// Where a vertical view ray lands on the backdrop after passing through the pane: refracted at
// the curved top surface by Snell's law, then carried down through the glass's thickness.
float2 refractOffset(float3 n, float eta, float depthHere) {
    float3 r = refract(float3(0.0, 0.0, -1.0), n, eta);
    return r.xy / max(-r.z, 0.05) * depthHere;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord + offset - halfSize;
    float r = min(radiusAt(p, cornerRadii), min(halfSize.x, halfSize.y));
    float sd = min(sdPane(p, halfSize, r), 0.0);

    // Surface: flat on top, rounding down to the rim over the bevel as a quarter circle.
    float b = max(bevel, 0.001);
    float x = clamp(1.0 + sd / b, 0.0, 1.0);
    // A drop's edge stands near vertical where it meets the page, so it bends the page behind it
    // hard and reflects its surroundings (Fresnel): the depth at the edge, from optics alone.
    float slope = x / sqrt(max(1.0 - x * x, mix(0.02, 0.002, steep)));
    float2 g = normalAt(p, halfSize, min(r * 1.5, min(halfSize.x, halfSize.y)));
    float3 n = normalize(float3(g * slope, 1.0));
    float h = thickness * sqrt(max(1.0 - x * x, 0.0));

    // Thaw: warmth from a finger clears the frost around it.
    float t = thawAmount * (1.0 - smoothstep(thawRadius * 0.35, thawRadius, length(p + halfSize - thaw)));
    float scatter = frost * (1.0 - t);

    // Dispersion: red bends least, blue most.
    float2 oR = refractOffset(n, 1.0 / (ior - dispersion), h + thickness * 0.15);
    float2 oG = refractOffset(n, 1.0 / ior, h + thickness * 0.15);
    float2 oB = refractOffset(n, 1.0 / (ior + dispersion), h + thickness * 0.15);

    float3 acc = float3(0.0);
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float a = hash(p + fi * 17.0) * 6.2831853;
        float rr = sqrt(hash(p.yx + fi * 31.0)) * scatter;
        float2 o = float2(cos(a), sin(a)) * rr;
        acc.r += see(coord + oR + o).r;
        acc.g += see(coord + oG + o).g;
        acc.b += see(coord + oB + o).b;
    }
    float3 c = acc / 6.0;

    // Fresnel (Schlick): the steeper the surface, the more of the surroundings it reflects.
    // The reflection is the backdrop itself, seen in the tilted surface.
    float cosI = clamp(n.z, 0.0, 1.0);
    float f0 = pow((ior - 1.0) / (ior + 1.0), 2.0);
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - cosI, 5.0);
    // What the tilted rim reflects is the scene just beyond the pane, softly, since the
    // reflection comes off a curved surface.
    float2 mirror = coord + g * (b + thickness);
    float3 reflected = (see(mirror) + see(mirror + g.yx * b * 0.5) + see(mirror - g.yx * b * 0.5)) / 3.0;
    // Pebbles carry no reflection on their face: only what they bend from behind them.
    c = mix(c, reflected, min(fresnel, 0.55) * (1.0 - steep));

    float grey = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = clamp(mix(float3(grey), c, saturation), 0.0, 1.0);
    // Coat (body = 0) or stained body (body = 1): Beer-Lambert opacity over the path through
    // the pane, full in the thick middle and thinning toward the rim.
    float a = tint.a;
    if (body > 0.5) {
        // A stained rim still carries some colour: the path never drops below a third.
        float path = 0.35 + 0.65 * (h + thickness * 0.15) / max(thickness * 1.15, 0.001);
        a = 1.0 - pow(1.0 - min(a, 0.999), max(path, 0.0));
    }
    c = mix(c, tint.rgb, a * (1.0 - 0.55 * t));

    float lum = luminance(c);
    float tl = luminance(tint.rgb);
    if (lum < floorMin && tl > lum) c = mix(c, tint.rgb, clamp((floorMin - lum) / (tl - lum), 0.0, 1.0));
    if (lum > floorMax && tl < lum) c = mix(c, tint.rgb, clamp((lum - floorMax) / (lum - tl), 0.0, 1.0));

    // Light entering through the bottom edge travels up the pane and is scattered out by the
    // frost: strongest near the bottom and where the glass is ground.
    float fromBottom = clamp((halfSize.y - p.y) / (size.y * 0.9), 0.0, 1.0);
    float3 carried = heat * horizon * 0.12 * (1.0 - fromBottom) * (1.0 - fromBottom) * clamp(frost / 8.0, 0.0, 1.0);
    c = 1.0 - (1.0 - c) * (1.0 - carried);

    if (steep > 0.5) c = pebbleFinish(c, -sd, g, (p.y + halfSize.y) / size.y, min(size.x, size.y), px, finish, 0.0);
    return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/** OKLab chroma: about 0 for greys and white, 0.15 to 0.2 for Kelvin's accents, under 0.06 for their soft tints. */
internal fun Color.chroma(): Float {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    val r = lin(red); val g = lin(green); val b = lin(blue)
    val l = Math.cbrt((0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b).toDouble()).toFloat()
    val m = Math.cbrt((0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b).toDouble()).toFloat()
    val s = Math.cbrt((0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b).toDouble()).toFloat()
    val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    return kotlin.math.sqrt(a * a + bb * bb)
}

/**
 * The vivid version of a colour for coloured controls: the same hue, perceptual lightness
 * (OKLab) lifted by [lift] and chroma pushed to the edge of sRGB there. Kelvin's accents are
 * already near that edge, so the change is a brighter fill, never a pastel one.
 */
fun Color.vivid(lift: Float = 0.05f): Color {
    fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    fun enc(c: Float) = if (c <= 0.0031308f) 12.92f * c else 1.055f * Math.pow(c.toDouble(), 1 / 2.4).toFloat() - 0.055f
    val r = lin(red); val g = lin(green); val b = lin(blue)
    val l = Math.cbrt((0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b).toDouble()).toFloat()
    val m = Math.cbrt((0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b).toDouble()).toFloat()
    val s = Math.cbrt((0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b).toDouble()).toFloat()
    val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    if (a * a + bb * bb < 0.0016f) return this // greys (Graphite) have no vivid version
    val target = (lightness + lift).coerceAtMost(0.9f)
    fun toRgb(k: Float): FloatArray {
        val l2 = target + 0.3963377774f * a * k + 0.2158037573f * bb * k
        val m2 = target - 0.1055613458f * a * k - 0.0638541728f * bb * k
        val s2 = target - 0.0894841775f * a * k - 1.2914855480f * bb * k
        val l3 = l2 * l2 * l2; val m3 = m2 * m2 * m2; val s3 = s2 * s2 * s2
        return floatArrayOf(
            4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3,
            -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3,
            -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3,
        )
    }
    var k = 1.6f
    var rgb = toRgb(k)
    while (k > 0.5f && rgb.any { it < -0.001f || it > 1.001f }) { k -= 0.01f; rgb = toRgb(k) }
    return Color(enc(rgb[0].coerceIn(0f, 1f)), enc(rgb[1].coerceIn(0f, 1f)), enc(rgb[2].coerceIn(0f, 1f)), alpha)
}

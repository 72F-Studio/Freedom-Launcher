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
import io.github.itshedimisawi.vitro.Glass
import io.github.itshedimisawi.vitro.GlassAppearance
import io.github.itshedimisawi.vitro.GlassBackdrop
import io.github.itshedimisawi.vitro.glassBackdrop
import io.github.itshedimisawi.vitro.glassEffect
import io.github.itshedimisawi.vitro.rememberGlassBackdrop
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * The two premium materials a person can pick in Settings > Appearance > Glass. [Liquid] is
 * Liquid Glass as Vitro renders it, calibrated against iOS 26, on the Glass level from
 * near-opaque to Clear. [Frost] is deep ground glass, as on the HyperOS lock screen: no
 * refraction, the page behind melted into a wide, saturated blur under a smoky coat, on its own
 * stepless intensity from Opaque to Sheer. Both apply wherever glass does.
 */
enum class GlassTexture { Liquid, Frost }

/**
 * Reference points on the Frost intensity, a stepless 0 to 1 like the Glass level: an opaque
 * coat at 0, the sheerest frost at 1. Frost never gives up the contrast floor, so even Sheer
 * keeps text readable.
 */
object FrostLevel {
    const val Opaque = 0f
    const val Default = 0.5f
    const val Sheer = 1f
}

/**
 * Reference points on Liquid glass's refraction, a stepless 0 to 1 set apart from the Glass
 * level: how hard the rim bends what is behind it, whether the pane is near-opaque or clear.
 */
object RefractionLevel {
    const val Subtle = 0f
    const val Default = 0.5f
    const val Strong = 1f
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
    /** How hard Liquid glass's rim bends the backdrop, 0 Subtle … 1 Strong; Frost does not refract. */
    val refraction: Float = RefractionLevel.Default,
    /** Which material draws the pane. Frost keeps [level] on the Liquid scale (how clear it reads, never past Glass) for the veil and the controls' stretch. */
    val texture: GlassTexture = GlassTexture.Liquid,
) {
    /** An opaque pane with no glass, forced by high-contrast text. */
    val isSolid: Boolean get() = solid
    /** Deep frost: a wide blur under a coat, no refraction, no rim light, no thaw. */
    val isFrost: Boolean get() = texture == GlassTexture.Frost
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

fun Float.resolve(
    a11y: KelvinAccessibility,
    texture: GlassTexture = GlassTexture.Liquid,
    refraction: Float = RefractionLevel.Default,
): GlassParams {
    if (texture == GlassTexture.Frost) return resolveFrost(a11y)
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
        refraction = refraction.coerceIn(0f, 1f),
    )
}

/**
 * Frost at intensity [this] (0 Opaque … 1 Sheer). The blur stays deep across the range (it is
 * what makes the material); only the coat thins, and colour behind is pushed further as it does,
 * so a sunset under the pane glows through as warm light rather than greying out.
 */
private fun Float.resolveFrost(a11y: KelvinAccessibility): GlassParams {
    val t = coerceIn(0f, 1f)
    // How clear the pane reads, on the Liquid scale: never past the Glass mark.
    val level = t * GlassLevel.Glass
    return GlassParams(
        level = level,
        frostDp = curve(t, 0f to 44f, 1f to 34f),
        tintAlpha = curve(t, 0f to 0.96f, FrostLevel.Default to 0.62f, 1f to 0.34f),
        thicknessDp = 0f,
        bevelDp = 0f,
        ior = 1f,
        dispersion = 0f,
        floor = 1f,
        horizon = 0f,
        saturation = curve(t, 0f to 1.3f, 1f to 1.8f),
        milk = curve(t, 0f to 1f, 1f to 0.4f),
        stainAlpha = curve(t, 0f to 0.98f, 1f to 0.9f),
        deform = if (a11y.reduceMotion) 0f else 0.15f + 0.85f * smoothstep(GlassLevel.Frosted, GlassLevel.Liquid, level),
        solid = a11y.highContrastText,
        texture = GlassTexture.Frost,
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
    /** The same content as Vitro records it, for Liquid glass. */
    internal val vitro: GlassBackdrop,
    /** The window the content is in. Vitro sees only its own window. */
    internal val view: android.view.View,
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
    val vitro = rememberGlassBackdrop()
    val view = androidx.compose.ui.platform.LocalView.current
    return remember(layer, graphics, vitro, view) { KelvinBackdrop(layer, graphics, groundState, vitro, view) }
}

/**
 * Put this on the container that draws the page, not on the glass itself. Backdrop always records
 * it (Frost and the veil read it); Vitro records it too while the texture is Liquid.
 */
fun Modifier.kelvinBackdrop(backdrop: KelvinBackdrop): Modifier = composed {
    val liquid = !Kelvin.glass.isFrost && !Kelvin.glass.isSolid
    layerBackdrop(backdrop.layer).then(
        if (liquid) Modifier.glassBackdrop(backdrop.vitro) else Modifier,
    )
}

/** How far stained glass darkens what shows through it before the stain goes on. */
private const val StainVeil = -0.5f

/**
 * A stain for Vitro, which mixes at most 85% of a tint (times [density]) over what shows through. The colour is
 * pushed past the fill so that over a light page, darkened by [veil], the glass lands on the
 * fill itself; over darker content it deepens, as stained glass does.
 */
private fun Color.stain(veil: Float, density: Float): Color {
    // A thinner stain lets more of the page in; pushing harder for it keeps the colour as rich
    // as the fill instead of fading toward the page's grey.
    val strength = 0.85f * density
    val under = (1f + veil) * (1f - strength)
    // Vitro's veil and rim light pale the mix further; deepening the colour by this power first
    // was matched on the emulator against the solid fill (the accent lands within a few steps).
    fun push(c: Float) = ((Math.pow(c.toDouble(), 1.3).toFloat() - under) / strength).coerceIn(0f, 1f)
    return Color(push(red), push(green), push(blue))
}

/** Vitro draws only rounded rectangles: the nearest one to a Kelvin shape. */
private fun Shape.cornerShape(): CornerBasedShape = when (this) {
    is CornerBasedShape -> this
    // A capsule's huge radius is clamped to half the height, as Vitro wants it.
    is ContinuousShape -> if (flatBottom) RoundedCornerShape(radius, radius, 0.dp, 0.dp) else RoundedCornerShape(radius)
    is NotchedShape -> RoundedCornerShape(radius)
    else -> RoundedCornerShape(0.dp)
}

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
 * Draws this element as Kelvin glass over [backdrop]. Liquid glass is rendered by Vitro
 * (itshedimisawi, Apache 2.0): rim refraction, highlights, blur and touch response tuned against
 * iOS 26, with a close approximation before Android 13; [pebble] glass swells under the finger.
 * Frost is Kelvin's own shader on Backdrop, and [thaw] clears nothing on either now. [motion]
 * moves the pane. Solid gets an opaque pane. Content is clipped to [shape].
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
        if (content == GlassContent.Pane && !g.isFrost && g.level > PaneGlassCap) remember(a11y, g.refraction) { PaneGlassCap.resolve(a11y, refraction = g.refraction) } else g
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
        // Deep frost already melts what is behind into soft light, so a lighter coat reads.
        pane -> maxOf(params.tintAlpha, if (params.isFrost) 0.55f else 0.86f)
        else -> params.tintAlpha
    }
    val ground = backdrop?.ground ?: colors.canvas
    // Frost keeps the contrast floor on dialogs and sheets too: its coat goes sheerer than theirs.
    val text = content == GlassContent.Text || (params.isFrost && content == GlassContent.Pane)
    val shader = isRuntimeShaderSupported()
    val reduceMotion = Kelvin.reduceMotion
    // Sheets, dialogs and menus open in a window of their own, which Vitro cannot see out of:
    // there Backdrop melts the page under the coat instead, as it does for Frost.
    val foreign = backdrop != null && androidx.compose.ui.platform.LocalView.current !== backdrop.view
    // A pebble with nothing to refract (no backdrop, high-contrast text, or no shader support)
    // is still a pebble: a solid domed stone in the glass's colour.
    if (isPebble && (backdrop == null || params.isSolid || ((params.isFrost || foreign) && !shader))) {
        return@composed Modifier
            .then(if (motion != null) Modifier.graphicsLayer(motion) else Modifier)
            .then(if (colored || elevated) Modifier.shadow(8.dp, shape, ambientColor = if (colored) baseTint else Color.Black, spotColor = if (colored) baseTint else Color.Black) else Modifier)
            .pebble(shape, if (colored) plainTint else tint ?: colors.raised, pebble)
    }

    // Liquid glass is Vitro's: refraction, highlights, blur and press response calibrated
    // against iOS 26, with its own approximation below Android 13. Kelvin sets the coat and the
    // veil from the Glass level and keeps its tints, stains and shadows.
    if (!params.isFrost && !params.isSolid && backdrop != null && !foreign) {
        val clarity = smoothstep(GlassLevel.Liquid, GlassLevel.Clear, params.level)
        // A vivid fill (Primary, the orb, a chosen pill) is stained glass; a soft or grey one
        // (Tinted, Secondary, Danger, Raised) is glass in its tone, which darkening would muddy.
        val stained = colored && baseTint.chroma() > 0.1f
        // The stain follows Transparency too: full up to Frosted, thinning toward Clear so the
        // page shows through a CTA as it does through every other pane, yet it still reads as one.
        val density = curve(params.level, GlassLevel.Frosted to 1f, GlassLevel.Liquid to 0.8f, 1f to 0.6f)
        val vitro = Glass.Regular.copy(
            // Ground glass toward the opaque end, Vitro's own blur from Liquid on.
            blurRadius = maxOf(params.frostDp * 0.6f, 2.5f - 0.5f * clarity).dp,
            // Coloured glass grows richer as it clears, so a see-through CTA glows rather than
            // washes out; other glass eases toward Vitro's Clear.
            saturation = if (colored) curve(params.level, GlassLevel.Frosted to 1.1f, GlassLevel.Liquid to 1.5f, 1f to 1.9f) else 1.1f - 0.1f * clarity,
            // The rim's bend is its own setting, apart from how opaque the pane is. Vitro's
            // calibrated rim (12dp bending 26dp) sits a little under Default.
            refractionHeight = (8f + 16f * params.refraction).dp,
            refractionAmount = (16f + 48f * params.refraction).dp,
            // The veil that keeps text legible: Regular's up to Liquid, Clear's at the end.
            // Stained glass darkens what shows through it instead, so its colour holds.
            brightness = when {
                stained -> StainVeil * density
                text || pane || colored -> curve(params.level, 0f to 0.8f, GlassLevel.Frosted to 0.65f, GlassLevel.Liquid to 0.5f, 1f to 0.1f)
                else -> 0.1f
            },
            appearance = if (dark) GlassAppearance.Dark else GlassAppearance.Light,
            // Coloured controls are stained; plain glass carries Kelvin's coat only toward the
            // opaque end and is bare Vitro glass from Liquid on.
            tint = if (stained) baseTint.stain(StainVeil * density, density).copy(alpha = density) else baseTint.copy(alpha = if (colored || pane || tintAlpha != null) alpha else alpha * params.milk),
            // Edge-to-edge bars and the dock's clear lens get no rim light: there it would rule a line.
            highlightIntensity = if (shape == androidx.compose.ui.graphics.RectangleShape || content == GlassContent.Decorative) 0f else 1f,
            // Vitro's shadow (30dp spread, 8dp drop, 16%) is calibrated on toolbar-sized glass and
            // swamps a button: pebbles keep Kelvin's small, close shadow and floating panes its
            // elevated one. Vitro thins both on the dark material itself.
            shadowRadius = if (isPebble) 12.dp else 24.dp,
            shadowOffset = if (isPebble) 3.dp else 4.dp,
            shadowOpacity = when {
                isPebble -> 0.07f
                colored || elevated -> 0.1f
                else -> 0f
            },
            // Buttons swell, glow and stretch under the finger the way iOS glass does.
            isInteractive = pebble != null && params.deform > 0f,
        )
        // Vitro's press response replaces the pebble's own; other motion (a menu growing from
        // its opener) still moves the pane.
        val layer = motion.takeIf { pebble == null }
        return@composed Modifier
            .then(if (layer != null) Modifier.graphicsLayer(layer) else Modifier)
            .glassEffect(backdrop.vitro, vitro, shape.cornerShape())
            .clip(shape)
    }

    drawBackdrop(
        backdrop = backdrop?.layer ?: emptyBackdrop(),
        shape = { shape },
        effects = {
            if (params.isSolid || backdrop == null) return@drawBackdrop
            val frost = (if (foreign) maxOf(params.frostDp, 16f) else params.frostDp).dp.toPx()
            if (!shader) {
                blur(maxOf(if (params.isFrost) frost else frost * 0.6f, 1f))
                return@drawBackdrop
            }
            if (params.isFrost || foreign) {
                // Deep frost: the whole depth is one wide blur; the shader only coats, saturates
                // and grains it. Pad for the blur's full reach.
                padding = maxOf(padding, frost * 1.8f + 4.dp.toPx())
                blur(frost)
                val radii = shape.paneRadii(size, layoutDirection, this)
                runtimeShaderEffect("kelvin-frost", FROST_AGSL, "content") {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("offset", -padding, -padding)
                    setFloatUniform("cornerRadii", radii)
                    setFloatUniform("tint", baseTint.red, baseTint.green, baseTint.blue, alpha)
                    setFloatUniform("floorMin", if (text && !dark) (0.5f + 0.28f * params.milk) * params.floor else 0f)
                    setFloatUniform("floorMax", if (text && dark) 1f - (0.93f + 0.04f * params.milk) * params.floor else 1f)
                    setFloatUniform("saturation", params.saturation)
                    setFloatUniform("body", if (colored) 1f else 0f)
                    setFloatUniform("ground", ground.red, ground.green, ground.blue)
                    setFloatUniform("steep", if (isPebble) 1f else 0f)
                    setFloatUniform("px", density)
                    setFloatUniform("finish", if (dark) 0.55f else 1f)
                    setFloatUniform("grain", if (dark) 0.014f else 0.018f)
                }
                return@drawBackdrop
            }
        },
        // Frost is matte: no rim light, its edge comes from the coat and the shadow.
        highlight = null,
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
            // Without glass there is nothing to show the pane's edge, so draw it.
            if (!colored && (params.isSolid || backdrop == null)) {
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
 * page's `canvas` tone, since a white pill would vanish on a white card. The chosen one is accent glass over content (a solid accent pebble elsewhere), the same as a
 * primary button, so a filter and a call to action never disagree on the colour. Put
 * [kelvinPillInk] on top.
 */
fun Modifier.kelvinPill(selected: Boolean, backdrop: KelvinBackdrop?, shape: Shape = CapsuleShape): Modifier = composed {
    when {
        selected -> if (backdrop != null) glass(backdrop, shape, GlassContent.Colored, tint = Kelvin.colors.accent, isPebble = true) else pebble(shape, Kelvin.colors.accent)
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
 * Kelvin deep frost. The backdrop arrives already melted by a wide blur; here it is saturated so
 * colour glows through rather than greying, coated with `glass-tint` (stained glass thins at a
 * pebble's rim), held to the contrast floor, and given a fine grain fixed to the pane, so it
 * reads as ground glass, not fog. No refraction, no reflection, no rim light.
 */
private const val FROST_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float4 tint;
uniform float floorMin;
uniform float floorMax;
uniform float saturation;
uniform float body;
uniform float3 ground;
uniform float steep;
uniform float px;
uniform float finish;
uniform float grain;

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

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord + offset - halfSize;
    float r = min(radiusAt(p, cornerRadii), min(halfSize.x, halfSize.y));
    float sd = min(sdPane(p, halfSize, r), 0.0);

    half4 s = content.eval(coord);
    float3 c = float3(s.rgb) + (1.0 - float(s.a)) * ground;
    float grey = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = clamp(mix(float3(grey), c, saturation), 0.0, 1.0);

    float a = tint.a;
    if (body > 0.5) {
        // Stained frost is densest in the middle and thins toward a pebble's rim.
        float path = 0.35 + 0.65 * dropThickness(-sd, min(halfSize.x, halfSize.y) * 0.6);
        a = 1.0 - pow(1.0 - min(a, 0.999), path);
    }
    c = mix(c, tint.rgb, a);

    float lum = luminance(c);
    float tl = luminance(tint.rgb);
    if (lum < floorMin && tl > lum) c = mix(c, tint.rgb, clamp((floorMin - lum) / (tl - lum), 0.0, 1.0));
    if (lum > floorMax && tl < lum) c = mix(c, tint.rgb, clamp((lum - floorMax) / (lum - tl), 0.0, 1.0));

    // Ground glass: a fine grain that stays on the pane while the page moves behind it.
    c += (hash(p) - 0.5) * grain;

    if (steep > 0.5) {
        float2 g = normalAt(p, halfSize, min(r * 1.5, min(halfSize.x, halfSize.y)));
        c = pebbleFinish(c, -sd, g, (p.y + halfSize.y) / size.y, min(size.x, size.y), px, finish, 0.0);
    }
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

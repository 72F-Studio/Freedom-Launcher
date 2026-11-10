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
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.draw.drawWithContent
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

// Kelvin glass on Android: Vitro for Liquid, Kelvin's frost shader on Backdrop (or the platform's own backdrop on
// Android 17 QPR2), a solid pane where neither can run. The levels, tints and helpers it uses are shared (Glass.kt).

@Stable
actual class KelvinBackdrop internal constructor(
    internal val layer: LayerBackdrop,
    internal val graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    private val groundState: androidx.compose.runtime.State<Color>,
    /** The same content as Vitro records it, for Liquid glass. */
    internal val vitro: GlassBackdrop,
    /** The window the content is in. Vitro sees only its own window. */
    internal val view: android.view.View,
) {
    /** What lies beyond the recorded page: its own ground, so glass never sees empty black. */
    internal actual val ground: Color get() = groundState.value
}

@Composable
actual fun rememberKelvinBackdrop(ground: Color): KelvinBackdrop {
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

actual fun Modifier.kelvinBackdrop(backdrop: KelvinBackdrop): Modifier = composed {
    val liquid = !Kelvin.glass.isFrost && !Kelvin.glass.isSolid
    layerBackdrop(backdrop.layer).then(
        if (liquid) Modifier.glassBackdrop(backdrop.vitro) else Modifier,
    )
}

/** How far stained glass darkens what shows through it before the stain goes on. */

actual fun Modifier.glass(
    backdrop: KelvinBackdrop?,
    shape: Shape,
    content: GlassContent,
    tint: Color?,
    tintAlpha: Float?,
    thaw: Thaw?,
    elevated: Boolean,
    motion: (GraphicsLayerScope.() -> Unit)?,
    pebble: GlassPress?,
    isPebble: Boolean,
    /** Liquid glass only: a lens with no frost, so what it magnifies (the dock's icon) stays sharp. */
    lens: Boolean,
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
    // Under the Flat finish every control is a solid fill, over content too; only panes stay glass.
    val flat = Kelvin.finish == KelvinFinish.Flat
    if (isPebble && (flat || backdrop == null || params.isSolid || ((params.isFrost || foreign) && !shader))) {
        return@composed Modifier
            .then(if (motion != null) Modifier.graphicsLayer(motion) else Modifier)
            .then(if (!flat && (colored || elevated)) Modifier.shadow(8.dp, shape, ambientColor = if (colored) baseTint else Color.Black, spotColor = if (colored) baseTint else Color.Black) else Modifier)
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
            blurRadius = if (lens) 0.dp else maxOf(params.frostDp * 0.6f, 2.5f - 0.5f * clarity).dp,
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
            // The press is Kelvin's own (GlassPress), the same on every material. Vitro's grows a
            // control by 17dp, which nearly doubles a small button.
            isInteractive = false,
        )
        return@composed Modifier
            .then(if (motion != null) Modifier.graphicsLayer(motion) else Modifier)
            .glassEffect(backdrop.vitro, vitro, shape.cornerShape())
            .clip(shape)
            .then(
                if (pebble != null) Modifier.drawWithContent {
                    drawContent()
                    with(pebble) { drawPressLight(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
                } else Modifier,
            )
    }

    // Kelvin's frost shader takes the same uniforms whichever renderer runs it.
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val frostUniforms: (Size, FloatArray, Float, (String, FloatArray) -> Unit) -> Unit = { size, radii, offset, set ->
        set("size", floatArrayOf(size.width, size.height))
        set("offset", floatArrayOf(offset, offset))
        set("cornerRadii", radii)
        set("tint", floatArrayOf(baseTint.red, baseTint.green, baseTint.blue, alpha))
        set("floorMin", floatArrayOf(if (text && !dark) (0.5f + 0.28f * params.milk) * params.floor else 0f))
        set("floorMax", floatArrayOf(if (text && dark) 1f - (0.93f + 0.04f * params.milk) * params.floor else 1f))
        set("saturation", floatArrayOf(params.saturation))
        set("body", floatArrayOf(if (colored) 1f else 0f))
        set("ground", floatArrayOf(ground.red, ground.green, ground.blue))
        set("steep", floatArrayOf(if (isPebble) 1f else 0f))
        set("px", floatArrayOf(density))
        set("finish", floatArrayOf(if (dark) 0.55f else 1f))
        set("grain", floatArrayOf(if (dark) 0.014f else 0.018f))
        set("heat", floatArrayOf(colors.glassHeat.red, colors.glassHeat.green, colors.glassHeat.blue))
        set("heatAmt", floatArrayOf(if (dark) 0.28f else 0.12f))
    }
    val shadowSpec = when {
        // Light through stained glass lands on the page in the glass's colour: a soft
        // coloured shadow under coloured controls, deepest where the pane is thickest.
        colored -> PaneShadow(baseTint, if (dark) 0.35f else 0.25f, 16.dp, 4.dp)
        // Pebbles float on a whisper of shadow, straight down.
        isPebble -> PaneShadow(Color.Black, if (dark) 0.3f else 0.055f, 10.dp, 3.dp)
        elevated -> PaneShadow(Color.Black, if (dark) 0.5f else 0.1f, 24.dp, 4.dp)
        else -> null
    }

    // Android 17 QPR2 (API 37.2) can blur and shade what is already drawn behind a node, so on
    // those devices Frost in the page's own window needs no recorded copy of the page: the
    // platform hands the pane exactly what is under it, wherever the pane has moved.
    if (NativeBackdrop.available && shader && params.isFrost && !foreign && backdrop != null && !params.isSolid) {
        return@composed Modifier.nativeFrost(shape, params.frostDp.dp, frostUniforms, shadowSpec, motion, pebble)
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
                    frostUniforms(size, radii, -padding) { name, v ->
                        when (v.size) {
                            1 -> setFloatUniform(name, v[0])
                            2 -> setFloatUniform(name, v[0], v[1])
                            3 -> setFloatUniform(name, v[0], v[1], v[2])
                            else -> setFloatUniform(name, v)
                        }
                    }
                }
                return@drawBackdrop
            }
        },
        // Frost is matte: no rim light, its edge comes from the coat and the shadow.
        highlight = null,
        shadow = shadowSpec?.let { sh -> { Shadow(radius = sh.radius, offset = DpOffset(0.dp, sh.drop), color = sh.color, alpha = sh.alpha) } },
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


package studio72f.kelvin

import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Text in a Kelvin style and colour. */
@Composable
fun KText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Kelvin.type.body,
    color: Color = Kelvin.colors.ink,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: style.textAlign),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A Kelvin icon tinted to [color]. Pass a [contentDescription] unless text beside it says the same. */
@Composable
fun KIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = Kelvin.colors.ink,
    /** Overrides the theme's icon style for this glyph (an empty star stays an outline). */
    style: IconStyle? = null,
) {
    // Kelvin draws icons filled (solid glyphs sit with the glass tiles' coloured shapes); the
    // theme can switch to outline. Glyphs Tabler has no filled form of stay lines either way.
    val filled = (style ?: LocalIconStyle.current) == IconStyle.Filled
    val shown = if (filled) KelvinIcons.filled(icon) else icon
    // A filled glyph carries far more ink than a stroke, so one set in plain ink (or the glass's
    // secondary ink, as on the dock) is drawn well toward the page, as light as it can go and still hold 3:1
    // on the group grey. Coloured and white icons keep their colour.
    val c = Kelvin.colors
    val tint = if (filled && (color == c.ink || color == c.glassInk2)) androidx.compose.ui.graphics.lerp(color, c.canvas, 0.6f).legibleOn(c.group, 3f) else color
    Image(
        painter = rememberVectorPainter(shown),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * A Tabler icon (MIT, https://tabler.io/icons) from its unmodified path data on the 24 grid:
 * outline paths stroked at Kelvin's 1.5, or [filled] paths from Tabler's filled set. Apps use
 * this for glyphs [KelvinIcons] does not carry, so every icon on screen is one family.
 */
fun tablerIcon(name: String, vararg paths: String, filled: Boolean = false, stroke: Float = 1.5f): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (d in paths) {
        val nodes = PathParser().parsePathString(d).toNodes()
        if (filled) b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
        else b.addPath(
            pathData = nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/**
 * Tabler Icons outline paths (MIT, https://tabler.io/icons), drawn on the 24 grid at Kelvin's
 * 1.5 stroke. Paths are unmodified from upstream.
 */
object KelvinIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.5f): ImageVector = tablerIcon(name, *paths, stroke = stroke)

    val Photo by lazy { icon("photo", "M15 8h.01", "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12", "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5", "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3") }
    val Album by lazy { icon("album", "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2l0 -12", "M12 4v7l2 -2l2 2v-7") }
    val Calendar by lazy { icon("calendar", "M4 7a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12", "M16 3v4", "M8 3v4", "M4 11h16", "M11 15h1", "M12 15v3") }
    val Settings by lazy { icon("settings", "M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065", "M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0") }
    val Search by lazy { icon("search", "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M21 21l-6 -6") }
    val Adjustments by lazy { icon("adjustments", "M12 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M4 6l8 0", "M16 6l4 0", "M6 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M4 12l2 0", "M10 12l10 0", "M15 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M4 18l11 0", "M19 18l1 0") }
    val Refresh by lazy { icon("refresh", "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4", "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4") }
    val ChevronRight by lazy { icon("chevron-right", "M9 6l6 6l-6 6") }
    val Plus by lazy { icon("plus", "M12 5l0 14", "M5 12l14 0") }
    val Check by lazy { icon("check", "M5 12l5 5l10 -10") }
    val X by lazy { icon("x", "M18 6l-12 12", "M6 6l12 12") }
    val Back by lazy { icon("arrow-left", "M5 12l14 0", "M5 12l6 6", "M5 12l6 -6") }
    val Cloud by lazy { icon("cloud", "M6.657 18c-2.572 0 -4.657 -2.007 -4.657 -4.483c0 -2.475 2.085 -4.482 4.657 -4.482c.393 -1.762 1.794 -3.2 3.675 -3.773c1.88 -.572 3.956 -.193 5.444 1c1.488 1.19 2.162 3.007 1.77 4.769h.99c1.913 0 3.464 1.56 3.464 3.486c0 1.927 -1.551 3.487 -3.465 3.487h-11.878") }
    val Info by lazy { icon("info-circle", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 9h.01", "M11 12h1v4h1") }
    val Alert by lazy { icon("alert-triangle", "M12 9v4", "M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0", "M12 16h.01") }
    val Trash by lazy { icon("trash", "M4 7l16 0", "M10 11l0 6", "M14 11l0 6", "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12", "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3") }
    val Sparkles by lazy { icon("sparkles", "M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2m0 -12a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2m-7 12a6 6 0 0 1 6 -6a6 6 0 0 1 -6 -6a6 6 0 0 1 -6 6a6 6 0 0 1 6 6") }
    val Heart by lazy { icon("heart", "M19.5 12.572l-7.5 7.428l-7.5 -7.428a5 5 0 1 1 7.5 -6.566a5 5 0 1 1 7.5 6.572") }
    val Play by lazy { icon("player-play", "M7 4v16l13 -8l-13 -8") }
    val Pause by lazy { icon("player-pause", "M6 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z", "M14 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1z") }
    val SkipBack by lazy { icon("player-skip-back", "M20 5v14l-12 -7z", "M4 5l0 14") }
    val SkipForward by lazy { icon("player-skip-forward", "M4 5v14l12 -7z", "M20 5l0 14") }
    val Lock by lazy { icon("lock", "M5 13a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6", "M11 16a1 1 0 1 0 2 0a1 1 0 0 0 -2 0", "M8 11v-4a4 4 0 1 1 8 0v4") }
    val Sun by lazy { icon("sun", "M12 12m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M3 12h1m8 -9v1m8 8h1m-9 8v1m-6.4 -15.4l.7 .7m12.1 -.7l-.7 .7m0 11.4l.7 .7m-12.1 -.7l-.7 .7") }
    val Dots by lazy { icon("dots-vertical", "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    val Folder by lazy { icon("folder", "M5 4h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2") }
    val SdCard by lazy { icon("device-sd-card", "M7 21h10a2 2 0 0 0 2 -2v-14a2 2 0 0 0 -2 -2h-6.172a2 2 0 0 0 -1.414 .586l-3.828 3.828a2 2 0 0 0 -.586 1.414v10.172a2 2 0 0 0 2 2", "M13 6v2", "M16 6v2", "M10 7v1") }
    val Home by lazy { icon("home", "M5 12l-2 0l9 -9l9 9l-2 0", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7", "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6") }
    val Book by lazy { icon("book", "M3 19a9 9 0 0 1 9 0a9 9 0 0 1 9 0", "M3 6a9 9 0 0 1 9 0a9 9 0 0 1 9 0", "M3 6l0 13", "M12 6l0 13", "M21 6l0 13") }
    val Chart by lazy { icon("chart-bar", "M3 12m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M9 8m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M15 4m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M4 20l14 0") }
    val Trophy by lazy { icon("trophy", "M8 21l8 0", "M12 17l0 4", "M7 4l10 0", "M17 4v8a5 5 0 0 1 -10 0v-8", "M5 9m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M19 9m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0") }

    private fun set(name: String): ImageVector = tablerIcon(name, *KelvinIconPaths.outline.getValue(name).toTypedArray())
    val Star by lazy { set("star") }
    val Flag by lazy { set("flag") }
    /** A rosette with a tick: a verified sender. */
    val Verified by lazy { set("rosette-discount-check") }
    val Paperclip by lazy { set("paperclip") }
    val Minus by lazy { set("minus") }
    val ChevronDown by lazy { set("chevron-down") }
    val ChevronUp by lazy { set("chevron-up") }
    val ChevronLeft by lazy { set("chevron-left") }
    val CircleCheck by lazy { set("circle-check") }
    val CircleX by lazy { set("circle-x") }
    val CircleDot by lazy { set("circle-dot") }
    val Circle by lazy { set("circle") }
    val Square by lazy { set("square") }
    val SquareCheck by lazy { set("square-check") }
    val User by lazy { set("user") }
    val UserCircle by lazy { set("user-circle") }
    val Mail by lazy { set("mail") }
    val Bell by lazy { set("bell") }
    val Bookmark by lazy { set("bookmark") }
    val Share by lazy { set("share") }
    val Copy by lazy { set("copy") }
    val Download by lazy { set("download") }
    val Upload by lazy { set("upload") }
    val Edit by lazy { set("edit") }
    val Pencil by lazy { set("pencil") }
    val Eye by lazy { set("eye") }
    val EyeOff by lazy { set("eye-off") }
    val Filter by lazy { set("filter") }
    val Music by lazy { set("music") }
    val Microphone by lazy { set("microphone") }
    val Camera by lazy { set("camera") }
    val Video by lazy { set("video") }
    val MapPin by lazy { set("map-pin") }
    val Clock by lazy { set("clock") }
    val Moon by lazy { set("moon") }
    val Wifi by lazy { set("wifi") }
    val Bluetooth by lazy { set("bluetooth") }
    val Battery by lazy { set("battery") }
    val Volume by lazy { set("volume") }
    val VolumeOff by lazy { set("volume-off") }
    val Phone by lazy { set("phone") }
    val Message by lazy { set("message") }
    val Send by lazy { set("send") }
    val Link by lazy { set("link") }
    val Archive by lazy { set("archive") }
    val Tag by lazy { set("tag") }
    val CalendarEvent by lazy { set("calendar-event") }
    val Shield by lazy { set("shield") }
    val ThumbUp by lazy { set("thumb-up") }
    val Grid by lazy { set("layout-grid") }
    val List by lazy { set("list") }
    val Bold by lazy { set("bold") }
    val Italic by lazy { set("italic") }
    val Underline by lazy { set("underline") }
    val ListNumbers by lazy { set("list-numbers") }
    val Quote by lazy { set("blockquote") }
    val Back15 by lazy { set("rewind-backward-15") }
    val Forward30 by lazy { set("rewind-forward-30") }
    /** Back and forward by a step smaller than a chapter: a sentence, a few seconds. */
    val TrackPrevious by lazy { set("player-track-prev") }
    val TrackNext by lazy { set("player-track-next") }
    /** Plans and tips: a plan's crown, a gift tier, a coffee tip, and the verified rosette of an owned plan. */
    val Crown by lazy { set("crown") }
    val Gift by lazy { set("gift") }
    val Coffee by lazy { set("coffee") }
    val Rosette by lazy { set("rosette-discount-check") }
    val Sleep by lazy { set("zzz") }
    val Speed by lazy { set("gauge") }
    val Queue by lazy { set("playlist") }
    val Grip by lazy { set("grip-vertical") }
    val ArrowDown by lazy { set("arrow-down") }
    val Rotate by lazy { set("rotate-clockwise") }

    private val filledCache = java.util.concurrent.ConcurrentHashMap<String, ImageVector>()

    /**
     * The filled form of [icon] where Tabler draws one (Albums takes the filled library, Chart the
     * filled pie). Arrows, chevrons, minus, plus, x and other line glyphs have none and stay lines,
     * as they do in every filled set.
     */
    fun filled(icon: ImageVector): ImageVector {
        val paths = KelvinIconPaths.filled[icon.name] ?: return icon
        return filledCache.getOrPut(icon.name) { tablerIcon(icon.name, *paths.toTypedArray(), filled = true) }
    }
}

/**
 * Kelvin press physics. The element squashes like warm wax (wider and lower while held,
 * stronger at higher Glass levels), springs back with a wobble on release, and reports where it
 * is touched so glass can light that spot. With reduced motion it only dims.
 */
fun Modifier.kelvinPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    touch: ((Offset?) -> Unit)? = null,
    shape: Shape = RectangleShape,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val press = remember { Animatable(0f) }
    val deform = Kelvin.glass.deform
    val reduce = Kelvin.reduceMotion
    val snappy = Kelvin.motion.snappy<Float>()
    val wobble = Kelvin.motion.wobble<Float>()
    val embers = rememberEmbers()
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> { if (!reduce) embers.press(i.pressPosition); touch?.invoke(i.pressPosition); launch { press.animateTo(1f, snappy) } }
                is PressInteraction.Release, is PressInteraction.Cancel -> { embers.release(); touch?.invoke(null); launch { press.animateTo(0f, wobble) } }
            }
        }
    }
    val ink = Kelvin.colors.ink
    val accent = Kelvin.colors.accent
    val glassHeat = Kelvin.colors.glassHeat
    val dark = Kelvin.dark
    this
        .graphicsLayer {
            val p = press.value
            if (!reduce) {
                // Warm wax: a little wider and lower while held.
                val squash = 0.03f + 0.03f * deform
                scaleX = 1f + squash * 0.6f * p
                scaleY = 1f - squash * p
                transformOrigin = TransformOrigin.Center
            }
            alpha = if (enabled) 1f else KelvinMotion.DISABLED_ALPHA
        }
        .clickable(
            interactionSource = source, indication = null, enabled = enabled,
            role = role, onClickLabel = onClickLabel, onClick = onClick,
        )
        .drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            if (reduce) {
                val p = press.value
                if (p > 0f) drawOutline(outline, ink.copy(alpha = KelvinMotion.PRESS_OVERLAY * p))
            } else {
                // Thermal: the finger leaves heat that cools along the black-body curve.
                with(embers) { drawEmbers(outline, accent, glassHeat, dark) }
            }
        }
}

/**
 * The Kelvin aurora (the Horizon): colour temperature made visible behind a title. The accent
 * rises from the top-start corner, its temperature partner (cool daylight for a warm accent,
 * candlelight for a cool one) from the top-end, and `glass-heat` pools low in the middle, the
 * way ColorOS 17 and HyperOS 4 let soft light bleed into a page. It is one bicubic mesh gradient
 * whose bottom edge is fully transparent, so it never ends in a line and is safe over a veil.
 * [strength] scales it: 1 behind top-level titles, less behind sheets and empty states. With
 * [circadian] the top-end light is the colour temperature of the hour ([circadianKelvin]): the
 * aurora is candlelit at night, gold at sunrise and sunset and sky-blue at midday.
 * [inset] is for a glow inside a page rather than at its top (an empty state): it feathers out at
 * the top as well as the bottom, so no edge shows where it begins.
 */
fun Modifier.horizon(strength: Float = 1f, circadian: Boolean = true, animate: Boolean = false, inset: Boolean = false): Modifier = composed {
    // Motion: the blobs drift, breathe and turn on unrelated cycles (9 to 19 seconds), so it never
    // visibly loops. The clock runs only while [animate], and never with reduced motion or
    // battery saver; a frame of drift only redraws, it never recomposes.
    val time = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val still = Kelvin.reduceMotion || LocalKelvinAccessibility.current.powerSave
    androidx.compose.runtime.LaunchedEffect(animate, still) {
        if (!animate || still) return@LaunchedEffect
        val start = androidx.compose.runtime.withFrameMillis { it } - (time.floatValue * 1000f).toLong()
        while (true) androidx.compose.runtime.withFrameMillis { time.floatValue = (it - start) / 1000f }
    }
    val c = Kelvin.colors
    val accent = c.horizon.copy(alpha = 1f)
    val a = (c.horizon.alpha * 1.9f).coerceAtMost(0.32f) * strength
    // Thermal: the partner light is the colour temperature of this hour, candlelight at night
    // and blue sky at noon, pushed to full colour and softened a touch toward white.
    val sky by rememberCircadianKelvin()
    val partner = remember(accent, sky, circadian) {
        if (!circadian || accent.chroma() < 0.04f) accent.temperaturePartner()
        else lerp(blackbody(sky).vivid(lift = 0f), Color.White, 0.2f)
    }
    val heat = c.glassHeat
    // The aurora is a handful of soft blobs (radial gradients, some stretched into ovals), each
    // its own size and colour: the accent, its temperature partner, heat and blends of them.
    // Each drifts on its own slow orbit, breathes and turns, so they overlap and part and mix
    // into new colours where they meet. A blob is a gradient, never a shape: it cannot fold or
    // show an edge, whatever the motion does. The whole field feathers out toward the bottom.
    val blobs = remember(accent, partner, heat) {
        // Colours that differ enough to make new ones where they overlap: the accent's cool
        // counterpart (blue against Ember, candlelight against Glacier) and two neighbours on the
        // hue wheel either side of the accent, besides the hour's light and heat.
        val cool = accent.temperaturePartner()
        val rose = accent.hueShifted(-38f)
        val amber = accent.hueShifted(26f)
        listOf(
            //         colour   x      y      radius aspect weight dx     dy     period phase
            AuroraBlob(accent, 0.10f, 0.06f, 0.74f, 1.35f, 1.00f, 0.16f, 0.10f, 17f, 0.0f),
            AuroraBlob(partner, 0.90f, 0.04f, 0.62f, 1.20f, 0.80f, 0.14f, 0.12f, 19f, 2.1f),
            AuroraBlob(cool, 0.78f, 0.34f, 0.46f, 0.85f, 0.55f, 0.24f, 0.16f, 14f, 4.4f),
            AuroraBlob(rose, 0.30f, 0.36f, 0.40f, 1.50f, 0.65f, 0.26f, 0.15f, 12f, 1.3f),
            AuroraBlob(heat, 0.52f, 0.46f, 0.36f, 1.70f, 0.60f, 0.28f, 0.12f, 10f, 5.2f),
            AuroraBlob(amber, 0.55f, 0.08f, 0.30f, 0.75f, 0.55f, 0.30f, 0.10f, 9f, 3.3f),
        )
    }
    this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawBehind {
            if (size.width <= 0f || size.height <= 0f || a <= 0f) return@drawBehind
            val t = time.floatValue
            val w = size.width
            val h = size.height
            for (b in blobs) {
                val ph = t * (2f * kotlin.math.PI.toFloat() / b.period) + b.phase
                // Lissajous drift on unrelated cycles, so it never visibly loops.
                val cx = (b.x + b.dx * kotlin.math.sin(ph)) * w
                val cy = (b.y + b.dy * kotlin.math.sin(ph * 0.73f + 1.1f)) * h
                val breathe = 1f + 0.12f * kotlin.math.sin(ph * 1.31f + 0.4f)
                val r = b.radius * w * breathe
                val angle = 25f * kotlin.math.sin(ph * 0.57f + b.phase)
                val alpha = (a * b.weight).coerceIn(0f, 1f)
                val brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to b.color.copy(alpha = alpha),
                    0.35f to b.color.copy(alpha = alpha * 0.72f),
                    0.7f to b.color.copy(alpha = alpha * 0.22f),
                    1f to b.color.copy(alpha = 0f),
                    center = Offset(cx, cy),
                    radius = r,
                )
                withTransform({
                    rotate(angle, Offset(cx, cy))
                    scale(b.aspect, 1f / b.aspect, Offset(cx, cy))
                }) {
                    drawCircle(brush, radius = r, center = Offset(cx, cy))
                }
            }
            // Feather the field to nothing toward the bottom: no edge where the aurora ends. Inset
            // in a page (an empty state), it feathers from the top too, so it has no edge at all.
            drawRect(
                if (inset) androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
                else androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, 0.4f to Color.Black, 1f to Color.Transparent),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
}

/** This colour turned [degrees] around the hue wheel, keeping its saturation and brightness. */
internal fun Color.hueShifted(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(alpha, red, green, blue), hsv)
    if (hsv[1] < 0.05f) return this
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** One light in the aurora: where it rests, how big and how stretched, how far and fast it drifts. */
private class AuroraBlob(
    val color: Color,
    val x: Float,
    val y: Float,
    val radius: Float,
    val aspect: Float,
    val weight: Float,
    val dx: Float,
    val dy: Float,
    val period: Float,
    val phase: Float,
)

/**
 * The other end of the colour-temperature scale from this accent: cool daylight for warm hues,
 * candlelight for cool ones, a soft violet between. Greys (Graphite) stay themselves.
 */
internal fun Color.temperaturePartner(): Color {
    if (chroma() < 0.04f) return this
    val warmth = red - blue
    return when {
        warmth > 0.08f -> Color(0xFF8FB3FF)
        warmth < -0.08f -> Color(0xFFFFB98A)
        else -> Color(0xFFC6A8FF)
    }
}

/**
 * Press-and-drag physics for glass. While held, the pane squashes like warm wax (wider and
 * lower) and is pulled toward the finger, with the pull easing off (tanh) so it never leaves
 * its place; it stretches along the direction of the pull and springs back on release. Pass
 * [layerBlock] to [glass] as `motion` so the backdrop stays aligned while the pane moves, and add
 * [gestures] to the same element. Reports the finger to an optional [Thaw].
 */
@Stable
class GlassPress internal constructor(private val scope: kotlinx.coroutines.CoroutineScope, private val thaw: Thaw?) {
    private val press = Animatable(0f)
    private val pull = Animatable(Offset.Zero, Offset.VectorConverter)
    private var start = Offset.Zero
    internal var deform = 0f
    /** How far the press has gone in, 0 to 1. */
    val value: Float get() = press.value
    /** Where the finger is, in the element's own coordinates; unspecified when not touched. */
    var touchAt by mutableStateOf(Offset.Unspecified)
        private set

    // Liquid-glass press (after Kyant's AndroidLiquidGlass, Apache 2.0): the pane swells by about
    // 4dp under the finger, is pulled toward it with the pull easing off (tanh), and stretches
    // along the pull.
    val layerBlock: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
        val k = 0.3f + 0.7f * deform
        val p = press.value
        if (deform > 0f && size.minDimension > 0f) {
            val o = pull.value
            val reach = size.minDimension
            translationX = reach * kotlin.math.tanh(0.05f * k * o.x / reach)
            translationY = reach * kotlin.math.tanh(0.05f * k * o.y / reach)
            val swell = 1f + k * p * 4.dp.toPx() / size.height
            val angle = kotlin.math.atan2(o.y, o.x)
            val stretch = k * 4.dp.toPx() / size.height
            scaleX = swell + stretch * kotlin.math.abs(kotlin.math.cos(angle) * o.x / size.maxDimension) * (size.width / size.height).coerceAtMost(1f)
            scaleY = swell + stretch * kotlin.math.abs(kotlin.math.sin(angle) * o.y / size.maxDimension) * (size.height / size.width).coerceAtMost(1f)
        }
    }

    /** Light that blooms under the finger while pressed: a faint lift over the pane and a soft pool where it touches. */
    internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPressLight(outline: androidx.compose.ui.graphics.Outline, strength: Float = 1f) {
        val p = press.value * strength
        if (p <= 0f) return
        drawOutline(outline, Color.White.copy(alpha = 0.08f * p), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
        val at = touchAt.takeIf { it != Offset.Unspecified }?.let { Offset(it.x.coerceIn(0f, size.width), it.y.coerceIn(0f, size.height)) } ?: center
        drawOutline(outline, Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.15f * p), 0.5f to Color.White.copy(alpha = 0.15f * p), 1f to Color.Transparent,
            center = at, radius = size.minDimension * 1.5f,
        ), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
    }

    val gestures: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            start = down.position
            touchAt = down.position
            thaw?.touch(down.position)
            scope.launch { press.animateTo(1f, spring(0.5f, 300f)) }
            scope.launch { pull.snapTo(Offset.Zero) }
            while (true) {
                // Read after everything else has seen the touch: once a scroll (or any other
                // gesture) claims it, or the finger heads up or down, the pebble lets go and
                // springs home instead of warping along while the page moves.
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed) break
                val moved = change.position - start
                if (kotlin.math.abs(moved.y) > viewConfiguration.touchSlop && kotlin.math.abs(moved.y) > kotlin.math.abs(moved.x)) break
                touchAt = change.position
                thaw?.touch(change.position)
                scope.launch { pull.animateTo(change.position - start, spring(0.9f, 1500f)) }
            }
            thaw?.touch(null)
            scope.launch { press.animateTo(0f, spring(0.5f, 300f)) }
            scope.launch { pull.animateTo(Offset.Zero, spring(0.5f, 300f)) }
        }
    }
}

@Composable
fun rememberGlassPress(thaw: Thaw? = null): GlassPress {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val press = remember(scope, thaw) { GlassPress(scope, thaw) }
    press.deform = Kelvin.glass.deform
    return press
}

/** Click semantics without press visuals, for glass elements whose motion comes from [GlassPress]. */
fun Modifier.kelvinClick(onClick: () -> Unit, enabled: Boolean = true, onClickLabel: String? = null): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
        .graphicsLayer { alpha = if (enabled) 1f else KelvinMotion.DISABLED_ALPHA }

/** How [KIcon] draws glyphs: [Filled] (Kelvin's default) or [Outline], Tabler's 1.5 stroke. */
enum class IconStyle { Filled, Outline }

val LocalIconStyle = androidx.compose.runtime.staticCompositionLocalOf { IconStyle.Filled }

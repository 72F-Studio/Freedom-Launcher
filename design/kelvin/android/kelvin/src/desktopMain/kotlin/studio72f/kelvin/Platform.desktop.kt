package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

@Composable
internal actual fun kelvinString(string: KelvinString): String = string.english

internal actual fun kelvinSystemLine(): String =
    "System: ${System.getProperty("os.name")} ${System.getProperty("os.version")}, Java ${System.getProperty("java.version")}"

// Desktop loads the bundled static weights from the library's own resources (font/ in the jar). The variable files
// are Android's; here a weight between two statics takes the nearer one.
private fun family(prefix: String, weights: List<Int>): FontFamily =
    FontFamily(weights.map { w -> Font(resource = "font/${prefix}_$w.ttf", weight = FontWeight(w)) })

private val display: FontFamily = family("onest", listOf(300, 400, 500, 600, 700))
private val text: FontFamily = family("montserrat", listOf(400, 500, 600, 700))

internal actual fun kelvinFamily(face: KelvinFace): FontFamily = if (face == KelvinFace.Display) display else text

internal actual fun kelvinDisplayAt(weight: Int): FontFamily = display

/**
 * Desktop has no power-saving or animation switch Kelvin can read yet, so only the app's own choice counts. Motion is
 * reduced by default all the same: a desktop mail window holds still.
 */
@Composable
internal actual fun rememberKelvinAccessibility(appReduceMotion: Boolean): KelvinAccessibility =
    remember(appReduceMotion) { KelvinAccessibility(reduceMotion = appReduceMotion) }

/** A few widening, fading capsules stand in for a blurred shadow. */
internal actual fun DrawScope.drawCapsuleShadow(blur: Float, offsetY: Float, alpha: Float) {
    val steps = 4
    for (i in steps downTo 1) {
        val grow = blur * i / steps
        drawRoundRect(
            color = Color.Black.copy(alpha = alpha / (steps * 1.5f)),
            topLeft = Offset(-grow, offsetY - grow),
            size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius((size.height + 2 * grow) / 2f),
        )
    }
}

private object DoodleFiles

/** Doodles ship inside the library's jar, under raw/. */
@Composable
internal actual fun rememberDoodleReader(): (String) -> List<String> = remember {
    { file ->
        DoodleFiles::class.java.getResourceAsStream("/raw/$file.txt")?.bufferedReader()?.use { it.readLines() }.orEmpty()
    }
}

/** A desktop has no haptic motor. */
@Composable
internal actual fun rememberHapticPerformer(): (HapticKind) -> Unit = remember { { } }

/** Skia blurs the shadow, as Android's shadow layer does. */
internal actual fun pathShadowPainter(
    path: androidx.compose.ui.graphics.Path,
    blur: Float,
    offsetY: Float,
    color: Color,
): DrawScope.() -> Unit {
    val native = path.asSkiaPath()
    val paint = org.jetbrains.skia.Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, blur / 2f)
    }
    return {
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.translate(0f, offsetY)
        drawContext.canvas.nativeCanvas.drawPath(native, paint)
        drawContext.canvas.nativeCanvas.restore()
    }
}

/** Desktop draws 72F's apps in the Flat finish, where pebbles are plain: no shader to run. */
internal actual fun pebblePainter(agsl: String): PebblePainter? = null

@Composable
internal actual fun KelvinBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** A desktop window does not blur what is behind it; panes are solid. */
@Composable
actual fun KelvinWindowGlass(): Boolean = false

@Composable
actual fun kelvinCrossWindowBlur(): Boolean = false

// Glass on desktop. 72F's desktop apps use the Flat finish: a pane is a solid fill with a hairline edge, and a
// control is a plain pebble. Nothing is recorded or blurred, so a backdrop only carries its ground colour.

@androidx.compose.runtime.Stable
actual class KelvinBackdrop internal constructor(private val groundState: androidx.compose.runtime.State<Color>) {
    internal actual val ground: Color get() = groundState.value
}

@Composable
actual fun rememberKelvinBackdrop(ground: Color): KelvinBackdrop {
    val groundState = androidx.compose.runtime.rememberUpdatedState(ground)
    return remember { KelvinBackdrop(groundState) }
}

actual fun Modifier.kelvinBackdrop(backdrop: KelvinBackdrop): Modifier = this

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
    val colors = Kelvin.colors
    val colored = content == GlassContent.Colored
    val plainTint = tint ?: colors.glassTint
    val moved = if (motion != null) Modifier.graphicsLayer(motion) else Modifier
    if (isPebble) return@composed moved.pebble(shape, if (colored) plainTint else tint ?: colors.raised, pebble)
    val fill = (if (colored) plainTint.vivid() else plainTint).copy(alpha = 1f)
    moved
        .then(if (elevated) Modifier.shadow(16.dp, shape) else Modifier)
        .background(fill, shape)
        .then(if (colored) Modifier else Modifier.drawWithContent { drawContent(); drawInsetEdge(shape, colors.hairline, 1.dp.toPx()) })
        .clip(shape)
}

/** The veil on desktop: the canvas itself over the page, fading out at its foot so there is no edge. */
actual fun Modifier.veil(backdrop: KelvinBackdrop?, fade: Dp): Modifier = composed {
    val canvas = Kelvin.colors.canvas
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val f = (fade.toPx() / size.height).coerceIn(0.01f, 1f)
            drawRect(veilFade(f), blendMode = BlendMode.DstIn)
        }
        .background(canvas)
}

/** Nothing shows through a desktop veil, so its ink is the theme's. */
@Composable
internal actual fun rememberVeilInk(page: KelvinBackdrop?, height: Int): Color = Kelvin.colors.ink

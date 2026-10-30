package studio72f.kelvin

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/**
 * The veil behind top chrome, as HyperOS draws it: no pane and no edge, just the page blurring
 * and washing into the canvas toward the top of the screen and fading to nothing below the
 * chrome, so content slides under it without a seam. Use it for headers that span the screen;
 * floating chrome (the dock, sheets, badges) is [glass].
 *
 * The blur and wash follow the Glass level: near-opaque at the left end, a light haze at Clear,
 * an opaque canvas with high-contrast text. Below Android 12 the wash alone carries it.
 */
fun Modifier.veil(backdrop: KelvinBackdrop?, fade: Dp = 32.dp): Modifier = composed {
    val params = Kelvin.glass
    val canvas = Kelvin.colors.canvas
    // Under Frost the veil melts the page as deeply as the panes do.
    val blurDp = if (params.isSolid) 0f else if (params.isFrost) params.frostDp else 8f + 22f * (1f - smoothLevel(params.level))
    val wash = veilWash(params, backdrop)
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            // Fade the whole veil out over its bottom [fade]: no edge where it ends.
            val f = (fade.toPx() / size.height).coerceIn(0.01f, 1f)
            drawRect(
                Brush.verticalGradient(0f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
        .drawBackdrop(
            backdrop = backdrop?.layer ?: emptyBackdrop(),
            shape = { RectangleShape },
            // The library's defaults light a rim and cast a shadow; a veil has no edge, and the
            // rim showed as a stroke along the top and sides of every header.
            highlight = null,
            shadow = null,
            effects = {
                if (params.isSolid || backdrop == null || !canBlur) return@drawBackdrop
                blur(blurDp.dp.toPx())
            },
            onDrawSurface = {
                val top = if (canBlur) wash else maxOf(wash, 0.9f)
                // Denser toward the status bar, where the most text sits over the most content.
                drawRect(Brush.verticalGradient(0f to canvas.copy(alpha = minOf(1f, top + 0.1f)), 1f to canvas.copy(alpha = top)))
            },
        )
}

/** How much of the canvas washes over the blurred page at the top of the veil. */
internal fun veilWash(params: GlassParams, backdrop: KelvinBackdrop?): Float =
    if (params.isSolid || backdrop == null) 1f
    else if (params.isFrost) 0.2f + 0.7f * params.tintAlpha
    else 0.25f + 0.65f * (1f - smoothLevel(params.level))

private fun smoothLevel(level: Float): Float {
    val t = level.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * How much of the veil's fade reaches below the chrome. Most of it sits inside the chrome's own
 * bottom edge, so the first thing on the page (a progress card, a notice) is never washed over;
 * only a quarter spills past to soften the line where the page passes under.
 */
private const val VeilSpill = 0.25f

/**
 * Ink for text set straight on the veil (the large title, header icons). It follows what shows
 * through: dark ink over a light page, white once dark content scrolls under a thin veil.
 * Inside [KelvinVeiledHeader]; elsewhere it is unspecified.
 */
val LocalVeilInk = compositionLocalOf { Color.Unspecified }

/**
 * Inside the content of a [KelvinVeiledPage] or [KelvinVeiledScreen]: how far the header reaches
 * down the page. Pad the top of what scrolls by it (inside the scroll, or as a list's content
 * padding) so the page starts below the header and slides up under it into the veil.
 */
val LocalVeilInset = compositionLocalOf { 0.dp }

/**
 * Top chrome over a scrolling page: [content] sits on a [veil] that fades out over [fade], mostly inside the chrome.
 * Measure the chrome with [onHeight] to pad the page. Text set directly on the veil should use
 * [LocalVeilInk], which turns light over dark content and back, so the title stays readable at
 * every Glass level.
 */
@Composable
fun KelvinVeiledHeader(
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
    fade: Dp = 32.dp,
    onHeight: (Int) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val ink = rememberVeilInk(backdrop, height)

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(with(density) { height.toDp() } + fade * VeilSpill)
                .veil(backdrop, fade),
        )
        CompositionLocalProvider(LocalVeilInk provides ink) {
            Box(Modifier.fillMaxWidth().onSizeChanged { height = it.height; onHeight(it.height) }, content = content)
        }
    }
}

/**
 * A page with top chrome on a veil, layered the way it stands physically: [content] (which
 * must put `Modifier.kelvinBackdrop(page)` on what scrolls) at the bottom, the [veil] over it,
 * and [header] on top. Glass inside [header] gets `chrome`, a backdrop of the page *with* the
 * veil, so a glass pill over the header refracts the blurred, washed view around it rather
 * than the raw page beneath the veil. Text on the veil uses [LocalVeilInk].
 */
@Composable
fun KelvinVeiledPage(
    page: KelvinBackdrop,
    modifier: Modifier = Modifier,
    fade: Dp = 32.dp,
    onHeaderHeight: (Int) -> Unit = {},
    header: @Composable BoxScope.(chrome: KelvinBackdrop) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val chrome = rememberKelvinBackdrop()
    val ink = rememberVeilInk(page, height)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().kelvinBackdrop(chrome)) {
            CompositionLocalProvider(LocalVeilInset provides with(density) { height.toDp() }) { content() }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(density) { height.toDp() } + fade * VeilSpill)
                    .veil(page, fade),
            )
        }
        CompositionLocalProvider(LocalVeilInk provides ink) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { height = it.height; onHeaderHeight(it.height) },
            ) { header(chrome) }
        }
    }
}

/**
 * Ink that follows what shows through the veil over [page]'s top [height] pixels.
 *
 * The page is sampled once, then again only after the window redraws (a scroll, a photo
 * arriving), at most every 250 ms, so a still screen does no work at all. Each sample draws
 * the page into a tiny [android.media.ImageReader] surface and reads it when RenderThread
 * delivers the frame, the route AndroidX's own layer snapshot takes below API 28: the main
 * thread never waits on the GPU, as it does for `GraphicsLayer.toImageBitmap()` on API 28+.
 */
@Composable
private fun rememberVeilInk(page: KelvinBackdrop?, height: Int): Color {
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = Kelvin.dark
    val wash = veilWash(Kelvin.glass, page)
    val canvasLum = Kelvin.colors.canvas.luminance()
    var lightInk by remember(dark) { mutableStateOf(dark) }
    LaunchedEffect(page, wash, height, dark, view) {
        val layer = page?.graphicsLayer
        if (layer == null || wash >= 0.85f || height <= 0) { lightInk = dark; return@LaunchedEffect }
        val redrawn = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val onDraw = android.view.ViewTreeObserver.OnDrawListener { redrawn.trySend(Unit) }
        view.viewTreeObserver.addOnDrawListener(onDraw)
        val probe = LuminanceProbe()
        try {
            while (true) {
                val lum = probe.sample(layer, (height * 0.6f).toInt(), density)
                if (lum != null) {
                    val seen = wash * canvasLum + (1f - wash) * lum
                    lightInk = if (lightInk) seen < 0.32f else seen < 0.22f
                }
                redrawn.receive()
                delay(250)
                redrawn.tryReceive()
            }
        } finally {
            // The observer may have been replaced while the view was detached.
            view.viewTreeObserver.let { if (it.isAlive) it.removeOnDrawListener(onDraw) }
            probe.close()
        }
    }
    val ink by animateColorAsState(if (lightInk) KelvinDark.ink else KelvinLight.ink, Kelvin.motion.gentle(), label = "veilInk")
    return ink
}

/** Mean relative luminance of a page's top rows, drawn into a 32x12 surface and read back off the main thread's critical path. */
private class LuminanceProbe {
    private val reader = android.media.ImageReader.newInstance(PW, PH, android.graphics.PixelFormat.RGBA_8888, 2)
    private val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    private var pending: kotlinx.coroutines.CancellableContinuation<Float?>? = null

    init {
        // Always drain, even a frame nobody waits for any more, so the next draw never blocks on a full queue.
        reader.setOnImageAvailableListener({ r ->
            val lum = r.acquireLatestImage()?.use { meanLuminance(it) }
            pending?.let { pending = null; if (it.isActive) it.resumeWith(Result.success(lum)) }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    suspend fun sample(page: GraphicsLayer, rows: Int, density: androidx.compose.ui.unit.Density): Float? {
        val w = page.size.width
        if (w <= 0 || rows <= 0) return null
        return kotlinx.coroutines.withTimeoutOrNull(1_000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                pending = cont
                cont.invokeOnCancellation { if (pending === cont) pending = null }
                val posted = runCatching {
                    val canvas = reader.surface.lockHardwareCanvas()
                    try {
                        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                        drawScope.draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), androidx.compose.ui.geometry.Size(PW.toFloat(), PH.toFloat())) {
                            scale(PW / w.toFloat(), PH / rows.toFloat(), pivot = Offset.Zero) { drawLayer(page) }
                        }
                    } finally {
                        reader.surface.unlockCanvasAndPost(canvas)
                    }
                }.isSuccess
                if (!posted && pending === cont) {
                    pending = null
                    cont.resumeWith(Result.success(null))
                }
            }
        }
    }

    fun close() {
        reader.setOnImageAvailableListener(null, null)
        reader.close()
    }

    private fun meanLuminance(image: android.media.Image): Float {
        val plane = image.planes[0]
        val buf = plane.buffer
        var sum = 0f
        for (y in 0 until PH) for (x in 0 until PW) {
            val i = y * plane.rowStride + x * plane.pixelStride
            val a = buf.get(i + 3).toInt() and 0xff
            if (a == 0) continue // Transparent reads as black, as the bitmap readback did.
            // RGBA, premultiplied.
            fun ch(o: Int) = ((buf.get(i + o).toInt() and 0xff) * 255 / a).coerceAtMost(255)
            sum += Color(ch(0), ch(1), ch(2)).luminance()
        }
        return sum / (PW * PH)
    }

    private companion object {
        const val PW = 32
        const val PH = 12
    }
}

/**
 * Every screen's title bar, the way the top-level pages have it: [header] on a veil over the page
 * (the page blurring and washing into the canvas toward the top, fading to nothing below the bar),
 * with [content] running the full height of the screen beneath it. Unlike [KelvinVeiledPage] the
 * page is recorded for you; [content] only pads its scrolling top by [LocalVeilInset]. Glass in
 * [header] gets `chrome`; nothing in [content] may refract the page it is part of.
 */
@Composable
fun KelvinVeiledScreen(
    modifier: Modifier = Modifier,
    fade: Dp = 32.dp,
    header: @Composable BoxScope.(chrome: KelvinBackdrop) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val page = rememberKelvinBackdrop()
    KelvinVeiledPage(page, modifier, fade, header = header) {
        Box(Modifier.fillMaxSize().kelvinBackdrop(page), content = content)
    }
}


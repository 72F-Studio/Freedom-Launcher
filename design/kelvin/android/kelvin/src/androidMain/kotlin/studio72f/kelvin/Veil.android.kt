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

actual fun Modifier.veil(backdrop: KelvinBackdrop?, fade: Dp): Modifier = composed {
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
            // Fade the whole veil out over its bottom [fade]: no edge where it ends. The fade holds
            // on before it lets go: most of it lies inside the chrome, under the title's lower half,
            // and a straight ramp let the page show sharp through the title's own letters.
            val f = (fade.toPx() / size.height).coerceIn(0.01f, 1f)
            drawRect(veilFade(f), blendMode = BlendMode.DstIn)
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


@Composable
internal actual fun rememberVeilInk(page: KelvinBackdrop?, height: Int): Color {
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


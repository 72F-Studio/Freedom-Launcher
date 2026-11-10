package studio72f.kelvin

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

/**
 * The veil behind top chrome, as HyperOS draws it: no pane and no edge, just the page blurring
 * and washing into the canvas toward the top of the screen and fading to nothing below the
 * chrome, so content slides under it without a seam. Use it for headers that span the screen;
 * floating chrome (the dock, sheets, badges) is [glass].
 *
 * The blur and wash follow the Glass level: near-opaque at the left end, a light haze at Clear,
 * an opaque canvas with high-contrast text. Below Android 12 the wash alone carries it.
 */
expect fun Modifier.veil(backdrop: KelvinBackdrop?, fade: Dp = 32.dp): Modifier

/** How much of the canvas washes over the blurred page at the top of the veil. */
internal fun veilWash(params: GlassParams, backdrop: KelvinBackdrop?): Float =
    if (params.isSolid || backdrop == null) 1f
    else if (params.isFrost) 0.2f + 0.7f * params.tintAlpha
    else 0.25f + 0.65f * (1f - smoothLevel(params.level))

internal fun smoothLevel(level: Float): Float {
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
 * The veil's own fade over the last [f] of its height: nearly whole through the first half of the
 * fade, then gone by the end, so what the chrome holds (a title, its icons) never has the page
 * showing sharp behind it, and the veil still ends without a line.
 */
internal fun veilFade(f: Float): Brush = Brush.verticalGradient(
    0f to Color.Black,
    (1f - f * 0.55f) to Color.Black,
    (1f - f * 0.25f) to Color.Black.copy(alpha = 0.5f),
    1f to Color.Transparent,
)

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
 * [veilAlpha] fades the veil alone (read at draw time), for chrome that arrives on scroll.
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
    veilAlpha: () -> Float = { 1f },
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
                .graphicsLayer { alpha = veilAlpha() }
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
internal expect fun rememberVeilInk(page: KelvinBackdrop?, height: Int): Color

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


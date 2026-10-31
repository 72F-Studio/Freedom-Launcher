package com.studio72f.eigen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.core.graphics.drawable.toBitmap
import com.studio72f.eigen.MainActivity
import kotlinx.coroutines.launch
import studio72f.kelvin.Kelvin
import java.util.WeakHashMap

/**
 * Kelvin's press (warm-wax squash, a sheen that blooms from the finger, wobble on release) with a long press,
 * which [studio72f.kelvin.kelvinPress] does not offer. Apps and drawers need both.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.eigenPress(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RectangleShape,
    label: String? = null,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val press = remember { Animatable(0f) }
    val at = remember { arrayOf<Offset?>(null) }
    val deform = Kelvin.glass.deform
    val reduce = Kelvin.reduceMotion
    val snappy = Kelvin.motion.snappy<Float>()
    val wobble = Kelvin.motion.wobble<Float>()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> { at[0] = i.pressPosition; launch { press.animateTo(1f, snappy) } }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch { press.animateTo(0f, wobble) }
            }
        }
    }
    this
        .graphicsLayer {
            if (!reduce) {
                val squash = 0.03f + 0.03f * deform
                scaleX = 1f + squash * 0.6f * press.value
                scaleY = 1f - squash * press.value
                transformOrigin = TransformOrigin.Center
            }
        }
        .combinedClickable(
            interactionSource = source, indication = null, role = Role.Button, onClickLabel = label,
            onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = onClick,
        )
        .drawWithContent {
            drawContent()
            val p = press.value
            if (p <= 0f) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Brush.radialGradient(
                listOf(Color.White.copy(alpha = (0.10f + 0.14f * deform) * p), Color.White.copy(alpha = 0f)),
                center = at[0] ?: center, radius = size.minDimension * 1.2f,
            ))
        }
}

private val iconCache = WeakHashMap<android.graphics.drawable.Drawable, androidx.compose.ui.graphics.ImageBitmap>()

/** Converts icons to bitmaps ahead of time (off the main thread), so opening drawers never waits on uploads. */
fun prewarmIcons(apps: List<MainActivity.App>) {
    for (a in apps) {
        val d = a.icon ?: continue
        synchronized(iconCache) { if (iconCache.containsKey(d)) null else d }?.let { drawable ->
            val bmp = runCatching { drawable.toBitmap(144, 144).asImageBitmap().also { it.prepareToDraw() } }.getOrNull() ?: return@let
            synchronized(iconCache) { iconCache.putIfAbsent(drawable, bmp) }
        }
    }
}

/** An app's icon (icon packs and all, as the activity loaded it), optionally desaturated. */
@Composable
fun AppIcon(app: MainActivity.App, size: Dp, mono: Boolean, modifier: Modifier = Modifier) {
    val drawable = app.icon ?: return
    val bitmap = remember(drawable) {
        synchronized(iconCache) { iconCache.getOrPut(drawable) { drawable.toBitmap(144, 144).asImageBitmap() } }
    }
    val filter = remember(mono) { if (mono) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null }
    Image(bitmap, null, modifier.size(size), colorFilter = filter)
}

fun MainActivity.App.title(): String = (name ?: label ?: "").toString()

/**
 * A sideways carousel that is clipped only by the screen, never by the card or padded column it sits in: it
 * bleeds [bleed] past its parent's edges and puts the same amount inside the scroll, so the first item still lines
 * up with the content above it while overflow reads clearly at the screen edge.
 */
@Composable
fun CarouselRow(bleed: Dp, modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier.bleed(bleed).horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = bleed),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content,
    )
}

/** Widens this element by [horizontal] on both sides past its parent's bounds (see [CarouselRow]). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val px = horizontal.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px) else constraints
    val placeable = measurable.measure(wide)
    layout((placeable.width - 2 * px).coerceAtLeast(0), placeable.height) { placeable.place(-px, 0) }
}

/**
 * Frost for any surface over the wallpaper (chat bubbles, conversation and widget cards): its slice of the frosted
 * wallpaper, lined up with the wallpaper behind it, a smoky shade and a milky lift, clipped to [shape]. Its place on
 * screen is read only while drawing, so moving redraws without recomposing. [tint] stains it (a sent bubble).
 */
fun Modifier.frostSurface(state: EigenState, shape: Shape, milk: Float = 0.11f, tint: Color? = null): Modifier = composed {
    val bounds = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val soft = state.softWallpaper
    val root = androidx.compose.ui.platform.LocalView.current.rootView
    val image = remember(soft) { soft?.asImageBitmap() }
    val filter = remember(state.greyscale) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (state.greyscale) 0f else 1.35f) }) }
    val photo = state.wallpaperMode == "photo" && image != null
    this.onGloballyPositioned { bounds.value = it.boundsInRoot() }
        .clip(shape)
        .drawBehind {
            if (photo && image != null) drawFrostSlice(image, root.width.toFloat(), root.height.toFloat(), bounds.value, filter, state.dim, milk)
            else drawRect(Color.White.copy(alpha = milk + 0.04f))
            if (tint != null) drawRect(tint)
        }
}

/** Draws the part of [image] (the frosted wallpaper, centre-cropped to a [w]×[h] screen) that lies behind [b]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrostSlice(
    image: androidx.compose.ui.graphics.ImageBitmap, w: Float, h: Float, b: androidx.compose.ui.geometry.Rect, filter: ColorFilter, dim: Float, milk: Float,
) {
    if (w <= 0f || h <= 0f) return
    val scale = maxOf(w / image.width, h / image.height)
    val dw = image.width * scale; val dh = image.height * scale
    drawImage(
        image,
        dstOffset = androidx.compose.ui.unit.IntOffset(((w - dw) / 2f - b.left).toInt(), ((h - dh) / 2f - b.top).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
        colorFilter = filter,
    )
    drawRect(Color.Black.copy(alpha = (dim * 0.45f + 0.08f).coerceAtMost(0.6f)))
    drawRect(Color.White.copy(alpha = milk))
}

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

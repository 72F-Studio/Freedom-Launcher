package studio72f.kelvin

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil

/**
 * The platform's own backdrop effects (Android 17 QPR2, API 37.2): a [RenderNode] given
 * `setBackdropRenderEffect` blurs and shades whatever is already drawn behind it in the window.
 * No copy of the page is recorded, so the pane always sees exactly what is under it, wherever
 * it has moved, and glass can never end up drawing the page it is part of.
 */
internal object NativeBackdrop {
    /** API 37.2 or later. `SDK_INT_FULL` exists from API 36. */
    val available: Boolean = Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= 3_700_002
}

/** A soft shadow under a pane: [color] at [alpha], blurred by [radius], dropped by [drop]. */
internal data class PaneShadow(val color: Color, val alpha: Float, val radius: Dp, val drop: Dp)

/**
 * Kelvin Frost drawn by the platform: a node clipped to [shape] whose backdrop effect is a
 * [blur] followed by Kelvin's frost shader (coat, saturation, contrast floor, grain and, for
 * pebbles, the bevel finish), with [uniforms] set exactly as on the Backdrop path. [shadow] is
 * drawn outside the pane only, so the pane never sees its own shadow through itself. [motion]
 * moves the pane; [press] adds the light where it is touched.
 */
@SuppressLint("NewApi")
internal fun Modifier.nativeFrost(
    shape: Shape,
    blur: Dp,
    uniforms: (Size, FloatArray, Float, (String, FloatArray) -> Unit) -> Unit,
    shadow: PaneShadow?,
    motion: (GraphicsLayerScope.() -> Unit)?,
    press: GlassPress?,
): Modifier = composed {
    // Parsed once per pane for its whole life; only uniforms change after that.
    val shader = remember { RuntimeShader(FROST_AGSL) }
    val node = remember { RenderNode("kelvin-frost") }
    this
        .then(if (motion != null) Modifier.graphicsLayer(motion) else Modifier)
        .then(
            if (shadow == null) Modifier else Modifier.drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.TRANSPARENT
                    setShadowLayer(shadow.radius.toPx(), 0f, shadow.drop.toPx(), shadow.color.copy(alpha = shadow.alpha).toArgb())
                }
                onDrawBehind {
                    // Outside the pane only: the backdrop must not see the shadow through itself.
                    clipPath(path, ClipOp.Difference) {
                        drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
                    }
                }
            },
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = android.graphics.Outline().apply {
                when (outline) {
                    is Outline.Rectangle -> setRect(0, 0, ceil(size.width).toInt(), ceil(size.height).toInt())
                    else -> setPath(Path().apply { addOutline(outline) }.asAndroidPath())
                }
            }
            val radii = shape.paneRadii(size, layoutDirection, this)
            val radius = blur.toPx().coerceAtLeast(1f)
            onDrawWithContent {
                val w = ceil(size.width).toInt()
                val h = ceil(size.height).toInt()
                if (w > 0 && h > 0) {
                    uniforms(size, radii, 0f) { name, v -> shader.setFloatUniform(name, v) }
                    node.setPosition(0, 0, w, h)
                    node.setOutline(clip)
                    node.setClipToOutline(true)
                    node.setBackdropRenderEffect(
                        RenderEffect.createChainEffect(
                            RenderEffect.createRuntimeShaderEffect(shader, "content"),
                            RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP),
                        ),
                    )
                    // The node carries no drawing of its own; the effect is the pane.
                    node.beginRecording(w, h).drawColor(android.graphics.Color.TRANSPARENT)
                    node.endRecording()
                    drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
                }
                press?.run { drawPressLight(outline) }
                drawContent()
            }
        }
}

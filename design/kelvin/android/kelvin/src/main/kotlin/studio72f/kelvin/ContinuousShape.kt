package studio72f.kelvin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Rounded rectangle with continuous (G2) corners: each corner eases from the straight edge into a
 * shorter circular arc through two cubic transitions, so the curvature never jumps. [smoothing]
 * 0 is an ordinary rounded rectangle; Kelvin uses 0.6. When the corner would not fit, smoothing
 * shrinks first, then the radius.
 */
@Immutable
data class ContinuousShape(val radius: Dp, val smoothing: Float = 0.6f, val flatBottom: Boolean = false) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(continuousPath(size, with(density) { radius.toPx() }, smoothing, flatBottom = flatBottom))
}

/** A pill: semicircular ends at any size. */
val CapsuleShape = ContinuousShape(100_000.dp, 0f)

/**
 * A Kelvin media tile: continuous corners with a round notch cut out of the bottom-end corner
 * around a badge of [badgeSize], leaving a [moat] of space between badge and tile.
 */
@Immutable
data class NotchedShape(val radius: Dp, val badgeSize: Dp, val moat: Dp = 4.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tile = continuousPath(size, with(density) { radius.toPx() }, 0.6f)
        val b = with(density) { badgeSize.toPx() }
        val cut = with(density) { b / 2f + moat.toPx() }
        val cx = if (layoutDirection == LayoutDirection.Ltr) size.width - b / 2f else b / 2f
        val hole = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx, size.height - b / 2f), cut))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, tile, hole))
    }
}

fun continuousPath(size: Size, radius: Float, smoothing: Float, path: Path = Path(), flatBottom: Boolean = false): Path {
    path.reset()
    val w = size.width
    val h = size.height
    val maxP = min(w, h) / 2f
    val r = radius.coerceIn(0f, maxP)
    if (r <= 0f) {
        path.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        return path
    }
    val p = min((1f + smoothing) * r, maxP)
    val s = (p / r - 1f).coerceIn(0f, 1f)
    val g = CornerGeometry(r, s, p)

    // Each corner as (corner point, unit vector along the incoming edge, unit vector along the outgoing edge).
    val corners = listOf(
        Triple(Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f)),
        Triple(Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f)),
        Triple(Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f)),
        Triple(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f)),
    )
    val start = corners[3].let { (c, _, v) -> c + v * p }
    path.moveTo(start.x, start.y)
    corners.forEachIndexed { i, (c, u, v) ->
        if (flatBottom && (i == 1 || i == 2)) path.lineTo(c.x, c.y) else g.appendCorner(path, c, u, v)
    }
    path.close()
    return path
}

private class CornerGeometry(val r: Float, s: Float, val p: Float) {
    private val arcMeasure = 90f * (1f - s)
    private val arcSection = sin(rad(arcMeasure / 2f)) * r * sqrt(2f)
    private val angleAlpha = (90f - arcMeasure) / 2f
    private val p3ToP4 = r * tan(rad(angleAlpha / 2f))
    private val angleBeta = 45f * s
    val c = p3ToP4 * cos(rad(angleBeta))
    val d = c * tan(rad(angleBeta))
    val b = (p - arcSection - c - d) / 3f
    val a = 2f * b
    private val arcK = 4f / 3f * tan(rad(arcMeasure) / 4f) * r

    fun appendCorner(path: Path, corner: Offset, u: Offset, v: Offset) {
        fun at(alongU: Float, alongV: Float) = corner + u * alongU + v * alongV
        val s0 = at(-p, 0f)
        path.lineTo(s0.x, s0.y)
        val c1 = at(-p + a, 0f)
        val c2 = at(-p + a + b, 0f)
        val e1 = at(-p + a + b + c, d)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)

        val s2 = at(-d, p - a - b - c)
        if (arcMeasure > 0.01f) {
            val center = at(-r, r)
            val chord = s2 - e1
            val t1 = tangent(e1 - center, chord)
            val t2 = tangent(s2 - center, chord)
            val k1 = e1 + t1 * arcK
            val k2 = s2 - t2 * arcK
            path.cubicTo(k1.x, k1.y, k2.x, k2.y, s2.x, s2.y)
        } else {
            path.lineTo(s2.x, s2.y)
        }

        val c3 = at(0f, p - a - b)
        val c4 = at(0f, p - a)
        val e2 = at(0f, p)
        path.cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
    }

    /** Unit tangent of a circle at radius vector [radius], oriented along the direction of travel. */
    private fun tangent(radius: Offset, travel: Offset): Offset {
        val len = radius.getDistance().takeIf { it > 0f } ?: return Offset.Zero
        val t = Offset(-radius.y / len, radius.x / len)
        return if (t.x * travel.x + t.y * travel.y >= 0f) t else -t
    }

    private fun rad(deg: Float) = (deg * PI / 180.0).toFloat()
}

/**
 * A card in a [KelvinGroup]: continuous corners, [top] on the top two and [bottom] on the bottom
 * two, so the first and last cards of a group keep the group's round outer corners while the
 * cards between them meet with small ones.
 */
@Immutable
data class SegmentShape(val top: Dp, val bottom: Dp, val smoothing: Float = 0.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { top.toPx() }
        val b = with(density) { bottom.toPx() }
        val upper = continuousPath(size, t, smoothing)
        if (t == b) return Outline.Generic(upper)
        val lower = continuousPath(size, b, smoothing, Path())
        val half = size.height / 2f
        val topRect = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, half)) }
        val bottomRect = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, half, size.width, size.height)) }
        return Outline.Generic(
            Path.combine(PathOperation.Union, Path.combine(PathOperation.Intersect, upper, topRect), Path.combine(PathOperation.Intersect, lower, bottomRect)),
        )
    }
}

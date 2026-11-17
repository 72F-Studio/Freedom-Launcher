package studio72f.kelvin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * An app's mark for a desktop's tray (the system tray, the menu bar, the notification area): its glyph in white on a
 * rounded square of the app's [tile] colour, and, when [badge] is set, a dot at the top end corner that says
 * something waits (unread mail). The system draws it small, 16 to 24 pixels, so it is solid and plain: one glyph, no
 * glass, no gradient. The dot is [KelvinColors.critical] with a ring of the tray's own darkness cut around it, so it
 * reads on a light or a dark panel. Pass the result to the platform's tray.
 */
@Composable
fun rememberKelvinTrayMark(icon: ImageVector, tile: Color, badge: Boolean, dot: Color = Kelvin.colors.critical): Painter {
    val glyph = rememberVectorPainter(icon)
    return remember(glyph, tile, badge, dot) { TrayMark(glyph, tile, badge, dot) }
}

private class TrayMark(
    private val glyph: VectorPainter,
    private val tile: Color,
    private val badge: Boolean,
    private val dot: Color,
) : Painter() {
    override val intrinsicSize = Size(TRAY_SIZE, TRAY_SIZE)

    override fun DrawScope.onDraw() {
        val s = size.minDimension
        drawRoundRect(tile, cornerRadius = CornerRadius(s * TILE_CORNER))
        val g = s * GLYPH_SHARE
        translate((s - g) / 2f, (s - g) / 2f) {
            with(glyph) { draw(Size(g, g), colorFilter = ColorFilter.tint(Color.White)) }
        }
        if (badge) {
            val r = s * DOT_SHARE
            val at = Offset(s - r, r)
            // A clear ring between the dot and the tile, so the dot never melts into it.
            drawCircle(Color.Transparent, r * RING, at, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            drawCircle(dot, r, at)
        }
    }

    private companion object {
        const val TRAY_SIZE = 64f
        const val TILE_CORNER = 0.26f
        const val GLYPH_SHARE = 0.62f
        const val DOT_SHARE = 0.2f
        const val RING = 1.35f
    }
}

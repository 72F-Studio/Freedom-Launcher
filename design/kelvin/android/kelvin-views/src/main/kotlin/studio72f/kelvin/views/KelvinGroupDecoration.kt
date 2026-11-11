package studio72f.kelvin.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView

/**
 * A RecyclerView's rows as Kelvin's groups (GUIDELINES.md, Groups are wells): every run of rows that [belongs] in a
 * group sits in one `group` well inset by the gutter, with 28dp corners only where the run begins and ends, and
 * barely-there dividers between rows (one pixel of `ink` at 6%, 8% in dark) from [textStart] to 16dp short of the end.
 * It is the View twin of `KelvinGroup`, for lists too long to be one.
 *
 * The well is drawn behind the rows, so it follows them when they move and never has to be told. Rows should draw no
 * background of their own. Colours come from the theme's `kelvinGroup` and `kelvinInk`.
 */
class KelvinGroupDecoration(
    context: Context,
    /** Whether the adapter's item at a position sits in a well (a message, not a header or a footer). */
    private val belongs: (adapter: RecyclerView.Adapter<*>, position: Int) -> Boolean = { _, _ -> true },
    /** Where a row's text starts, so dividers start there too: 16dp, or 72dp after a 44dp avatar, 60dp after a tile. */
    textStartDp: Int = 16,
    /** The margin either side of the well: 16dp, or the app's own. */
    gutterDp: Int = 16,
    /** The well's corner: [SOFT_RADIUS_DP] (Kelvin's own), or [FIRM_RADIUS_DP] where the app's theme uses firm corners. */
    radiusDp: Int = SOFT_RADIUS_DP,
) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val gutter = gutterDp * density
    private val radius = radiusDp * density
    private val runGap = RUN_GAP_DP * density
    private val textStart = textStartDp * density
    private val dividerEnd = DIVIDER_END_DP * density

    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint().apply { strokeWidth = 1f }
    private val path = Path()
    private val rect = RectF()

    private fun RecyclerView.inWell(position: Int): Boolean {
        val adapter = adapter ?: return false
        return position in 0 until adapter.itemCount && belongs(adapter, position)
    }

    private fun RecyclerView.wellPosition(child: View): Int? =
        getChildAdapterPosition(child).takeIf { it != RecyclerView.NO_POSITION && inWell(it) }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.wellPosition(view) ?: return
        outRect.left = gutter.toInt()
        outRect.right = gutter.toInt()
        if (!parent.inWell(position - 1)) outRect.top = runGap.toInt()
        if (!parent.inWell(position + 1)) outRect.bottom = runGap.toInt()
    }

    // Index 1 is kelvinInk in the attribute array read here, not a styleable resource.
    @SuppressLint("ResourceType")
    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val theme = parent.context.obtainStyledAttributes(intArrayOf(R.attr.kelvinGroup, R.attr.kelvinInk))
        wellPaint.color = theme.getColor(0, DEFAULT_GROUP)
        val ink = theme.getColor(1, Color.BLACK)
        theme.recycle()
        val dark = ColorUtils.calculateLuminance(ink) > HALF
        dividerPaint.color = ColorUtils.setAlphaComponent(ink, ((if (dark) ALPHA_DARK else ALPHA_LIGHT) * FULL).toInt())

        var run: Run? = null
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.wellPosition(child)
            run = when {
                position == null -> {
                    run?.let { drawRun(canvas, parent, it) }
                    null
                }
                run == null -> Run(child, position, child, position)
                else -> {
                    drawDivider(canvas, parent, child.top + child.translationY)
                    run.copy(last = child, lastPosition = position)
                }
            }
        }
        run?.let { drawRun(canvas, parent, it) }
    }

    private data class Run(val first: View, val firstPosition: Int, val last: View, val lastPosition: Int)

    private fun drawRun(canvas: Canvas, parent: RecyclerView, run: Run) {
        // A run that carries on off screen runs past the edge, so corners appear only where the group begins and ends.
        val roundTop = !parent.inWell(run.firstPosition - 1)
        val roundBottom = !parent.inWell(run.lastPosition + 1)
        val top = if (roundTop) run.first.top + run.first.translationY else -radius
        val bottom = if (roundBottom) run.last.bottom + run.last.translationY else parent.height + radius
        val t = if (roundTop) radius else 0f
        val b = if (roundBottom) radius else 0f
        rect.set(parent.paddingLeft + gutter, top, parent.width - parent.paddingRight - gutter, bottom)
        path.reset()
        path.addRoundRect(rect, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
        canvas.drawPath(path, wellPaint)
    }

    private fun drawDivider(canvas: Canvas, parent: RecyclerView, y: Float) {
        val left = parent.paddingLeft + gutter
        val right = parent.width - parent.paddingRight - gutter
        val rtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val start = if (rtl) left + dividerEnd else left + textStart
        val end = if (rtl) right - textStart else right - dividerEnd
        canvas.drawLine(start, y, end, y, dividerPaint)
    }

    companion object {
        /** The well's corner with Kelvin's soft corners. */
        const val SOFT_RADIUS_DP = 28
        /** The well's corner with firm corners, the Compose group's large corner. */
        const val FIRM_RADIUS_DP = 16
        private const val RUN_GAP_DP = 8
        private const val DIVIDER_END_DP = 16
        private const val ALPHA_LIGHT = 0.06f
        private const val ALPHA_DARK = 0.08f
        private const val FULL = 255
        private const val HALF = 0.5
        private val DEFAULT_GROUP = Color.rgb(0xF3, 0xF4, 0xF6)
    }
}

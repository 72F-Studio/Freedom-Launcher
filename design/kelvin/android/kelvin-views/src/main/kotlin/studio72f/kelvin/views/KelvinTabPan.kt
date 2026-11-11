package studio72f.kelvin.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.drawToBitmap
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** How far a pane within a page pans on a tab switch: an eighth of its width (GUIDELINES.md, Tabs are places side by side). */
const val KELVIN_PANE_TRAVEL = 0.125f

/** How far a whole screen pans on a dock switch: a quarter of its width. */
const val KELVIN_SCREEN_TRAVEL = 0.25f

/**
 * Pans this View to a tab beside it, as `KelvinTabContent` does in Compose (GUIDELINES.md, Tabs are places side by
 * side): what it shows now slides [travel] of its width away from the tab picked and fades early; [swap] puts the new
 * content in, and it comes in from that tab's side on the same spring, with no bounce. [dir] is 1 for a tab to the
 * right, -1 for one to the left and 0 for a fade in place; right-to-left mirrors it. The old content leaves as a
 * snapshot laid over the View, so the two pass each other; a View whose parent is not a FrameLayout, or one too large
 * to snapshot, lets the new content in alone. A second pick mid-pan turns from where it is. Reduced motion (animators
 * off in the system's settings) swaps in place.
 */
fun View.panToTab(dir: Int, travel: Float = KELVIN_PANE_TRAVEL, swap: () -> Unit) {
    if (!ValueAnimator.areAnimatorsEnabled() || !isLaidOut || width == 0) {
        swap()
        return
    }
    running.remove(this)?.cancel()
    val parent = parent as? FrameLayout
    val ghost = parent?.let { host ->
        val bitmap = runCatching { drawToBitmap() }.getOrNull() ?: return@let null
        ImageView(context).apply {
            setImageBitmap(bitmap)
            alpha = this@panToTab.alpha
            translationX = this@panToTab.translationX
            layoutParams = FrameLayout.LayoutParams(this@panToTab.width, this@panToTab.height).apply {
                leftMargin = this@panToTab.left - host.paddingLeft
                topMargin = this@panToTab.top - host.paddingTop
            }
            host.addView(this, host.indexOfChild(this@panToTab) + 1)
        }
    }
    val mirrored = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -dir else dir
    val distance = width * travel * mirrored
    val pan = Pan(this, ghost)
    running[this] = pan
    if (ghost != null) {
        ghost.animate()
            .translationX(ghost.translationX - distance)
            .setDuration(if (dir == 0) GENTLE_MS else PAN_MS)
            .setInterpolator(if (dir == 0) GENTLE else PAN_SPRING)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = pan.dropGhost()
                },
            )
            .start()
        pan.fades += ObjectAnimator.ofFloat(ghost, View.ALPHA, ghost.alpha, 0f).apply {
            duration = if (dir == 0) GENTLE_MS else EARLY_MS
            interpolator = if (dir == 0) GENTLE else EARLY
            start()
        }
    }
    swap()
    translationX = distance
    alpha = 0f
    animate()
        .translationX(0f)
        .setDuration(if (dir == 0) GENTLE_MS else PAN_MS)
        .setInterpolator(if (dir == 0) GENTLE else PAN_SPRING)
        .setListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (running[this@panToTab] === pan) running.remove(this@panToTab)
                }
            },
        )
        .start()
    pan.fades += ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f).apply {
        duration = if (dir == 0) GENTLE_MS else ARRIVE_MS
        interpolator = if (dir == 0) GENTLE else ARRIVE
        start()
    }
}

/** One pan in flight: the snapshot leaving and the fades, so a second pick can stop them where they are. */
private class Pan(private val view: View, private var ghost: ImageView?) {
    val fades = mutableListOf<ObjectAnimator>()

    fun cancel() {
        fades.forEach { it.cancel() }
        view.animate().cancel()
        ghost?.animate()?.cancel()
        dropGhost()
        view.alpha = 1f
    }

    fun dropGhost() {
        val g = ghost ?: return
        ghost = null
        (g.parent as? FrameLayout)?.removeView(g)
        (g.drawable as? BitmapDrawable)?.bitmap?.recycle()
        g.setImageDrawable(null)
    }
}

private val running = WeakHashMap<View, Pan>()

/**
 * The time curve of one of Kelvin's springs, a [damping] ratio over a [stiffness], read as an interpolator that
 * settles within [durationMs]: the same motion the Compose side gets from `spring()`, for a View animator that
 * wants a duration and a curve.
 */
internal class KelvinSpringInterpolator(
    private val damping: Float,
    stiffness: Float,
    private val durationMs: Long,
) : TimeInterpolator {
    private val w0 = sqrt(stiffness)
    private val end = at(durationMs / 1000f)

    private fun at(t: Float): Float {
        if (damping >= 1f) {
            return 1f - (1f + w0 * t) * exp(-w0 * t)
        }
        val wd = w0 * sqrt(1f - damping * damping)
        val e = exp(-damping * w0 * t)
        return 1f - e * (cos(wd * t) + (damping * w0 / wd) * sin(wd * t))
    }

    override fun getInterpolation(input: Float): Float = (at(input * durationMs / 1000f) / end).coerceIn(0f, 1f)
}

// The tab spring (damping 0.9, stiffness 360), the early fade of what leaves (1, 900), the fade of what arrives
// (1, 500) and the gentle fade for a switch with no side (0.9, 260): GUIDELINES.md, Motion, as KelvinTransitions
// sets them.
private const val PAN_MS = 320L
private const val EARLY_MS = 160L
private const val ARRIVE_MS = 200L
private const val GENTLE_MS = 300L
private val PAN_SPRING = KelvinSpringInterpolator(0.9f, 360f, PAN_MS)
private val EARLY = KelvinSpringInterpolator(1f, 900f, EARLY_MS)
private val ARRIVE = KelvinSpringInterpolator(1f, 500f, ARRIVE_MS)
private val GENTLE = KelvinSpringInterpolator(0.9f, 260f, GENTLE_MS)

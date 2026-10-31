package studio72f.kelvin

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The 4dp grid. [space0] is only the photo grid gap. */
object KelvinSpace {
    val space0 = 2.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
    val space14 = 56.dp
    val space16 = 64.dp

    val gutter = space4
    val sectionGap = space6
    val rowMinHeight = space14
    val touchTarget = space12
}

/** Continuous-corner shapes for every role. */
object KelvinShapes {
    val xs = ContinuousShape(8.dp)
    val sm = ContinuousShape(12.dp)
    val md = ContinuousShape(16.dp)
    val lg = ContinuousShape(24.dp)
    val tile = ContinuousShape(22.dp)
    val xl = ContinuousShape(32.dp)
    val full = CapsuleShape
}

/** Onest (SIL Open Font License 1.1), bundled. */
val Onest = FontFamily(
    Font(R.font.onest_300, FontWeight.Light),
    Font(R.font.onest_400, FontWeight.Normal),
    Font(R.font.onest_500, FontWeight.Medium),
    Font(R.font.onest_600, FontWeight.SemiBold),
    Font(R.font.onest_700, FontWeight.Bold),
)

object KelvinType {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
        fontFamily = Onest, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight),
        letterSpacing = tracking.em, lineHeightStyle = trim,
    )

    val hero = style(64, 64, 300, -0.04)
    val display = style(34, 40, 700, -0.03)
    val numeral = style(34, 40, 600, -0.02)
    val headline = style(26, 32, 700, -0.02)
    val title = style(20, 26, 650, -0.01)
    val rowTitle = style(16, 22, 500)
    val label = style(14, 20, 600)
    val body = style(15, 22, 400)
    val summary = style(13, 18, 400)
    val caption = style(12, 16, 500, 0.01)
    val micro = style(11, 14, 600, 0.02)
}

/** Springs. All of them snap when motion is reduced. */
object KelvinMotion {
    /** Press and release, switch knob. */
    @Composable @ReadOnlyComposable
    fun <T> snappy(): FiniteAnimationSpec<T> = if (Kelvin.reduceMotion) snap() else spring(0.82f, 700f)

    /** Things that move: dock droplet, sheets, pages, tile to viewer. */
    @Composable @ReadOnlyComposable
    fun <T> spatial(): FiniteAnimationSpec<T> = if (Kelvin.reduceMotion) snap() else spring(0.76f, 420f)

    /** Things that change: size, colour, the collapsed bar. */
    @Composable @ReadOnlyComposable
    fun <T> gentle(): FiniteAnimationSpec<T> = if (Kelvin.reduceMotion) snap() else spring(0.9f, 260f)

    /** Liquid settle: the overshoot a droplet makes when it lands. */
    @Composable @ReadOnlyComposable
    fun <T> wobble(): FiniteAnimationSpec<T> = if (Kelvin.reduceMotion) snap() else spring(0.55f, Spring.StiffnessMediumLow)

    /**
     * The rubber band for anything pulled past its end: [over] (how far the finger is past it)
     * moves the thing c·x·d / (d + c·x). About half the finger's travel at first, less and less
     * after, never more than [dimension]. The same curve UIKit uses, with its touch coefficient.
     */
    fun rubberBand(over: Float, dimension: Float, coefficient: Float = 0.55f): Float {
        val x = kotlin.math.abs(over)
        return kotlin.math.sign(over) * coefficient * x * dimension / (dimension + coefficient * x)
    }

    /** A flick this fast (dp per second) decides a switch or dismisses a sheet on its own. */
    const val FLICK_VELOCITY = 600f

    const val PRESS_SCALE = 0.96f
    const val PRESS_OVERLAY = 0.08f
    const val DISABLED_ALPHA = 0.38f
    const val BACK_GESTURE_SCALE = 0.92f
}

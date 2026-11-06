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

/*
 * Two bundled faces, both SIL Open Font License 1.1:
 * - Onest, the display face: titles, the masthead, figures, tabs, names, actions and eyebrows.
 *   Everything that names or counts. The masthead is Onest Black.
 * - Montserrat, the text face: rows, body, summaries, captions and controls. Everything that
 *   explains. Its round, open geometric forms (the Gotham school) give running text a voice
 *   no grotesque has, and it stays easy to read at 12sp.
 * Both are variable on weight (Android 8 and later); before that the nearest bundled static
 * weight stands in.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun family(variable: Int, statics: Map<Int, Int>): FontFamily =
    if (android.os.Build.VERSION.SDK_INT >= 26) FontFamily(
        (100..900 step 100).map { w ->
            Font(variable, FontWeight(w), variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)))
        },
    ) else FontFamily(statics.map { (w, res) -> Font(res, FontWeight(w)) })

/** Onest, Kelvin's display face. */
val KelvinDisplay: FontFamily = family(
    R.font.onest_variable,
    mapOf(300 to R.font.onest_300, 400 to R.font.onest_400, 500 to R.font.onest_500, 600 to R.font.onest_600, 700 to R.font.onest_700),
)

/** Montserrat, Kelvin's text face. */
val KelvinText: FontFamily = family(
    R.font.montserrat_variable,
    mapOf(400 to R.font.montserrat_400, 500 to R.font.montserrat_500, 600 to R.font.montserrat_600, 700 to R.font.montserrat_700),
)

private val displayFamilies = java.util.concurrent.ConcurrentHashMap<Int, FontFamily>()

/**
 * Onest at exactly [weight] (100 to 900), for type that moves along the weight axis, such as the
 * masthead slimming as it folds. Families are cached per weight.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun displayAt(weight: Int): FontFamily = displayFamilies.getOrPut(weight.coerceIn(100, 900)) {
    val w = weight.coerceIn(100, 900)
    if (android.os.Build.VERSION.SDK_INT < 26) KelvinDisplay
    else FontFamily(
        Font(
            R.font.onest_variable, FontWeight(w),
            variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)),
        ),
    )
}

/**
 * Which faces Kelvin sets type in: its own ([Kelvin], Onest and Montserrat) or the phone's
 * ([System], the system sans for both roles, as the person set it in the phone's font settings).
 * The scale, weights and the figure grammar are the same either way; only the faces change.
 */
enum class KelvinFonts { Kelvin, System }

/** Kelvin's type scale, set in [fonts]. Read it as `Kelvin.type` inside a [KelvinTheme]. */
@androidx.compose.runtime.Immutable
class KelvinType private constructor(val fonts: KelvinFonts) {
    private val system = fonts == KelvinFonts.System
    private val displayFace = if (system) FontFamily.Default else KelvinDisplay
    private val textFace = if (system) FontFamily.Default else KelvinText

    /** The display face at exactly [weight], for type that moves along the weight axis (the masthead). */
    fun displayAt(weight: Int): FontFamily = if (system) FontFamily.Default else studio72f.kelvin.displayAt(weight)

    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0, figures: Boolean = false, display: Boolean = false) = TextStyle(
        fontFamily = if (display) displayFace else textFace, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight),
        letterSpacing = tracking.em, lineHeightStyle = trim,
        // Figures are proportional by default, so a 1 never stands apart; a figure that ticks in
        // place (a timer) asks for one width per digit itself (see `ticking`).
        fontFeatureSettings = if (figures) "tnum" else null,
    )

    // Hierarchy is carried by the face as much as the size: everything that names or counts is
    // Onest, everything that explains is Montserrat. Figures are light and large; titles calm,
    // except the masthead, which is Black.
    val hero = style(64, 64, 300, -0.045, display = true)
    val display = style(30, 36, 600, -0.025, display = true)
    /** The massive title centred in a one-handed opening: Black, slimming as it folds into the bar. */
    val masthead = style(52, 58, 900, -0.035, display = true).copy(fontFamily = displayAt(900))
    val numeral = style(40, 44, 300, -0.03, display = true)
    val headline = style(22, 28, 600, -0.015, display = true)
    val title = style(18, 24, 600, -0.01, display = true)
    /** Top-level switch between a page's views (Apps, Widgets), as OriginOS sets it. */
    val tab = style(28, 34, 700, -0.02, display = true)
    /** Small uppercase label above a figure or a coloured card. */
    val eyebrow = style(11, 14, 700, 0.1, display = true)
    /** A section's name over its group. */
    val section = style(14, 18, 600, -0.005, display = true)
    /** The name of a thing inside a component: a notice, a toast, an album, a sender. */
    val name = style(16, 22, 600, -0.01, display = true)
    /** A button's or a confirm slider's words: an action is named, so it is set in the display face. */
    val action = style(15, 20, 600, -0.005, display = true)
    /** A small figure in a row, a list or on a control (a count, a time, a level). */
    val figure = style(15, 20, 500, -0.01, display = true)
    /** [style] with one width per digit, for a figure that ticks in place, such as a running timer. */
    fun ticking(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")
    val rowTitle = style(16, 22, 500)
    val label = style(14, 20, 600)
    val body = style(15, 22, 400)
    val summary = style(13, 18, 400)
    val caption = style(12, 16, 500, 0.01)
    val micro = style(11, 14, 600, 0.02)

    companion object {
        /** Onest and Montserrat. */
        val Default = KelvinType(KelvinFonts.Kelvin)
        /** The phone's own sans. */
        val SystemFonts = KelvinType(KelvinFonts.System)
        fun of(fonts: KelvinFonts) = if (fonts == KelvinFonts.System) SystemFonts else Default
    }
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

package studio72f.kelvin

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
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

/**
 * How round Kelvin's corners are. [Soft] is Kelvin's own: deep corners, and every control a pill.
 * [Firm] is for work apps: a tighter scale (4 to 20dp) and buttons as rounded rectangles; chips and
 * round things (avatars, switches, the compose orb, progress) stay pills in either. Pass it to [KelvinTheme].
 */
enum class KelvinCorners { Soft, Firm }

/**
 * One scale of corner radii. Read it through [KelvinShapes] (or `Kelvin.shape`) inside a
 * [KelvinTheme]; the theme picks the scale from [KelvinCorners].
 */
@Immutable
class KelvinShapeScale private constructor(
    /** Badges, a checkbox, small icon tiles, skeletons. */
    val xsRadius: Dp,
    /** Chips' and segments' inner pieces, thumbnails. */
    val smRadius: Dp,
    /** Fields, notices, toasts, inset panels. */
    val mdRadius: Dp,
    /** Groups (the well), cards, choice cards. */
    val lgRadius: Dp,
    /** Media tiles. */
    val tileRadius: Dp,
    /** Sheets and dialogs. */
    val xlRadius: Dp,
    /** Buttons, the search field, the dock, a segmented control's track: null is a pill. */
    controlRadius: Dp?,
    /**
     * How far each corner eases into its straight edges ([ContinuousShape.smoothing]). The easing makes a corner
     * look larger than its radius, about half again at Soft's 0.6, so Firm keeps it low.
     */
    val smoothing: Float,
) {
    val xs = ContinuousShape(xsRadius, smoothing)
    val sm = ContinuousShape(smRadius, smoothing)
    val md = ContinuousShape(mdRadius, smoothing)
    val lg = ContinuousShape(lgRadius, smoothing)
    val tile = ContinuousShape(tileRadius, smoothing)
    val xl = ContinuousShape(xlRadius, smoothing)
    val full: Shape = CapsuleShape
    val control: Shape = controlRadius?.let { ContinuousShape(it, smoothing) } ?: CapsuleShape
    /** Filter chips, status pills and recipient chips: round in every scale. */
    val pill: Shape = CapsuleShape

    /** A sheet's shape: [xl] at the top, square at the foot. */
    val sheet = ContinuousShape(xlRadius, smoothing, flatBottom = true)

    companion object {
        val Soft = KelvinShapeScale(8.dp, 12.dp, 16.dp, 24.dp, 22.dp, 32.dp, controlRadius = null, smoothing = 0.6f)
        val Firm = KelvinShapeScale(4.dp, 8.dp, 12.dp, 16.dp, 14.dp, 20.dp, controlRadius = 12.dp, smoothing = 0.2f)
        fun of(corners: KelvinCorners) = when (corners) {
            KelvinCorners.Soft -> Soft
            KelvinCorners.Firm -> Firm
        }
    }
}

val LocalKelvinShapes = staticCompositionLocalOf { KelvinShapeScale.Soft }

/** Continuous-corner shapes for every role, in the scale the [KelvinTheme] picked. */
object KelvinShapes {
    val xs: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.xs
    val sm: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.sm
    val md: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.md
    val lg: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.lg
    val tile: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.tile
    val xl: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.xl
    val full: Shape get() = CapsuleShape
    val control: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.control
    val pill: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.pill
    val sheet: Shape @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current.sheet
    val scale: KelvinShapeScale @Composable @ReadOnlyComposable get() = LocalKelvinShapes.current
}

/**
 * The size of Kelvin's controls at the standard display size. Kelvin's own components are built
 * from these, and an app that draws a control of its own (a button with free-form content, a row
 * with two actions) reads them here and sets no size of its own, so every app moves together
 * when one of them changes. Each control sits inside a [touch] target however small it draws.
 */
object KelvinSizes {
    /** The least a person can be asked to hit. */
    val touch = 48.dp
    /** A button, and the larger one that closes a flow at the foot of a page. */
    val button = 44.dp
    val buttonLarge = 52.dp
    /** A filled button's side padding, and a text button's. */
    val buttonPadding = 20.dp
    val textButtonPadding = 12.dp
    /** The least a row in a group stands, and the padding above and below its words. */
    val row = 52.dp
    val rowPadding = 8.dp
    /** A text field, the search capsule, a segmented control and a filter chip. */
    val field = 52.dp
    val search = 42.dp
    val segmented = 40.dp
    val chip = 32.dp
    /** The mark of a radio or a checkbox, beside a 15sp label. */
    val mark = 20.dp
    /** A switch as drawn. */
    val switchWidth = 46.dp
    val switchHeight = 28.dp
    /** The colour tile beside a row's title, and the glyph in a row or on a button. */
    val tile = 32.dp
    val icon = 20.dp
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
/** Onest, Kelvin's display face. */
val KelvinDisplay: FontFamily = kelvinFamily(KelvinFace.Display)

/** Montserrat, Kelvin's text face. */
val KelvinText: FontFamily = kelvinFamily(KelvinFace.Text)

private val displayFamilies = HashMap<Int, FontFamily>()

/**
 * Onest at exactly [weight] (100 to 900), for type that moves along the weight axis, such as the
 * masthead slimming as it folds. Families are cached per weight.
 */
fun displayAt(weight: Int): FontFamily {
    val w = weight.coerceIn(100, 900)
    return displayFamilies.getOrPut(w) { kelvinDisplayAt(w) }
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

    // The sizes are set for the two faces, not for a system sans: Onest and Montserrat are wide, with
    // a tall x-height, so they read a size larger than Roboto at the same sp.
    // The scale has ten sizes and no neighbours a point apart: 11 (marks only: an eyebrow, a badge),
    // then 12, 14 and 16 for everything a person reads, then 20, 24, 28, 34, 44 and 56 for titles
    // and figures. Reading text and a row's title are both 14, what explains them is 12, and a
    // title is 16; nothing a person reads in a sentence goes under 12. Styles that share a size are
    // told apart by face, weight and ink, never by a point of size.
    // Hierarchy is carried by the face as much as the size: everything that names or counts is
    // Onest, everything that explains is Montserrat. Figures are light and large; titles calm,
    // except the masthead, which is Black.
    val hero = style(56, 58, 300, -0.045, display = true)
    val display = style(28, 34, 600, -0.025, display = true)
    /** The massive title centred in a one-handed opening: Black, slimming as it folds into the bar. */
    val masthead = style(44, 50, 900, -0.035, display = true).copy(fontFamily = displayAt(900))
    val numeral = style(34, 40, 300, -0.03, display = true)
    val headline = style(20, 26, 600, -0.015, display = true)
    val title = style(16, 22, 600, -0.01, display = true)
    /** Top-level switch between a page's views (Apps, Widgets), as OriginOS sets it. */
    val tab = style(24, 30, 700, -0.02, display = true)
    /** Small uppercase label above a figure or a coloured card. */
    val eyebrow = style(11, 14, 700, 0.1, display = true)
    /** A section's name over its group. */
    val section = style(12, 16, 600, 0.0, display = true)
    /** The name of a thing inside a component: a notice, a toast, an album, a sender. */
    val name = style(14, 20, 600, -0.01, display = true)
    /** A button's or a confirm slider's words: an action is named, so it is set in the display face. */
    val action = style(14, 20, 600, -0.005, display = true)
    /** A small figure in a row, a list or on a control (a count, a time, a level). */
    val figure = style(14, 20, 500, -0.01, display = true)
    /** [style] with one width per digit, for a figure that ticks in place, such as a running timer. */
    fun ticking(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")
    val rowTitle = style(14, 20, 500)
    val label = style(12, 16, 600)
    val body = style(14, 20, 400)
    val summary = style(12, 18, 400)
    val caption = style(12, 16, 500, 0.01)
    val micro = style(11, 14, 600, 0.02)

    /**
     * Material's fifteen type roles answered in Kelvin's scale, for screens still written against
     * `MaterialTheme.typography`. An app's bridge builds its `Typography` from these and sets no
     * size of its own: Material's own sizes are drawn for Roboto and run a size large in Onest and
     * Montserrat. Names and counts (display, headline, title, label) are Onest; body is Montserrat.
     */
    val material: KelvinMaterialType by lazy {
        KelvinMaterialType(
            displayLarge = hero,
            displayMedium = style(44, 50, 600, -0.035, display = true),
            displaySmall = style(34, 40, 600, -0.03, display = true),
            headlineLarge = display,
            headlineMedium = style(24, 30, 600, -0.02, display = true),
            headlineSmall = headline,
            titleLarge = title,
            titleMedium = name,
            titleSmall = section,
            bodyLarge = body,
            bodyMedium = body,
            bodySmall = summary,
            labelLarge = action,
            labelMedium = style(12, 16, 600, display = true),
            labelSmall = style(11, 14, 600, 0.02, display = true),
        )
    }

    companion object {
        /** Onest and Montserrat. */
        val Default = KelvinType(KelvinFonts.Kelvin)
        /** The phone's own sans. */
        val SystemFonts = KelvinType(KelvinFonts.System)
        fun of(fonts: KelvinFonts) = if (fonts == KelvinFonts.System) SystemFonts else Default
    }
}

/** Kelvin's answer to each of Material's type roles ([KelvinType.material]). */
@androidx.compose.runtime.Immutable
class KelvinMaterialType(
    val displayLarge: TextStyle, val displayMedium: TextStyle, val displaySmall: TextStyle,
    val headlineLarge: TextStyle, val headlineMedium: TextStyle, val headlineSmall: TextStyle,
    val titleLarge: TextStyle, val titleMedium: TextStyle, val titleSmall: TextStyle,
    val bodyLarge: TextStyle, val bodyMedium: TextStyle, val bodySmall: TextStyle,
    val labelLarge: TextStyle, val labelMedium: TextStyle, val labelSmall: TextStyle,
)

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

package studio72f.kelvin

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import kotlin.time.TimeSource

// What Kelvin asks of the platform it runs on. Each has an Android answer (androidMain) and a desktop one
// (desktopMain); everything else in the library is shared.

/**
 * The words Kelvin itself shows or speaks (a close button's name, the error panel's labels). Android reads them from
 * its string resources, so an app's translations apply; desktop uses the English here until it has translations.
 */
enum class KelvinString(internal val english: String) {
    Back("Back"),
    Selected("Selected"),
    Chosen("Chosen"),
    SelectMessage("Select message"),
    CurrentAccount("Current account"),
    CurrentPlan("Current plan"),
    ImagesHidden("Images are hidden"),
    ShowImages("Show images"),
    ErrorViewMore("View more"),
    ErrorCode("Error code"),
    ErrorWhy("Why it happened"),
    ErrorDetail("Technical detail"),
    ErrorLogPreview("What will be sent"),
    ErrorEmailLogs("Email the logs"),
    TextureLiquid("Liquid glass"),
    TextureLiquidSummary("Bends the page at its edges and catches the light"),
    TextureFrost("Frost"),
    TextureFrostSummary("Calm ground glass, easiest to read through"),
    PlayerPlay("Play"),
    PlayerPause("Pause"),
    PlayerBack("Back 15 seconds"),
    PlayerForward("Forward 30 seconds"),
    PlayerPosition("Position"),
    PlayerQueue("Queue"),
    PageOf("Page %1\$d of %2\$d"),
}

@Composable
internal expect fun kelvinString(string: KelvinString): String

/** Kelvin's two faces: Onest for display, Montserrat for text. */
internal enum class KelvinFace { Display, Text }

/** The face as a family that covers every weight. */
internal expect fun kelvinFamily(face: KelvinFace): FontFamily

/** Onest at exactly [weight], for type that moves along the weight axis. */
internal expect fun kelvinDisplayAt(weight: Int): FontFamily

/** What the system says about contrast, power saving and motion, with the app's own reduce-motion choice. */
@Composable
internal expect fun rememberKelvinAccessibility(appReduceMotion: Boolean): KelvinAccessibility

/**
 * A soft drop shadow for a capsule filling the draw area, drawn rather than elevated: system shadows are capped too
 * light to hold a white knob against a white group.
 */
internal expect fun DrawScope.drawCapsuleShadow(blur: Float, offsetY: Float, alpha: Float)

/** Reads a doodle's path list by its file name (see [KelvinDoodle.file]), one line per path. */
@Composable
internal expect fun rememberDoodleReader(): (String) -> List<String>

/** The kinds of touch feedback Kelvin asks for; a platform without a motor ignores them. */
internal enum class HapticKind { Tick, ToggleOn, ToggleOff, Confirm }

@Composable
internal expect fun rememberHapticPerformer(): (HapticKind) -> Unit

/** A soft shadow of [path], [blur] wide and dropped by [offsetY]. Built once, drawn each frame. */
internal expect fun pathShadowPainter(path: Path, blur: Float, offsetY: Float, color: Color): DrawScope.() -> Unit

/** Draws a lit pebble with the shader in Pebble.kt. A platform that cannot run it has none, and pebbles are plain. */
internal interface PebblePainter {
    @Suppress("LongParameterList")
    fun DrawScope.draw(
        outline: Outline,
        radii: FloatArray,
        color: Color,
        rimWidth: Float,
        strength: Float,
        rim: Float,
        heat: Color,
        heatAmount: Float,
    )
}

internal expect fun pebblePainter(agsl: String): PebblePainter?

/** Runs [onBack] for the system's back gesture or button while [enabled]. A desktop window has neither. */
@Composable
internal expect fun KelvinBackHandler(enabled: Boolean = true, onBack: () -> Unit)

private val clockStart = TimeSource.Monotonic.markNow()

/** Milliseconds on a clock that only runs forward, for timing how fast something changes. */
internal fun monotonicMillis(): Long = clockStart.elapsedNow().inWholeMilliseconds

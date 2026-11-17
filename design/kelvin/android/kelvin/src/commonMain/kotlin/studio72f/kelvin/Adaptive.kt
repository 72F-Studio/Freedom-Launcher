package studio72f.kelvin

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Phones, foldables, tablets and phones on their side. A 72F app is one app at every size: the same places, the
// same pages, the same glass. The floating KelvinDock stays at every width, centred at the foot of the window, as
// the phone-first apps on vivo, Oppo and iPad keep their bar; what changes with the window is how far a page lets
// its lines and grids run. The window decides the chrome; a pane decides its own content, from its own width
// (BoxWithConstraints), because a list beside a detail is narrow even on a tablet.

/** How wide a window is, at the breakpoints Android's window size classes use (600dp and 840dp). */
enum class KelvinWidth { Compact, Medium, Expanded }

/** How tall a window is: [Compact] under 480dp is a phone on its side, where a tall opening would fill the screen. */
enum class KelvinHeight { Compact, Medium, Expanded }

/**
 * The window a Kelvin app is drawn in, in dp. [KelvinTheme] provides it as [LocalKelvinWindow] from the window's
 * real size, so it follows a fold opening, a rotation and split screen without the app doing anything.
 */
@Immutable
data class KelvinWindow(val width: Dp, val height: Dp) {
    val widthClass: KelvinWidth
        get() = when {
            width < 600.dp -> KelvinWidth.Compact
            width < 840.dp -> KelvinWidth.Medium
            else -> KelvinWidth.Expanded
        }
    val heightClass: KelvinHeight
        get() = when {
            height < 480.dp -> KelvinHeight.Compact
            height < 900.dp -> KelvinHeight.Medium
            else -> KelvinHeight.Expanded
        }

    /** True from 600dp: an open foldable, a tablet, a phone on its side. */
    val wide: Boolean get() = widthClass != KelvinWidth.Compact

    /** True under 480dp tall: a phone on its side. Openings shrink and nothing tall is pinned. */
    val short: Boolean get() = heightClass == KelvinHeight.Compact

    /**
     * How many columns a grid takes when the person chose [phoneColumns] for a phone about [phoneWidth] wide: the
     * same tile size, so more of them in a wider [available] width, never fewer than they chose.
     */
    fun columnsFor(phoneColumns: Int, available: Dp = width, phoneWidth: Dp = 400.dp): Int =
        maxOf(phoneColumns, (phoneColumns * available.value / phoneWidth.value).roundToInt())

    companion object {
        /** A phone held upright, for previews and tests that draw without a window. */
        val Phone = KelvinWindow(400.dp, 860.dp)
    }
}

/** The window around, from [KelvinTheme]. */
val LocalKelvinWindow = staticCompositionLocalOf { KelvinWindow.Phone }

/** The window's size as Compose reports it; [KelvinTheme] calls it, so apps read [LocalKelvinWindow] instead. */
@Composable
fun rememberKelvinWindow(): KelvinWindow {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return remember(size, density) {
        if (size.width == 0 || size.height == 0) KelvinWindow.Phone
        else with(density) { KelvinWindow(size.width.toDp(), size.height.toDp()) }
    }
}

/** The widest a column of reading (settings, a form, an article, a first-run page) runs before it is centred. */
val KelvinReadingWidth = 640.dp

/**
 * A column of reading held to [max] and centred, so lines do not run across a tablet: settings, forms, a first-run
 * page, an article. On a phone it is the whole width. For a list that scrolls, use [KelvinReadingBox] instead, so
 * the margins still scroll it.
 */
fun Modifier.kelvinReadingWidth(max: Dp = KelvinReadingWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)

/**
 * Hands [content] the side padding that holds what it lays out to [max] wide and centred: at least [min], more as
 * the space grows. Give it to a lazy list's `contentPadding`, so the whole width still scrolls.
 */
@Composable
fun KelvinReadingBox(
    modifier: Modifier = Modifier,
    max: Dp = KelvinReadingWidth,
    min: Dp = 16.dp,
    content: @Composable (gutter: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        content(kelvinReadingGutter(maxWidth, max, min))
    }
}

/** The side padding that holds [available] to [max] wide, centred, and never under [min]. */
fun kelvinReadingGutter(available: Dp, max: Dp = KelvinReadingWidth, min: Dp = 16.dp): Dp =
    maxOf(min, (available - max) / 2)

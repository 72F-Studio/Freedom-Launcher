package studio72f.kelvin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Kelvin on a page of its own colour: a reader's sepia or night page, a photo viewer's black, a
 * themed card. Everything inside is inked in [ink] over [ground], so Kelvin's controls sit on that
 * page as they sit on the canvas: wells and tracks are the ink washed thinly over the ground,
 * secondary text and icons the ink at lower strength (held to 4.5:1 and 3:1 on the ground), and the
 * accent stays the app's own, with its soft fill and soft ink recomputed for the ground. Whether
 * the page reads as light or dark is taken from the ground, so glass and shadows follow it.
 */
@Composable
fun KelvinOnPage(ground: Color, ink: Color, content: @Composable () -> Unit) {
    val base = Kelvin.colors
    val dark = ground.luminance() < 0.4f
    val colors = remember(base, ground, ink) { base.onPage(ground, ink, dark) }
    CompositionLocalProvider(LocalKelvinColors provides colors, LocalKelvinDark provides dark, content = content)
}

/** The palette [KelvinOnPage] provides: [KelvinColors] re-inked for [ink] over [ground]. */
fun KelvinColors.onPage(ground: Color, ink: Color, dark: Boolean = ground.luminance() < 0.4f): KelvinColors {
    fun wash(a: Float) = ink.copy(alpha = a).compositeOver(ground)
    val soft = accent.copy(alpha = if (dark) 0.22f else 0.14f).compositeOver(ground)
    return copy(
        canvas = ground,
        group = wash(0.06f),
        // Raised sits above the page: lighter than the ground in light, a brighter wash in dark.
        raised = if (dark) wash(0.12f) else lerp(ground, Color.White, 0.7f),
        sunken = wash(0.10f),
        hairline = wash(0.12f),
        ink = ink,
        ink2 = wash(0.68f).legibleOn(ground),
        ink3 = wash(0.52f).legibleOn(ground, 3f),
        accentSoft = soft,
        onAccentSoft = accent.legibleOn(soft).legibleOn(ground),
        media = ground,
    )
}

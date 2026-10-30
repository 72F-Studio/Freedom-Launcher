package studio72f.kelvin

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** What the person picked in Settings > Appearance > Accent. */
@Immutable
sealed interface Accent {
    data object Ember : Accent
    data object Graphite : Accent
    data class Preset(val preset: AccentPreset) : Accent
}

/** Things the system asks of us that change how Kelvin draws. Read once per theme. */
@Immutable
data class KelvinAccessibility(
    /** Android's "High contrast text": glass goes solid. */
    val highContrastText: Boolean = false,
    /** Battery saver: glass is capped at Frosted. */
    val powerSave: Boolean = false,
    /** Animations off or the app's own "Reduce motion": springs snap, shapes never deform. */
    val reduceMotion: Boolean = false,
)

private fun KelvinColors.withAccent(accent: Accent, dark: Boolean): KelvinColors = when (accent) {
    Accent.Ember -> this
    Accent.Graphite -> copy(
        accent = ink, onAccent = canvas, accentSoft = sunken, onAccentSoft = ink,
        horizon = ink.copy(alpha = 0.05f),
        // Neutral light, a little under daylight.
        glassHeat = Color(0xFFFFF1E2),
    )
    is Accent.Preset -> (if (dark) accent.preset.dark else accent.preset.light).let {
        copy(
            accent = it.accent, onAccent = it.onAccent, accentSoft = it.soft, onAccentSoft = it.onSoft,
            horizon = it.accent.copy(alpha = horizon.alpha),
            // Glass is lit at the accent's colour temperature: its bright form, paled toward white.
            glassHeat = lerp(accent.preset.dark.accent, Color.White, 0.3f),
        )
    }
}

val LocalKelvinColors = staticCompositionLocalOf { KelvinLight }
val LocalKelvinDark = staticCompositionLocalOf { false }
val LocalKelvinAccessibility = staticCompositionLocalOf { KelvinAccessibility() }
val LocalGlass = staticCompositionLocalOf { GlassLevel.Frosted.resolve(KelvinAccessibility()) }

/** Entry point for reading tokens inside a [KelvinTheme]. */
object Kelvin {
    val colors: KelvinColors @Composable @ReadOnlyComposable get() = LocalKelvinColors.current
    val dark: Boolean @Composable @ReadOnlyComposable get() = LocalKelvinDark.current
    val glass: GlassParams @Composable @ReadOnlyComposable get() = LocalGlass.current
    val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalKelvinAccessibility.current.reduceMotion
    val space = KelvinSpace
    val shape = KelvinShapes
    val type = KelvinType
    val motion = KelvinMotion
}

/**
 * Kelvin theme. [texture] is the glass material the person picked and [glass] its intensity:
 * for [GlassTexture.Liquid] the Glass level (0 near-opaque … 1 Clear), for [GlassTexture.Frost]
 * the frost (0 Opaque … 1 Sheer). Keep one intensity per texture so switching back restores it.
 * The theme lowers it
 * when the system asks for high-contrast text or battery saving. [reduceMotion] is the app's own
 * setting; the system animator scale is honoured on top of it. [adjust] lets an app make a
 * documented variation of the palette (for example a darker group colour for AMOLED screens).
 */
@Composable
fun KelvinTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Ember,
    glass: Float = GlassLevel.Frosted,
    texture: GlassTexture = GlassTexture.Liquid,
    reduceMotion: Boolean = false,
    adjust: (KelvinColors) -> KelvinColors = { it },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val a11y = remember(context, reduceMotion) { context.readAccessibility(reduceMotion) }
    val colors = remember(dark, accent, adjust) { adjust((if (dark) KelvinDark else KelvinLight).withAccent(accent, dark)) }
    val glassParams = remember(glass, texture, a11y) { glass.resolve(a11y, texture) }
    val selection = remember(colors) { TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.3f)) }
    CompositionLocalProvider(
        LocalKelvinColors provides colors,
        LocalKelvinDark provides dark,
        LocalKelvinAccessibility provides a11y,
        LocalGlass provides glassParams,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}

private fun Context.readAccessibility(appReduceMotion: Boolean): KelvinAccessibility {
    val highContrast = runCatching { Settings.Secure.getInt(contentResolver, "high_text_contrast_enabled", 0) == 1 }.getOrDefault(false)
    val power = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    val animationsOff = !ValueAnimator.areAnimatorsEnabled()
    return KelvinAccessibility(highContrast, power, appReduceMotion || animationsOff)
}

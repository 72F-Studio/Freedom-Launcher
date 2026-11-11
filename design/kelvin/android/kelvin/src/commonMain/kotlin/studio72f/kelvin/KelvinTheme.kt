package studio72f.kelvin

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

/** What the person picked in Settings > Appearance > Accent. */
@Immutable
sealed interface Accent {
    data object Ember : Accent
    data object Graphite : Accent
    data class Preset(val preset: AccentPreset) : Accent

    /** An app's own accent outside the dial, such as [KelvinAzure]. */
    data class Custom(val light: AccentFamily, val dark: AccentFamily) : Accent
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
    is Accent.Preset -> withFamily(if (dark) accent.preset.dark else accent.preset.light, accent.preset.dark)
    is Accent.Custom -> withFamily(if (dark) accent.dark else accent.light, accent.dark)
}

private fun KelvinColors.withFamily(family: AccentFamily, darkFamily: AccentFamily): KelvinColors = copy(
    accent = family.accent, onAccent = family.onAccent, accentSoft = family.soft, onAccentSoft = family.onSoft,
    horizon = family.accent.copy(alpha = horizon.alpha),
    // Glass is lit at the accent's colour temperature: its bright form, paled toward white.
    glassHeat = lerp(darkFamily.accent, Color.White, 0.3f),
)

val LocalKelvinColors = staticCompositionLocalOf { KelvinLight }
val LocalKelvinDark = staticCompositionLocalOf { false }
val LocalKelvinAccessibility = staticCompositionLocalOf { KelvinAccessibility() }
val LocalKelvinType = staticCompositionLocalOf { KelvinType.Default }
val LocalGlass = staticCompositionLocalOf { FrostLevel.Default.resolve(KelvinAccessibility(), GlassTexture.Frost) }

/** Entry point for reading tokens inside a [KelvinTheme]. */
object Kelvin {
    val colors: KelvinColors @Composable @ReadOnlyComposable get() = LocalKelvinColors.current
    val dark: Boolean @Composable @ReadOnlyComposable get() = LocalKelvinDark.current
    val glass: GlassParams @Composable @ReadOnlyComposable get() = LocalGlass.current
    val finish: KelvinFinish @Composable @ReadOnlyComposable get() = LocalKelvinFinish.current
    val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalKelvinAccessibility.current.reduceMotion
    val space = KelvinSpace
    val shape = KelvinShapes
    val type: KelvinType @Composable @ReadOnlyComposable get() = LocalKelvinType.current
    val motion = KelvinMotion
}

/**
 * Kelvin theme. [texture] is the glass material the person picked and [glass] its intensity.
 * Frost is the default: deep frost for panes and Kelvin's glazed, heat-lit pebbles for controls.
 * Liquid glass (iOS 26-style refraction, rendered by Vitro) is an option a person turns on.
 * for [GlassTexture.Liquid] the Glass level (0 near-opaque … 1 Clear), for [GlassTexture.Frost]
 * the frost (0 Opaque … 1 Sheer). Keep one intensity per texture so switching back restores it.
 * [refraction] is how hard Liquid glass bends what is behind it (0 Subtle … 1 Strong), apart
 * from how opaque it is.
 * The theme lowers it
 * when the system asks for high-contrast text or battery saving. [reduceMotion] is the app's own
 * setting; the system animator scale is honoured on top of it. [adjust] lets an app make a
 * documented variation of the palette (for example a darker group colour for AMOLED screens).
 * [fonts] picks Kelvin's own faces or the phone's (offer it in Appearance as "System font").
 * [icons] draws glyphs filled (the default) or as Tabler's 1.5 outline.
 * [doodles] picks the set of empty-state scenes: casual, semi-professional or corporate.
 * [finish] is how controls are finished: Kelvin's pebbles, or [KelvinFinish.Flat] for work apps.
 * [corners] is how round everything is: Kelvin's [KelvinCorners.Soft], or [KelvinCorners.Firm] for work apps.
 */
@Composable
fun KelvinTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.Ember,
    glass: Float = FrostLevel.Default,
    texture: GlassTexture = GlassTexture.Frost,
    refraction: Float = RefractionLevel.Default,
    reduceMotion: Boolean = false,
    fonts: KelvinFonts = KelvinFonts.Kelvin,
    icons: IconStyle = IconStyle.Outline,
    doodles: DoodleSet = DoodleSet.Casual,
    finish: KelvinFinish = KelvinFinish.Pebble,
    corners: KelvinCorners = KelvinCorners.Soft,
    adjust: (KelvinColors) -> KelvinColors = { it },
    content: @Composable () -> Unit,
) {
    val a11y = rememberKelvinAccessibility(reduceMotion)
    val colors = remember(dark, accent, adjust) {
        adjust((if (dark) KelvinDark else KelvinLight).withAccent(accent, dark)).let {
            // `on-accent-soft` is only ever text and icons: keep it at 4.5:1 on its soft fill and
            // on the group grey, whatever the accent.
            it.copy(onAccentSoft = it.onAccentSoft.legibleOn(it.accentSoft).legibleOn(it.group))
        }
    }
    val glassParams = remember(glass, texture, refraction, a11y, finish) {
        // Flat controls hold their shape: nothing stretches, swells or wobbles, as with reduced motion.
        glass.resolve(a11y, texture, refraction).let { if (finish == KelvinFinish.Flat) it.copy(deform = 0f) else it }
    }
    val selection = remember(colors) { TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.3f)) }
    CompositionLocalProvider(
        LocalKelvinColors provides colors,
        LocalKelvinDark provides dark,
        LocalKelvinAccessibility provides a11y,
        LocalGlass provides glassParams,
        LocalKelvinType provides KelvinType.of(fonts),
        LocalIconStyle provides icons,
        LocalDoodleSet provides doodles,
        LocalKelvinFinish provides finish,
        LocalKelvinShapes provides KelvinShapeScale.of(corners),
        LocalTextSelectionColors provides selection,
        content = content,
    )
}

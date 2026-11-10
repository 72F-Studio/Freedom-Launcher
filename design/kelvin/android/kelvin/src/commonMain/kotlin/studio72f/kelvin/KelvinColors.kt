// Generated from tokens.json by tools/kt-colors.js. Edit the tokens, not this file.
package studio72f.kelvin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Every Kelvin colour role for one theme. See tokens.json for where each is used. */
@Immutable
data class KelvinColors(
    val canvas: Color,
    val group: Color,
    val raised: Color,
    val sunken: Color,
    val hairline: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val horizon: Color,
    val glassTint: Color,
    val glassLit: Color,
    val lift: Color,
    val glassHeat: Color,
    val glassEdge: Color,
    val glassInk2: Color,
    val scrim: Color,
    val media: Color,
    val positive: Color,
    val positiveSoft: Color,
    val caution: Color,
    val cautionSoft: Color,
    val critical: Color,
    val criticalSoft: Color,
    val info: Color,
    val infoSoft: Color,
)

val KelvinLight = KelvinColors(
    canvas = Color(0xFFFFFFFF),
    group = Color(0xFFF3F4F6),
    raised = Color(0xFFFFFFFF),
    sunken = Color(0xFFE8E9EC),
    hairline = Color(0xFFE4E5E8),
    ink = Color(0xFF000000),
    ink2 = Color(0xFF68696B),
    ink3 = Color(0xFF929496),
    accent = Color(0xFFFD5F12),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFFECE4),
    onAccentSoft = Color(0xFFEC5900),
    horizon = Color(0x1FFF6116),
    glassTint = Color(0xFFFFFFFF),
    glassLit = Color(0xFFFFFFFF),
    lift = Color(0xFFFFFFFF),
    glassHeat = Color(0xFFFF9E61),
    glassEdge = Color(0x1A000000),
    glassInk2 = Color(0xFF4B4C4F),
    scrim = Color(0x6B000000),
    media = Color(0xFF000000),
    positive = Color(0xFF00C04D),
    positiveSoft = Color(0xFFE1F7E3),
    caution = Color(0xFFD68604),
    cautionSoft = Color(0xFFFFEDDB),
    critical = Color(0xFFE0141A),
    criticalSoft = Color(0xFFFDE8E8),
    info = Color(0xFF0BA8F5),
    infoSoft = Color(0xFFE3F3FE),
)

val KelvinDark = KelvinColors(
    canvas = Color(0xFF000000),
    group = Color(0xFF1B1C1D),
    raised = Color(0xFF2B2B2D),
    sunken = Color(0xFF232425),
    hairline = Color(0xFF2F3032),
    ink = Color(0xFFE6E7E9),
    ink2 = Color(0xFF97989B),
    ink3 = Color(0xFF68696B),
    accent = Color(0xFFE15606),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF3C2F2A),
    onAccentSoft = Color(0xFFFC7841),
    horizon = Color(0x26E15606),
    glassTint = Color(0xFF1C1D1F),
    glassLit = Color(0xFF4A4B50),
    lift = Color(0xFF46474B),
    glassHeat = Color(0xFFE8783A),
    glassEdge = Color(0x80000000),
    glassInk2 = Color(0xFFB9BABD),
    scrim = Color(0xA3000000),
    media = Color(0xFF000000),
    positive = Color(0xFF299D50),
    positiveSoft = Color(0xFF2B3530),
    caution = Color(0xFFCB7B12),
    cautionSoft = Color(0xFF39322A),
    critical = Color(0xFFE61D26),
    criticalSoft = Color(0xFF3F1D1F),
    info = Color(0xFF2B92DB),
    infoSoft = Color(0xFF2B343C),
)

/** One accent dial preset for one theme: fill, content on it, soft fill, content on that. */
@Immutable
data class AccentFamily(val accent: Color, val onAccent: Color, val soft: Color, val onSoft: Color)

/** Accent dial presets besides Ember (the default) and Graphite (derived from ink). */
enum class AccentPreset(val light: AccentFamily, val dark: AccentFamily) {
    Glacier(
        light = AccentFamily(Color(0xFF4D93F9), Color(0xFFFFFFFF), Color(0xFFE8F1FF), Color(0xFF3487FE)),
        dark = AccentFamily(Color(0xFF427FE6), Color(0xFFFFFFFF), Color(0xFF2E333E), Color(0xFF669DF9)),
    ),
    Fjord(
        light = AccentFamily(Color(0xFF009FD0), Color(0xFFFFFFFF), Color(0xFFDEF4FF), Color(0xFF0894BF)),
        dark = AccentFamily(Color(0xFF018CBD), Color(0xFFFFFFFF), Color(0xFF28343B), Color(0xFF43A7D6)),
    ),
    Fern(
        light = AccentFamily(Color(0xFF00AA45), Color(0xFFFFFFFF), Color(0xFFE0F7E4), Color(0xFF069F49)),
        dark = AccentFamily(Color(0xFF149446), Color(0xFFFFFFFF), Color(0xFF2B3530), Color(0xFF4FAE69)),
    ),
    Dusk(
        light = AccentFamily(Color(0xFFAB79F8), Color(0xFFFFFFFF), Color(0xFFF2EDFF), Color(0xFFA766FE)),
        dark = AccentFamily(Color(0xFF9A67E4), Color(0xFFFFFFFF), Color(0xFF35313E), Color(0xFFB388F8)),
    ),
    Rose(
        light = AccentFamily(Color(0xFFF75B8B), Color(0xFFFFFFFF), Color(0xFFFFEAEE), Color(0xFFFF307E)),
        dark = AccentFamily(Color(0xFFDE4D7A), Color(0xFFFFFFFF), Color(0xFF3B2F35), Color(0xFFF57497)),
    ),
    Apricot(
        light = AccentFamily(Color(0xFFE67420), Color(0xFFFFFFFF), Color(0xFFFFEDE1), Color(0xFFE0600F)),
        dark = AccentFamily(Color(0xFFE17730), Color(0xFFFFFFFF), Color(0xFF3D2E25), Color(0xFFFF9F65)),
    ),
    Ruby(
        light = AccentFamily(Color(0xFFF0444C), Color(0xFFFFFFFF), Color(0xFFFEE8E9), Color(0xFFD6262F)),
        dark = AccentFamily(Color(0xFFDA3A45), Color(0xFFFFFFFF), Color(0xFF3D2B2C), Color(0xFFF06567)),
    ),
    Sage(
        light = AccentFamily(Color(0xFF5A9A72), Color(0xFFFFFFFF), Color(0xFFE7F2EA), Color(0xFF3F7F59)),
        dark = AccentFamily(Color(0xFF5F9B76), Color(0xFFFFFFFF), Color(0xFF2C3530), Color(0xFF80B594)),
    ),
}

/**
 * Azure: the deeper, more saturated blue of the 72F Mail icon. White labels sit on it at 3.5:1, bold. Not a dial
 * preset, so avatar colours (which cycle through [AccentPreset]) don't shift; apps use it as [Accent.Custom].
 */
object KelvinAzure {
    val light = AccentFamily(Color(0xFF3487FE), Color(0xFFFFFFFF), Color(0xFFE8F1FF), Color(0xFF0A67DB))
    val dark = AccentFamily(Color(0xFF3487FE), Color(0xFFFFFFFF), Color(0xFF2E333E), Color(0xFF669DF9))
}

/** Settings icon tiles: same in both themes, white glyph on top (3:1). */
object KelvinTiles {
    val Blue = Color(0xFF4993FF)
    val Orange = Color(0xFFE47602)
    val Green = Color(0xFF1BA951)
    val Red = Color(0xFFFE5C59)
    val Violet = Color(0xFFA47DFF)
    val Teal = Color(0xFF0AA4AB)
    val Grey = Color(0xFF91949A)
}

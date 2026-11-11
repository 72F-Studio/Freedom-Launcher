package studio72f.kelvin

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How Kelvin finishes its controls. An app picks one for its whole surface; it is a brand
 * decision, not a setting a person changes.
 *
 * [Pebble] is Kelvin's own: frosted stones lit in the bevel, on a whisper of shadow, that swell
 * and lean under the finger and keep the touch's heat.
 *
 * [Flat] is for work apps (72F Mail): the same shapes, colours and type with nothing raised and
 * nothing soft. Fills are solid, a pale fill is a grey step off the page instead of an outline, a press
 * dims in place, and nothing swells, leans or stretches. It needs no shader, so it draws the same
 * on every Android version and on desktop.
 */
enum class KelvinFinish { Pebble, Flat }

val LocalKelvinFinish = staticCompositionLocalOf { KelvinFinish.Pebble }

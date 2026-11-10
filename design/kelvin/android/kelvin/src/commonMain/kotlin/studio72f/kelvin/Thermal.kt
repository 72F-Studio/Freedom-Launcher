package studio72f.kelvin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/*
 * Thermal: Kelvin takes its name literally. Everything a person touches heats up and cools down
 * along a real black-body curve and the aurora follows the colour temperature of the hour.
 * Interaction heats; rest cools. Nothing stays hot, so a still screen is calm and costs nothing.
 */

/**
 * The colour of a black body at [kelvin] (1,000 to 40,000 K), after Tanner Helland's fit of
 * Mitchell Charity's blackbody table: candlelight near 1,900 K, a tungsten bulb near 2,700 K,
 * white near 6,500 K and blue sky above 10,000 K.
 */
fun blackbody(kelvin: Float): Color {
    val t = kelvin.coerceIn(1000f, 40000f) / 100f
    val r = if (t <= 66f) 255.0 else 329.698727446 * (t - 60.0).pow(-0.1332047592)
    val g = if (t <= 66f) 99.4708025861 * ln(t.toDouble()) - 161.1195681661 else 288.1221695283 * (t - 60.0).pow(-0.0755148492)
    val b = when {
        t >= 66f -> 255.0
        t <= 19f -> 0.0
        else -> 138.5177312231 * ln(t - 10.0) - 305.0447927307
    }
    fun c(v: Double) = (v / 255.0).toFloat().coerceIn(0f, 1f)
    return Color(c(r), c(g), c(b))
}

/** Whether an accent sits on the warm side of the scale (Ember, Apricot, Ruby) or the cool side (Glacier, Fjord). */
internal fun Color.isWarm(): Boolean = red >= blue

/**
 * The colour of matter at [heat] (0 cold to 1 white-hot), anchored on the app's accent so every
 * 72F app glows in its own colour: an ember of the accent deepened, then the vivid accent, then
 * `glass-heat`, then white-hot (warm white for warm accents, blue-white for cool ones).
 */
fun thermalColor(heat: Float, accent: Color, glassHeat: Color): Color {
    val h = heat.coerceIn(0f, 1f)
    val whiteHot = if (accent.isWarm()) blackbody(5200f) else blackbody(11000f)
    val vivid = accent.vivid(lift = 0f)
    val ember = lerp(vivid, Color.Black, 0.35f)
    return when {
        h < 0.35f -> lerp(ember, vivid, h / 0.35f)
        h < 0.7f -> lerp(vivid, glassHeat, (h - 0.35f) / 0.35f)
        else -> lerp(glassHeat, whiteHot, (h - 0.7f) / 0.3f)
    }
}

/**
 * The colour temperature of the light at [hour] (0 to 24, local time): candlelight through the
 * night, a warm sunrise, blue sky at midday, a golden evening. It is the temperature of the
 * room the screen is in, the way Night Light thinks of it, exaggerated toward the sky by day.
 */
fun circadianKelvin(hour: Float): Float {
    val stops = floatArrayOf(0f, 1900f, 5f, 1900f, 6.5f, 2700f, 8.5f, 5000f, 12.5f, 12000f, 16f, 8000f, 18.5f, 3200f, 20.5f, 2200f, 22.5f, 1900f, 24f, 1900f)
    val h = ((hour % 24f) + 24f) % 24f
    var i = 0
    while (i < stops.size - 2 && stops[i + 2] < h) i += 2
    val (h0, k0, h1, k1) = listOf(stops[i], stops[i + 1], stops[i + 2], stops[i + 3])
    return k0 + (k1 - k0) * smoothstep(h0, h1, h)
}

/** The colour temperature of this hour, refreshed every minute. */
@Composable
fun rememberCircadianKelvin(): State<Float> = produceState(initialValue = circadianNow()) {
    while (true) {
        value = circadianNow()
        delay(60_000)
    }
}

private fun circadianNow(): Float {
    val now = java.util.Calendar.getInstance()
    return circadianKelvin(now.get(java.util.Calendar.HOUR_OF_DAY) + now.get(java.util.Calendar.MINUTE) / 60f)
}

/** One point of heat a finger left: where, when it was laid down and, once lifted, when. */
internal class Ember(val at: Offset, val born: Long) {
    var released: Long = -1L
    /** How hot it got while held, which is where it starts cooling from. */
    var peak: Float = 0f
}

/**
 * The heat fingers leave on a surface. A touch warms the spot under it over a quarter second,
 * spreading as it is held; on release the spot keeps glowing and cools along the black-body
 * curve, white-hot to the accent to an ember, gone in about a second. Several touches leave
 * several embers. It runs a frame clock only while something is still warm.
 */
@Stable
internal class Embers {
    val list = mutableStateListOf<Ember>()
    var now by mutableLongStateOf(0L)

    fun press(at: Offset) { now = clock(); list += Ember(at, now) }
    fun release() { now = clock(); list.lastOrNull { it.released < 0 }?.let { it.peak = heatOf(it); it.released = now } }

    private fun clock() = monotonicMillis()

    fun heatOf(e: Ember): Float {
        if (e.released < 0) return smoothstep(0f, 260f, (now - e.born).toFloat())
        return e.peak * exp(-(now - e.released) / 380f)
    }

    /**
     * [dark] lets an ember reach white-hot; on a light surface white-hot would vanish into the
     * page, so there the glow peaks at the accent's own heat and is laid on a little stronger.
     */
    fun DrawScope.drawEmbers(outline: Outline, accent: Color, glassHeat: Color, dark: Boolean) {
        val peak = if (dark) 1f else 0.62f
        val strength = if (dark) 0.5f else 0.62f
        for (e in list) {
            val h = heatOf(e)
            if (h < 0.02f) continue
            val held = if (e.released < 0) now - e.born else e.released - e.born
            val r = size.minDimension * (0.55f + 0.5f * smoothstep(0f, 700f, held.toFloat())) * (1f + 0.35f * (1f - h))
            val c = thermalColor(h * peak, accent, glassHeat)
            drawOutline(
                outline,
                Brush.radialGradient(
                    0f to c.copy(alpha = strength * h.pow(0.8f)),
                    0.45f to c.copy(alpha = 0.45f * strength * h),
                    1f to c.copy(alpha = 0f),
                    center = e.at, radius = r.coerceAtLeast(1f),
                ),
            )
        }
    }
}

@Composable
internal fun rememberEmbers(): Embers {
    val embers = remember { Embers() }
    val warm = embers.list.isNotEmpty()
    LaunchedEffect(warm) {
        if (!warm) return@LaunchedEffect
        while (embers.list.isNotEmpty()) {
            withFrameMillis {
                embers.now = monotonicMillis()
                embers.list.removeAll { it.released >= 0 && embers.heatOf(it) < 0.02f }
            }
        }
    }
    return embers
}

/**
 * Haptics, the way the best OEM skins use them: a crisp segment tick when a selection moves,
 * distinct on and off clicks for switches, a confirm for a committed action. Each falls back to
 * the nearest constant on older Android.
 */
@Stable
class KelvinHaptics internal constructor(private val perform: (HapticKind) -> Unit) {
    fun tick() = perform(HapticKind.Tick)
    fun toggle(on: Boolean) = perform(if (on) HapticKind.ToggleOn else HapticKind.ToggleOff)
    fun confirm() = perform(HapticKind.Confirm)
}

@Composable
fun rememberKelvinHaptics(): KelvinHaptics {
    val perform = rememberHapticPerformer()
    return remember(perform) { KelvinHaptics(perform) }
}

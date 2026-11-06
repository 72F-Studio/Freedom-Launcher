package studio72f.kelvin

import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hand-drawn scenes from Open Doodles by Pablo Stanley (CC0, opendoodles.com) for empty, waiting
 * and error states. Each is a list of paths converted by `tools/doodles.py`.
 */
enum class KelvinDoodle(@RawRes internal val res: Int) {
    Loving(R.raw.kelvin_doodle_loving),
    Reading(R.raw.kelvin_doodle_reading),
    Unboxing(R.raw.kelvin_doodle_unboxing),
    Clumsy(R.raw.kelvin_doodle_clumsy),
    SittingReading(R.raw.kelvin_doodle_sitting_reading),
    Chilling(R.raw.kelvin_doodle_chilling),
    Meditating(R.raw.kelvin_doodle_meditating),
}

/**
 * Draws [doodle] in the theme: the line art in `ink`, its few coloured shapes in the app's accent,
 * so a scene reads cleanly in light and dark and carries each app's own colour. Decorative: it
 * has no name for TalkBack; the empty state's title and message say what it means.
 */
@Composable
fun KelvinDoodle(doodle: KelvinDoodle, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val parts by produceState<List<Pair<Boolean, Path>>>(emptyList(), doodle) {
        value = withContext(Dispatchers.Default) {
            resources.openRawResource(doodle.res).bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
                val (role, evenOdd, tx, ty, data) = line.split(' ', limit = 5)
                val path = PathParser().parsePathString(data).toPath()
                path.translate(Offset(tx.toFloat(), ty.toFloat()))
                if (evenOdd == "1") path.fillType = PathFillType.EvenOdd
                (role == "accent") to path
            }
        }
    }
    val ink = Kelvin.colors.ink
    val accent = Kelvin.colors.accent
    Canvas(modifier.aspectRatio(1024f / 768f)) {
        val k = size.width / 1024f
        scale(k, k, pivot = Offset.Zero) {
            parts.forEach { (isAccent, path) -> drawPath(path, if (isAccent) accent else ink) }
        }
    }
}

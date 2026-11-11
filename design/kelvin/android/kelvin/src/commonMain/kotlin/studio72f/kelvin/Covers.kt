package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * The cover of something with no picture of its own (a note, a voice memo, a link without an
 * image): its first words, set large in white on one of the [KelvinTiles] colours, so a list of
 * pictures and words reads as one list. [tint] defaults to the colour [KelvinTiles.forKey] gives
 * the text, so the same note keeps its colour wherever it is shown. [icon] names what it is (a
 * microphone for a voice memo) at the top-start corner.
 *
 * The words are sized to the cover by measuring them: the longest fits on its own line, so a word
 * is never broken in two, and they get smaller (down to 8) only as far as that needs. The cover is a
 * picture of the thing, not its label: it is silent to TalkBack, and the row it sits in names it.
 */
@Composable
fun KelvinWordCover(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = KelvinTiles.forKey(text),
    icon: ImageVector? = null,
    shape: Shape = KelvinShapes.sm,
) {
    val dark = Kelvin.dark
    // Deeper toward the foot, as if lit from above; a step darker in dark mode so it never glares.
    val top = if (dark) lerp(tint, Color.Black, 0.12f) else tint
    val foot = lerp(tint, Color.Black, if (dark) 0.34f else 0.22f)
    val words = remember(text) { coverWords(text) }
    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, foot))),
    ) {
        val pad = min(maxWidth, maxHeight) * 0.11f
        if (icon != null) {
            KIcon(icon, null, Modifier.align(Alignment.TopStart).padding(pad), size = 14.dp, color = Color.White.copy(alpha = 0.85f))
        }
        if (words.isNotEmpty()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val base = Kelvin.type.name.copy(color = Color.White, fontFamily = Kelvin.type.displayAt(650), fontWeight = FontWeight(650))
            val room = with(density) { (maxWidth - pad * 2).roundToPx() }
            // The largest size, in steps of one, at which the longest word sits on one line.
            val sp = remember(words, room, base, density) {
                val longest = words.split(' ').maxBy { it.length }
                var size = CoverMax
                while (size > CoverMin) {
                    val w = measurer.measure(longest, base.copy(fontSize = size.sp), softWrap = false, maxLines = 1).size.width
                    // A little to spare: the variable font may set a touch wider than it measures before it has loaded.
                    if (w <= room * 0.92f) break
                    size -= 1f
                }
                size.sp
            }
            BasicText(
                words,
                Modifier.align(Alignment.BottomStart).padding(pad),
                style = base.copy(fontSize = sp, lineHeight = sp * 1.08f),
                maxLines = 3,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** The first few words that carry letters or digits, without markup, up to about 24 characters. */
internal fun coverWords(text: String): String {
    val words = text.replace(Regex("[#*_>`\\[\\]()]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim(',', '.', ':', ';', '!', '?', '"', '\'') }
        .filter { w -> w.any { it.isLetterOrDigit() } }
    val out = StringBuilder()
    for (w in words) {
        if (out.isNotEmpty() && out.length + 1 + w.length > 24) break
        if (out.isNotEmpty()) out.append(' ')
        out.append(w)
        if (out.length >= 14) break
    }
    return out.toString()
}

/** The cover's word size runs from 18 down to 8, the smallest that still reads at a glance. */
private const val CoverMax = 18f
private const val CoverMin = 8f

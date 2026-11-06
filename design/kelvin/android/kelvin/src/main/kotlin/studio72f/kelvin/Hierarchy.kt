package studio72f.kelvin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/*
 * Hierarchy, the OriginOS way: contrast comes from size, face and colour working together, not
 * from bold alone. A figure's numbers are large and light, its marks (the / of a date, the : of
 * a time) carry the accent, and its units sit small and grey on the same baseline, so "7/2026",
 * "12:32" and "86%" each read at a glance and look like nobody else's.
 */

internal enum class FigurePart { Number, Mark, Unit }

/**
 * Splits a figure into numbers (digits, with a decimal point or thousands separator between
 * digits), marks (`/`, `:` and `–`) and units (everything else: %, GB, °C, ₹, <, k).
 */
internal fun figureParts(text: String): List<Pair<FigurePart, String>> {
    // Words with no digits ("End of episode", "Off") are set whole in the figure's style.
    if (text.none { it.isDigit() }) return listOf(FigurePart.Number to text)
    fun kind(i: Int): FigurePart {
        val ch = text[i]
        if (ch.isDigit()) return FigurePart.Number
        val between = i > 0 && i < text.lastIndex && text[i - 1].isDigit() && text[i + 1].isDigit()
        if ((ch == '.' || ch == ',') && between) return FigurePart.Number
        if (ch == '/' || ch == ':' || ch == '–') return FigurePart.Mark
        return FigurePart.Unit
    }
    val out = mutableListOf<Pair<FigurePart, String>>()
    var i = 0
    while (i < text.length) {
        val k = kind(i)
        var j = i + 1
        while (j < text.length && kind(j) == k) j++
        val run = text.substring(i, j).let { if (k == FigurePart.Unit) it.trim() else it }
        if (run.isNotEmpty()) out += k to run
        i = j
    }
    return out
}

/**
 * The ink of a figure that is the point of its screen (a score, storage used): the ink at the
 * top, warming into the accent toward the baseline, as OriginOS's iManager sets its score.
 */
@Composable
fun kelvinHeatInk(): Brush {
    val c = Kelvin.colors
    return Brush.verticalGradient(0f to c.ink, 0.3f to c.ink, 0.9f to c.accent)
}

/**
 * A figure set in Kelvin's grammar: numbers in [style] (large and light), marks in the [mark]
 * colour, units at 40% size on the same baseline: symbols (%, °, ₹) in [unit], words (GB, AQI, k) in
 * [unitWord], `ink-2`, so no accent word appears that is not an action. Marks and symbols default to `on-accent-soft`, the accent
 * as text: lighter than the fill in dark, deeper in light, so a small % or : stays legible. Numbers roll when they change
 * ([rolling]); [heat] sets the numbers in [kelvinHeatInk]. Read aloud as written.
 */
@Composable
fun KelvinFigure(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Kelvin.type.numeral,
    color: Color = Kelvin.colors.ink,
    heat: Boolean = false,
    mark: Color = Kelvin.colors.figureAccent,
    unit: Color = Kelvin.colors.figureAccent,
    unitWord: Color = Kelvin.colors.ink2,
    rolling: Boolean = true,
) {
    // A figure never wraps; when it is wider than its room (a narrow card, large text) it is drawn
    // scaled down as a whole to fit, units and marks with it, rather than being cut off. A plain
    // custom layout, so rows that ask for intrinsic sizes (KelvinStatRow) can still measure it.
    androidx.compose.ui.layout.Layout(
        content = { FigureRow(text, Modifier, style, color, heat, mark, unit, unitWord, rolling) },
        modifier = modifier,
        measurePolicy = FitFigurePolicy,
    )
}

/**
 * Lays a figure out at its natural size, scaled down when that is wider than the room. It answers
 * intrinsic questions itself (a [KelvinStatRow] asks for its cards' heights before measuring them):
 * the height a figure needs at a given width is its natural height times the same scale.
 */
private object FitFigurePolicy : androidx.compose.ui.layout.MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>,
        constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val p = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
        val k = if (constraints.hasBoundedWidth && p.width > constraints.maxWidth && p.width > 0) constraints.maxWidth.toFloat() / p.width else 1f
        val w = (p.width * k).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (p.height * k).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val base = p[androidx.compose.ui.layout.FirstBaseline]
        val lines = if (base == androidx.compose.ui.layout.AlignmentLine.Unspecified) emptyMap()
            else mapOf<androidx.compose.ui.layout.AlignmentLine, Int>(androidx.compose.ui.layout.FirstBaseline to (base * k).toInt())
        return layout(w, h, lines) {
            p.placeWithLayer(0, 0) {
                scaleX = k; scaleY = k
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
        }
    }

    private fun scaleAt(m: androidx.compose.ui.layout.IntrinsicMeasurable, width: Int): Float {
        val natural = m.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        return if (width != androidx.compose.ui.unit.Constraints.Infinity && natural > width && natural > 0) width.toFloat() / natural else 1f
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int): Int {
        val m = measurables.first()
        return (m.minIntrinsicHeight(androidx.compose.ui.unit.Constraints.Infinity) * scaleAt(m, width)).toInt()
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int): Int {
        val m = measurables.first()
        return (m.maxIntrinsicHeight(androidx.compose.ui.unit.Constraints.Infinity) * scaleAt(m, width)).toInt()
    }

    // Any width works (it scales), so the least it needs is nothing; the most, its natural width.
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int): Int = 0

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int): Int =
        measurables.first().maxIntrinsicWidth(height)
}

@Composable
private fun FigureRow(
    text: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
    heat: Boolean,
    mark: Color,
    unit: Color,
    unitWord: Color,
    rolling: Boolean,
) {
    val parts = remember(text) { figureParts(text) }
    val brush = if (heat) kelvinHeatInk() else null
    val unitStyle = style.copy(
        color = unit,
        // Units are 40% of a large figure; on a small one they keep to at least 12sp (or the
        // figure's own size) so they stay legible.
        fontSize = (style.fontSize.value * 0.4f).coerceAtLeast(minOf(style.fontSize.value * 0.92f, 12f)).sp,
        lineHeight = TextUnit.Unspecified,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = TextUnit.Unspecified,
        fontFeatureSettings = null,
    )
    // The numbers' baseline, measured once from the style. Rolling digits must not supply their
    // own: mid-roll a digit has two copies sliding past each other, and aligning the row to them
    // made the whole figure jump.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val baseline = remember(style, measurer) { measurer.measure("0", style).firstBaseline.toInt() }
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        parts.forEachIndexed { i, (kind, run) ->
            val m = if (kind == FigurePart.Unit) Modifier.alignByBaseline() else Modifier.alignBy { baseline }
            when (kind) {
                FigurePart.Number ->
                    if (rolling) KelvinRollingText(run, m, style, color, brush)
                    else BasicText(run, m, style = if (brush != null) style.copy(brush = brush) else style.copy(color = color), softWrap = false, maxLines = 1)
                FigurePart.Mark -> BasicText(run, m, style = style.copy(color = mark), softWrap = false, maxLines = 1)
                // Symbols (%, °, ₹) are punctuation and take the accent; words (GB, AQI, k) stay
                // neutral, because an accent word reads as something to tap.
                FigurePart.Unit -> BasicText(
                    run,
                    m.padding(start = if (i > 0) 3.dp else 0.dp, end = if (i < parts.lastIndex) 3.dp else 0.dp),
                    style = if (run.any { it.isLetter() }) unitStyle.copy(color = unitWord) else unitStyle,
                    softWrap = false, maxLines = 1,
                )
            }
        }
    }
}

/** A small uppercase label over a figure or a coloured card, in the display face, tracked wide. */
@Composable
fun KelvinEyebrow(text: String, modifier: Modifier = Modifier, color: Color = Kelvin.colors.ink2) =
    BasicText(text.uppercase(), modifier, style = Kelvin.type.eyebrow.copy(color = color))

/**
 * The top-level switch between a page's views, set as titles (OriginOS's Apps and Widgets): the
 * current view in full ink, the others the same size at 30%, a short accent bar under the current
 * one that slides when it changes. [trailing] holds an action at the end of the row.
 */
@Composable
fun KelvinTitleTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val centres = remember { mutableStateMapOf<Int, Float>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val barHalf = with(density) { 11.dp.toPx() }
    val x by animateFloatAsState((centres[selected] ?: 0f) - barHalf, Kelvin.motion.spatial(), label = "title tab")
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                // The ink follows the sliding bar: full under it, 30% a tab away, so the titles
                // trade weight as the bar travels rather than on a timer of their own.
                val step = if (centres.size > 1) abs((centres[1] ?: 0f) - (centres[0] ?: 0f)).coerceAtLeast(1f) else 1f
                val near = centres[i]?.let { (1f - abs(x + barHalf - it) / step).coerceIn(0f, 1f) } ?: (if (on) 1f else 0f)
                val ink = androidx.compose.ui.graphics.lerp(c.ink.copy(alpha = 0.3f), c.ink, near)
                BasicText(
                    t,
                    Modifier
                        .onPlaced { centres[i] = it.positionInParent().x + it.size.width / 2f }
                        .semantics { this.selected = on; heading() }
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                            if (!on) { haptics.tick(); onSelect(i) }
                        },
                    style = Kelvin.type.tab.copy(color = ink),
                )
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        if (centres.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomStart)
                    .graphicsLayer { translationX = x }
                    .size(22.dp, 3.dp)
                    .background(c.accent, CircleShape),
            )
        }
    }
}

/**
 * Time with its date stacked beside it, as OriginOS sets its notification shade: the time a light
 * hero figure with an accent colon, the date and weekday in the display face beside it.
 */
@Composable
fun KelvinTimeLockup(time: String, date: String, day: String, modifier: Modifier = Modifier, style: TextStyle = Kelvin.type.hero) {
    val c = Kelvin.colors
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        KelvinFigure(time, style = style)
        Column(Modifier.padding(start = 14.dp)) {
            BasicText(date, style = Kelvin.type.title.copy(color = c.ink))
            BasicText(day, style = Kelvin.type.title.copy(color = c.ink2, fontWeight = FontWeight.Medium))
        }
    }
}

/*
 * Legibility. Kelvin's accent and status colours are vivid enough for fills, which in light
 * mode makes them too light for small text on a light ground. Text set in a colour therefore
 * goes through [legibleOn]: same hue and chroma, lightness moved (darker on a light ground,
 * lighter on a dark one) until it meets WCAG's 4.5:1. Fills never change.
 */
private val legibleCache = java.util.concurrent.ConcurrentHashMap<Long, Color>()

/**
 * This colour, as text on [background], moved in OKLab lightness only as far as it must to meet
 * [ratio] (4.5:1 by default, WCAG AA for normal text). Already legible colours come back as they are.
 */
fun Color.legibleOn(background: Color, ratio: Float = 4.5f): Color {
    val key = (toArgb().toLong() shl 32) or (background.toArgb().toLong() and 0xffffffffL) xor (ratio.toRawBits().toLong() shl 7)
    return legibleCache.getOrPut(key) { computeLegible(this, background, ratio) }
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

private fun computeLegible(fg: Color, bg: Color, ratio: Float): Color {
    val opaque = fg.copy(alpha = 1f)
    if (contrast(opaque, bg) >= ratio) return fg
    val lab = opaque.convert(androidx.compose.ui.graphics.colorspace.ColorSpaces.Oklab)
    val l0 = lab.component1()
    val a = lab.component2()
    val b = lab.component3()
    val darken = bg.luminance() > 0.18f
    fun at(l: Float) = Color(l.coerceIn(0f, 1f), a, b, 1f, androidx.compose.ui.graphics.colorspace.ColorSpaces.Oklab)
        .convert(androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb)
    // Bisect between the colour and black (or white) for the lightness closest to it that passes.
    var lo = if (darken) 0f else l0
    var hi = if (darken) l0 else 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        val ok = contrast(at(mid), bg) >= ratio
        if (darken) { if (ok) lo = mid else hi = mid } else { if (ok) hi = mid else lo = mid }
    }
    val l = if (darken) lo else hi
    return at(l).copy(alpha = fg.alpha)
}

/**
 * This height, grown by as much as one line of text [line] tall grows with the person's font
 * size, so a fixed-height control holding text (a chip, a pill, a field) never clips it at 200%
 * (WCAG 1.4.4). At the default font size it is unchanged.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun androidx.compose.ui.unit.Dp.withTextScale(line: TextUnit): androidx.compose.ui.unit.Dp {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    return if (fs <= 1f || !line.isSp) this else this + (line.value * (fs - 1f)).dp
}

/** Whether the person has made text much larger (more than 130%), so dense labels give way. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun largeText(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f

/**
 * The ink for an icon on this fill: white wherever white clears WCAG's 3:1, black where it cannot.
 * Every accent and dial fill is tuned so white clears 3:1, so on them this is white; it guards
 * fills that are not (Graphite in dark, an app's own colour).
 */
fun Color.iconInk(): Color = if (contrast(Color.White, this) >= 3f) Color.White else Color(0xFF141414)

/**
 * The accent for a figure's marks and symbol units (the `:` of a time, the `/` of a date, a `%`):
 * the accent itself, darkened only as far as 3.5:1 on the group grey needs. They sit on large
 * figures and carry no meaning alone, so they take a little over the large-text bar, staying vivid
 * where text-safe `on-accent-soft` (4.5:1) would read dull.
 */
val KelvinColors.figureAccent: Color get() = accent.legibleOn(group, 3.5f)

package studio72f.kelvin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Immutable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Selection: checkbox, radio, stepper, chips and count badges. Every one is a 48dp target, moves
 * like a pebble under the finger (kelvinPull), ticks or toggles on the haptics, takes its name
 * from the KelvinRow it sits in, and never shows state by colour alone: a checkbox draws its tick,
 * a radio its dot, a chosen chip its check.
 */

/** The ring of an empty control: neutral ink as light as it can go and still hold 3:1. */
@Composable
internal fun emptyRing(): Color {
    val c = Kelvin.colors
    return lerp(c.ink, c.canvas, 0.6f).legibleOn(c.group, 3f)
}

/**
 * A checkbox: an empty rounded square at rest; checked, the accent fills it from the centre and
 * a white tick draws itself in. Named by its [KelvinRow] or [contentDescription].
 */
@Composable
fun KelvinCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = Kelvin.colors
    val label = contentDescription ?: LocalControlLabel.current
    val haptics = rememberKelvinHaptics()
    val fill by animateFloatAsState(if (checked) 1f else 0f, Kelvin.motion.wobble(), label = "fill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, Kelvin.motion.gentle(), label = "tick")
    val ring = emptyRing()
    val accent = c.accent
    Box(
        modifier
            .size(48.dp)
            .kelvinPull({ haptics.toggle(!checked); onCheckedChange(!checked) }, enabled, Role.Checkbox, shape = KelvinShapes.xs, wash = false)
            .semantics {
                stateDescription = if (checked) "checked" else "not checked"
                if (label != null) this.contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).drawBehind {
                val r = CornerRadius(6.dp.toPx())
                val w = 2.dp.toPx()
                drawRoundRect(ring, cornerRadius = r, style = Stroke(w), alpha = 1f - fill.coerceIn(0f, 1f))
                // The fill grows from the centre and settles with a little overshoot.
                val s = size.minDimension * fill.coerceIn(0f, 1.15f)
                if (s > 0.5f) drawRoundRect(
                    accent,
                    topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f),
                    size = androidx.compose.ui.geometry.Size(s, s),
                    cornerRadius = CornerRadius(6.dp.toPx() * (s / size.minDimension)),
                )
                if (tick > 0.01f) {
                    val path = Path().apply {
                        moveTo(size.width * 0.27f, size.height * 0.53f)
                        lineTo(size.width * 0.43f, size.height * 0.68f)
                        lineTo(size.width * 0.74f, size.height * 0.36f)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val drawn = Path()
                    measure.getSegment(0f, measure.length * tick, drawn, true)
                    drawPath(drawn, accent.iconInk(), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            },
        )
    }
}

/**
 * A radio button: an empty ring at rest; chosen, the ring thickens into the accent and a white
 * dot settles in its centre. Use in a [KelvinGroup] of [KelvinRow]s, one per option.
 */
@Composable
fun KelvinRadio(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val c = Kelvin.colors
    val label = contentDescription ?: LocalControlLabel.current
    val haptics = rememberKelvinHaptics()
    val on by animateFloatAsState(if (selected) 1f else 0f, Kelvin.motion.wobble(), label = "radio")
    val ring = emptyRing()
    val accent = c.accent
    Box(
        modifier
            .size(48.dp)
            .kelvinPull({ if (!selected) { haptics.tick(); onClick() } }, enabled, Role.RadioButton, shape = CircleShape, wash = false)
            .semantics {
                stateDescription = if (selected) "selected" else "not selected"
                if (label != null) this.contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).drawBehind {
                val r = size.minDimension / 2f
                val p = on.coerceIn(0f, 1.15f)
                drawCircle(lerp(ring, accent, on.coerceIn(0f, 1f)), r - 1.dp.toPx(), style = Stroke(2.dp.toPx() + (r - 2.dp.toPx()) * p.coerceAtMost(1f)))
                if (p > 0.01f) drawCircle(Color.White, r * 0.36f * p)
            },
        )
    }
}

/**
 * A stepper: minus, the value as a figure that rolls as it changes, plus, on one capsule. Each
 * end is its own pebble and dims at the limit of [range]. [format] turns the value into what is
 * shown and read ("1.5×", "30 min").
 * [caption] names the value under it ("conversational" under 300 wpm); [onValueClick] makes the
 * value itself a button that opens a finer control (a slider). With [fill] the value takes the
 * row's spare width, for a stepper that is the main control of its line.
 */
@Composable
fun KelvinStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    range: IntRange = 0..99,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    caption: String? = null,
    onValueClick: (() -> Unit)? = null,
    valueClickLabel: String? = null,
    fill: Boolean = false,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val lines = if (caption != null) Kelvin.type.figure.lineHeight * 2 else Kelvin.type.figure.lineHeight
    Row(
        modifier
            .height((if (caption != null) 56.dp else 44.dp).withTextScale(lines))
            .background(c.sunken, CircleShape)
            .semantics(mergeDescendants = false) { stateDescription = format(value) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canDown = value - step >= range.first
        val canUp = value + step <= range.last
        Box(
            Modifier.size(44.dp).kelvinPull({ haptics.tick(); onValueChange(value - step) }, canDown, onClickLabel = decreaseLabel)
                .semantics { contentDescription = decreaseLabel },
            contentAlignment = Alignment.Center,
        ) { KIcon(KelvinIcons.Minus, null, size = 20.dp) }
        Column(
            (if (fill) Modifier.weight(1f) else Modifier).widthIn(min = 40.dp)
                .then(if (onValueClick != null) Modifier.kelvinPull(onValueClick, onClickLabel = valueClickLabel, shape = CircleShape) else Modifier)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KelvinFigure(format(value), style = Kelvin.type.figure.copy(fontWeight = FontWeight.SemiBold))
            if (caption != null) KText(caption, style = Kelvin.type.caption, color = c.ink2, maxLines = 1)
        }
        Box(
            Modifier.size(44.dp).kelvinPull({ haptics.tick(); onValueChange(value + step) }, canUp, onClickLabel = increaseLabel)
                .semantics { contentDescription = increaseLabel },
            contentAlignment = Alignment.Center,
        ) { KIcon(KelvinIcons.Plus, null, size = 20.dp) }
    }
}

/**
 * A chip. A filter chip ([selected] not null) turns `accent-soft` with a check when chosen; an
 * input chip ([onRemove] given, for a recipient or a tag) carries an x that removes it. Both move
 * like pebbles; the check and the x are real glyphs, so state never rests on colour.
 */
@Composable
fun KelvinChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
) {
    val c = Kelvin.colors
    val on = selected == true
    val fill by animateColorAsState(if (on) c.accentSoft else c.sunken, Kelvin.motion.gentle(), label = "chip")
    val ink = if (on) c.onAccentSoft else c.ink
    Row(
        modifier
            .heightIn(min = 36.dp.withTextScale(Kelvin.type.label.lineHeight))
            .then(if (onClick != null) Modifier.kelvinPull(onClick, role = if (selected != null) Role.Checkbox else Role.Button, shape = CircleShape, wash = false) else Modifier)
            .background(fill, CircleShape)
            .semantics { if (selected != null) stateDescription = if (on) "selected" else "not selected" }
            .padding(start = if (on || icon != null) 10.dp else 14.dp, end = if (onRemove != null) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (on) KIcon(KelvinIcons.Check, null, size = 16.dp, color = ink)
        else if (icon != null) KIcon(icon, null, size = 16.dp, color = ink)
        KText(text, style = Kelvin.type.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = ink, maxLines = 1)
        if (onRemove != null) Box(
            Modifier.size(28.dp).kelvinPull(onRemove, onClickLabel = removeLabel, shape = CircleShape)
                .semantics { if (removeLabel != null) contentDescription = removeLabel },
            contentAlignment = Alignment.Center,
        ) { KIcon(KelvinIcons.X, null, size = 14.dp, color = c.ink2) }
    }
}

/**
 * A count badge: the accent capsule with a white figure (`99+` past [max]), rolling as it
 * changes. Hidden at zero. On a dock tab or an icon use [KelvinBadged].
 */
@Composable
fun KelvinBadge(count: Int, modifier: Modifier = Modifier, max: Int = 99) {
    if (count <= 0) return
    val c = Kelvin.colors
    val text = if (count > max) "$max+" else count.toString()
    Box(
        modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).background(c.accent, CircleShape).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        KelvinRollingText(text, style = Kelvin.type.micro.copy(fontWeight = FontWeight.Bold), color = c.accent.iconInk())
    }
}

/** [content] (an icon, an avatar) with a [KelvinBadge] on its top-end corner. */
@Composable
fun KelvinBadged(count: Int, modifier: Modifier = Modifier, offset: Dp = 6.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        KelvinBadge(count, Modifier.align(Alignment.TopEnd).offset(x = offset, y = -offset))
    }
}

/**
 * One option in a [KelvinChoiceList]: its [label], set in [labelStyle] when the option is a face
 * (a reading font shows itself), and a [summary] that says or shows what it does (a sample line
 * with the effect applied).
 */
@Immutable
data class KelvinChoice(
    val label: String,
    val summary: androidx.compose.ui.text.AnnotatedString? = null,
    val labelStyle: androidx.compose.ui.text.TextStyle? = null,
)

/**
 * Choosing one of a handful of options that each deserve a line (a reading font, a strength):
 * rows with a radio at the end, the whole row the target. Pills are for narrowing a list and the
 * segmented control for two or three short values; reach for this when options need room to say
 * or show what they are. Put it in a [KelvinGroup] of its own under a [KelvinSectionHeader] that
 * names the setting; the rows go straight into the group, so it draws its dividers between them.
 * TalkBack reads each row as a radio button, selected or not, with its summary.
 */
@Composable
fun KelvinChoiceList(choices: List<KelvinChoice>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    choices.forEachIndexed { i, choice ->
        val chosen = i == selected
        Row(
            Modifier.groupDividerInset(16.dp).fillMaxWidth().heightIn(min = 56.dp)
                .kelvinPress({ if (!chosen) { haptics.tick(); onSelect(i) } }, role = Role.RadioButton)
                .semantics(mergeDescendants = true) { this.selected = chosen }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                KText(choice.label, style = choice.labelStyle?.let { Kelvin.type.rowTitle.merge(it) } ?: Kelvin.type.rowTitle, color = c.ink)
                if (choice.summary != null) {
                    androidx.compose.foundation.text.BasicText(choice.summary, style = Kelvin.type.summary.copy(color = c.ink2))
                }
            }
            // The row is the control; the radio only draws its state.
            KelvinRadio(chosen, { if (!chosen) { haptics.tick(); onSelect(i) } }, Modifier.clearAndSetSemantics {})
        }
    }
}

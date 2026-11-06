package studio72f.kelvin

import androidx.compose.foundation.layout.offset

import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class Tone { Neutral, Info, Positive, Caution, Critical, Accent }

internal data class ToneColors(val fill: Color, val ink: Color)

@Composable
internal fun Tone.colors(): ToneColors {
    val c = Kelvin.colors
    return when (this) {
        Tone.Neutral -> ToneColors(c.sunken, c.ink2)
        // Status colours are vivid fills; as text on their soft fill they take a legible shade.
        Tone.Info -> ToneColors(c.infoSoft, c.info.legibleOn(c.infoSoft))
        Tone.Positive -> ToneColors(c.positiveSoft, c.positive.legibleOn(c.positiveSoft))
        Tone.Caution -> ToneColors(c.cautionSoft, c.caution.legibleOn(c.cautionSoft))
        Tone.Critical -> ToneColors(c.criticalSoft, c.critical.legibleOn(c.criticalSoft))
        Tone.Accent -> ToneColors(c.accentSoft, c.onAccentSoft)
    }
}

/** Small capsule naming a state. Always include the word; colour never carries meaning alone. */
@Composable
fun KelvinPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, icon: ImageVector? = null) {
    val t = tone.colors()
    Row(
        modifier.height(26.dp.withTextScale(Kelvin.type.caption.lineHeight)).background(t.fill, CapsuleShape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) KIcon(icon, null, size = 14.dp, color = t.ink)
        KText(text, style = Kelvin.type.caption.copy(fontWeight = FontWeight.SemiBold), color = t.ink, maxLines = 1)
    }
}

/**
 * A notice in a group: a glass tile carrying a glyph whose shape says what kind it is (a tick for
 * success, a warning triangle for a caution or an error, an "i" for information), so it never rests
 * on colour, then the title, an optional [message], an optional [progress] (negative for work of
 * unknown length) and one action. A Neutral or Accent notice may show its topic's [icon] instead.
 * [onDismiss] adds an x. [gutter] is the margin either side (0 inside a padded container).
 */
@Composable
fun KelvinNotice(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    progress: Float? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "",
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val t = tone.colors()
    val glyph = when (tone) {
        Tone.Caution, Tone.Critical -> KelvinIcons.Alert
        Tone.Positive -> KelvinIcons.CircleCheck
        Tone.Info -> KelvinIcons.Info
        Tone.Neutral, Tone.Accent -> icon ?: KelvinIcons.Info
    }
    Row(
        modifier.padding(horizontal = gutter).fillMaxWidth().background(t.fill, KelvinShapes.md)
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = if (onDismiss != null) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The library's glass tile, in the tone's own colour.
        val c = Kelvin.colors
        val tileColor = when (tone) {
            Tone.Info -> c.info; Tone.Positive -> c.positive; Tone.Caution -> c.caution
            Tone.Critical -> c.critical; Tone.Accent -> c.accent; Tone.Neutral -> c.ink3
        }
        KelvinGlassTile(glyph, tileColor)
        Column(Modifier.weight(1f)) {
            KText(title, style = Kelvin.type.name)
            if (!message.isNullOrEmpty()) KText(message, style = Kelvin.type.summary, color = Kelvin.colors.ink2)
            if (progress != null) {
                KelvinProgressBar(progress.takeIf { it >= 0f }, Modifier.padding(top = 8.dp), color = t.ink, track = Kelvin.colors.hairline)
            }
            if (actionText != null && onAction != null) {
                KText(actionText, Modifier.padding(top = 8.dp).kelvinPress(onAction), Kelvin.type.action, t.ink)
            }
        }
        if (onDismiss != null) KelvinIconButton(KelvinIcons.X, dismissLabel, onDismiss, Modifier.offset(y = (-12).dp), kind = IconButtonKind.Plain)
    }
}

/** Full-area empty, waiting or failed state: icon, headline, one sentence, one action. */
@Composable
fun KelvinEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    doodle: KelvinDoodle? = null,
) {
    val c = Kelvin.colors
    // The scene or tile bobs gently, as if breathing; still with reduced motion.
    val bob = if (Kelvin.reduceMotion) 0f else {
        val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "empty").animateFloat(
            -4f, 4f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1800), androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "bob",
        )
        t
    }
    Column(modifier.fillMaxWidth().horizon(0.7f).padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val float = Modifier.graphicsLayer { translationY = bob * density }
        if (doodle != null) KelvinDoodle(doodle, float.widthIn(max = 280.dp).fillMaxWidth().padding(bottom = 8.dp))
        else KelvinGlassTile(icon, c.accent, float, size = 72.dp)
        KText(title, Modifier.semantics { heading() }, style = Kelvin.type.title, align = androidx.compose.ui.text.style.TextAlign.Center)
        KText(message, style = Kelvin.type.body, color = c.ink2, align = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionText != null && onAction != null) KelvinButton(actionText, onAction, Modifier.padding(top = 8.dp))
    }
}

/** Filled text field: label above, grey box, accent border on focus, critical border and message on error. */
@Composable
fun KelvinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    help: String? = null,
    error: String? = null,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = Kelvin.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KelvinEyebrow(label, Modifier.padding(start = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Kelvin.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .semantics { if (error != null) error(error) },
            decorationBox = { field ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp.withTextScale(Kelvin.type.body.lineHeight)).clip(KelvinShapes.md)
                        .background(if (focused) c.group else c.sunken)
                        .border(2.dp, when { error != null -> c.critical; focused -> c.accent; else -> Color.Transparent }, KelvinShapes.md)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (icon != null) KIcon(icon, null, size = 20.dp, color = c.ink2)
                    Box(Modifier.weight(1f)) { field() }
                }
            },
        )
        val note = error ?: help
        if (error != null) KelvinNote(error, Tone.Critical, Modifier.padding(start = 4.dp))
        else if (note != null) KText(note, Modifier.padding(start = 4.dp), Kelvin.type.summary, c.ink2)
    }
}

/** Indeterminate progress: an accent arc turning on a faint track. For a known amount use a bar. */
@Composable
fun KelvinSpinner(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp, color: Color = Kelvin.colors.accent, strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp) {
    val turn = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinner")
    val angle by turn.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "spinner angle")
    val track = Kelvin.colors.hairline
    androidx.compose.foundation.Canvas(modifier.size(size).then(Modifier.graphicsLayer()).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val inset = strokeWidth.toPx() / 2f
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
        drawArc(color, angle - 90f, 100f, false, topLeft, arcSize, style = stroke)
    }
}

/** A hairline between items that are not in a [KelvinGroup] (inside one, the gap between cards does this). */
@Composable
fun KelvinDivider(modifier: Modifier = Modifier, startInset: androidx.compose.ui.unit.Dp = 0.dp, endInset: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(modifier.fillMaxWidth().padding(start = startInset, end = endInset).height(1.dp).background(Kelvin.colors.hairline))
}

/**
 * Text whose digits roll like an odometer when they change, as HyperOS clocks and iOS numeric
 * transitions do: each digit slides up through a clipped window when it grows and down when it
 * shrinks (9 to 0 counts as growing), on the spatial spring. Other characters swap in place.
 * Use tabular figures (`hero`, `numeral`) so the row never jitters. Read as one value by
 * TalkBack. With reduced motion, digits change in place.
 */
@Composable
fun KelvinRollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = Kelvin.type.numeral,
    color: Color = Kelvin.colors.ink,
    brush: androidx.compose.ui.graphics.Brush? = null,
) {
    val reduce = Kelvin.reduceMotion
    val ink = if (brush != null) style.copy(brush = brush) else style.copy(color = color)
    val slide = Kelvin.motion.spatial<androidx.compose.ui.unit.IntOffset>()
    val fade = Kelvin.motion.gentle<Float>()
    androidx.compose.foundation.layout.Row(
        modifier.rollingSemantics(text),
    ) {
        text.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(text.length - i) {
                // A digit that changes faster than a roll takes (a timer's hundredths) swaps in
                // place; rolling it would leave it forever mid-flight.
                val lastChange = remember { longArrayOf(0L, 0L) }
                val now = android.os.SystemClock.uptimeMillis()
                val fast = remember(ch) { val f = now - lastChange[0] < 320L; lastChange[0] = now; f }
                androidx.compose.animation.AnimatedContent(
                    targetState = ch,
                    // Each digit rolls inside its own line, never over its neighbours.
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        val from = initialState
                        val to = targetState
                        if (reduce || fast || !from.isDigit() || !to.isDigit()) {
                            // Instant: no overlap of the old and new glyph, and no size spring.
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) togetherWith
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap())) using
                                androidx.compose.animation.SizeTransform(clip = true) { _, _ -> androidx.compose.animation.core.snap() }
                        } else {
                            val up = to > from || (from == '9' && to == '0')
                            (androidx.compose.animation.slideInVertically(slide) { h -> if (up) h else -h } + androidx.compose.animation.fadeIn(fade)) togetherWith
                                (androidx.compose.animation.slideOutVertically(slide) { h -> if (up) -h else h } + androidx.compose.animation.fadeOut(fade)) using
                                androidx.compose.animation.SizeTransform(clip = true)
                        }
                    },
                    label = "digit",
                ) { c -> androidx.compose.foundation.text.BasicText(c.toString(), style = ink) }
            }
        }
    }
}

private fun Modifier.rollingSemantics(text: String): Modifier =
    this.then(Modifier.semantics(mergeDescendants = true) { contentDescription = text })

/**
 * A short message under a field or control in a status tone, led by a glyph whose shape says what
 * it is, so it never rests on colour alone (for people who do not see red from green): an error
 * or a caution is a warning triangle, success a tick in a circle, information an "i". The text
 * takes the tone's ink at 4.5:1. The glyph sits on the text's first line.
 */
@Composable
fun KelvinNote(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    val (icon, ink) = when (tone) {
        Tone.Critical -> KelvinIcons.Alert to c.critical
        Tone.Caution -> KelvinIcons.Alert to c.caution
        Tone.Positive -> KelvinIcons.CircleCheck to c.positive
        Tone.Info -> KelvinIcons.Info to c.info
        Tone.Accent -> KelvinIcons.Info to c.accent
        Tone.Neutral -> KelvinIcons.Info to c.ink2
    }
    val legible = ink.legibleOn(c.group)
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        KIcon(icon, null, Modifier.padding(top = 1.dp), size = 16.dp, color = legible)
        KText(text, style = Kelvin.type.summary, color = legible)
    }
}

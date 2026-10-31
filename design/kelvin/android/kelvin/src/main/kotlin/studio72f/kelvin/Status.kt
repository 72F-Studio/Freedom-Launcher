package studio72f.kelvin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class Tone { Neutral, Info, Positive, Caution, Critical, Accent }

private data class ToneColors(val fill: Color, val ink: Color)

@Composable
private fun Tone.colors(): ToneColors {
    val c = Kelvin.colors
    return when (this) {
        Tone.Neutral -> ToneColors(c.sunken, c.ink2)
        Tone.Info -> ToneColors(c.infoSoft, c.info)
        Tone.Positive -> ToneColors(c.positiveSoft, c.positive)
        Tone.Caution -> ToneColors(c.cautionSoft, c.caution)
        Tone.Critical -> ToneColors(c.criticalSoft, c.critical)
        Tone.Accent -> ToneColors(c.accentSoft, c.onAccentSoft)
    }
}

/** Small capsule naming a state. Always include the word; colour never carries meaning alone. */
@Composable
fun KelvinPill(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, icon: ImageVector? = null) {
    val t = tone.colors()
    Row(
        modifier.height(26.dp).background(t.fill, CapsuleShape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) KIcon(icon, null, size = 14.dp, color = t.ink)
        KText(text, style = Kelvin.type.caption.copy(fontWeight = FontWeight.SemiBold), color = t.ink, maxLines = 1)
    }
}

/**
 * Inline status: what is happening, what to do, optional progress (0–1, or negative for
 * "working") and one action.
 */
@Composable
fun KelvinNotice(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    progress: Float? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val t = tone.colors()
    val icon = when (tone) { Tone.Caution -> KelvinIcons.Alert; Tone.Critical -> KelvinIcons.Alert; else -> KelvinIcons.Info }
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().background(t.fill, KelvinShapes.md).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KIcon(icon, null, size = 22.dp, color = t.ink)
        Column(Modifier.weight(1f)) {
            KText(title, style = Kelvin.type.body.copy(fontWeight = FontWeight.SemiBold))
            KText(message, style = Kelvin.type.summary, color = Kelvin.colors.ink2)
            if (progress != null) {
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).background(Kelvin.colors.hairline, CapsuleShape)) {
                    if (progress >= 0f) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(t.ink, CapsuleShape))
                }
            }
            if (actionText != null && onAction != null) {
                KText(actionText, Modifier.padding(top = 8.dp).kelvinPress(onAction), Kelvin.type.label, t.ink)
            }
        }
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
) {
    val c = Kelvin.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(72.dp).clip(ContinuousShape(24.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            KIcon(icon, null, size = 34.dp, color = c.onAccentSoft)
        }
        KText(title, style = Kelvin.type.title, align = androidx.compose.ui.text.style.TextAlign.Center)
        KText(message, style = Kelvin.type.body, color = c.ink2, align = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionText != null && onAction != null) KelvinButton(actionText, onAction)
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
        KText(label, Modifier.padding(start = 4.dp), Kelvin.type.caption, c.ink2)
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
                    Modifier.fillMaxWidth().height(56.dp).clip(KelvinShapes.md)
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
        if (note != null) KText(note, Modifier.padding(start = 4.dp), Kelvin.type.summary, if (error != null) c.critical else c.ink2)
    }
}

/** Indeterminate progress: an accent arc turning on a faint track. For a known amount use a bar. */
@Composable
fun KelvinSpinner(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp, color: Color = Kelvin.colors.accent, strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp) {
    val turn = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinner")
    val angle by turn.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "spinner angle")
    val track = Kelvin.colors.hairline
    androidx.compose.foundation.Canvas(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
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

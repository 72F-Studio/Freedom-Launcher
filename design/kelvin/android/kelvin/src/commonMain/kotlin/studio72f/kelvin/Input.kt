package studio72f.kelvin

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A one-time code: one box per digit on the sunken grey, the box the next digit goes into ringed
 * in the accent, digits rolling in as they arrive. It is one text field underneath, so paste and
 * the keyboard's SMS suggestion fill it whole. [onComplete] runs when the last digit lands.
 */
@Composable
fun KelvinCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    error: Boolean = false,
    errorText: String? = null,
    onComplete: (String) -> Unit = {},
) {
  // The boxes, and under them, when the code is wrong, a note with a warning glyph: the red ring
  // alone would leave someone who cannot see red without a sign.
  androidx.compose.foundation.layout.Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    BasicTextField(
        // The cursor is pinned to the end: digits always go into the next box, wherever the
        // field was tapped.
        value = androidx.compose.ui.text.input.TextFieldValue(code, androidx.compose.ui.text.TextRange(code.length)),
        onValueChange = { tv ->
            val v = tv.text
            val digits = v.filter { it.isDigit() }.take(length)
            if (digits != code) {
                haptics.tick()
                onCodeChange(digits)
                if (digits.length == length) { haptics.confirm(); onComplete(digits) }
            }
        },
        modifier = Modifier.fillMaxWidth().semantics {
            this.contentDescription = contentDescription; stateDescription = "${code.length} of $length"
            if (error && errorText != null) error(errorText)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { _ ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until length) {
                    val current = i == code.length
                    val ring by animateColorAsState(
                        when { error -> c.critical; current -> c.accent; else -> Color.Transparent },
                        Kelvin.motion.gentle(), label = "code ring",
                    )
                    Box(
                        Modifier.weight(1f).aspectRatio(0.82f).heightIn(min = 48.dp)
                            .background(c.sunken, KelvinShapes.md)
                            .border(2.dp, ring, KelvinShapes.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        val ch = code.getOrNull(i)
                        if (ch != null) KelvinRollingText(ch.toString(), style = Kelvin.type.numeral.copy(fontSize = Kelvin.type.headline.fontSize))
                    }
                }
            }
        },
    )
    if (error && errorText != null) KelvinNote(errorText, Tone.Critical, Modifier.padding(start = 4.dp))
  }
}

/** One toggle in a [KelvinFormatBar]: its glyph, its name and whether it is on where the cursor is. */
@Immutable
data class FormatAction(val icon: ImageVector, val label: String, val active: Boolean, val onClick: () -> Unit)

/**
 * The formatting bar above the keyboard while composing: a control capsule of toggles (bold,
 * italic, underline, list, quote, link). One that applies where the cursor is sits on an
 * `accent-soft` disc in `on-accent-soft`, so on and off differ in shape as well as colour. The
 * capsule moves as one pebble and scrolls sideways when the toggles outgrow it.
 */
@Composable
fun KelvinFormatBar(actions: List<FormatAction>, modifier: Modifier = Modifier, backdrop: KelvinBackdrop? = null) {
    val c = Kelvin.colors
    KelvinControlCapsule(modifier, backdrop) {
        actions.forEach { a ->
            val fill by animateColorAsState(if (a.active) c.accentSoft else Color.Transparent, Kelvin.motion.gentle(), label = "format")
            Box(
                Modifier.size(44.dp)
                    .kelvinPull(a.onClick, role = Role.Switch, onClickLabel = a.label, shape = CircleShape)
                    .semantics { contentDescription = a.label; stateDescription = if (a.active) "on" else "off" }
                    .background(fill, CircleShape),
                contentAlignment = Alignment.Center,
            ) { KIcon(a.icon, null, size = 20.dp, color = if (a.active) c.onAccentSoft else c.ink, style = IconStyle.of(a.active)) }
        }
    }
}

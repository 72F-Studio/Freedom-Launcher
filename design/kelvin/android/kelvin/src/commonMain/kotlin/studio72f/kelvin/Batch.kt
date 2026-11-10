package studio72f.kelvin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Where one row of a batch stands. Before the person approves, a row is [Included] or [LeftOut] and a tap moves it
 * between the two. After, it shows what happened: [Working], [Done], [NeedsYou] (a tap finishes it) or [Failed].
 */
enum class BatchMark { Included, LeftOut, Working, Done, NeedsYou, Failed }

/**
 * One row of a batch the person reviews and approves ("unsubscribe from these senders"): who it is, a line about
 * what it covers, and a line saying what will happen to it, or what did. It reads like an inbox row, so the list
 * looks like mail and not like a form.
 *
 * The mark at the end is the row's state, never a control of its own: the whole row is the target. A row that is
 * left out fades, so the eye stays on what will change. [note], the third line, carries the state in words; colour
 * only repeats it.
 */
@Composable
fun KelvinBatchRow(
    name: String,
    detail: String,
    note: String,
    mark: BatchMark,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** What a tap does, for TalkBack ("Leave out", "Open"). */
    clickLabel: String? = null,
    /**
     * The note's tone when the mark alone would say the wrong thing: an included row whose note is a caveat
     * ("No link") is not accent news. Null follows the mark.
     */
    noteTone: Tone? = null,
) {
    val c = Kelvin.colors
    val tone = noteTone ?: when (mark) {
        BatchMark.Included -> Tone.Accent
        BatchMark.LeftOut, BatchMark.Working -> Tone.Neutral
        BatchMark.Done -> Tone.Positive
        BatchMark.NeedsYou -> Tone.Caution
        BatchMark.Failed -> Tone.Critical
    }
    val noteInk = when (tone) {
        Tone.Accent -> c.accent.legibleOn(c.group)
        Tone.Neutral -> c.ink3
        Tone.Positive -> c.positive.legibleOn(c.group)
        Tone.Caution -> c.caution.legibleOn(c.group)
        Tone.Critical -> c.critical.legibleOn(c.group)
        Tone.Info -> c.info.legibleOn(c.group)
    }
    val presence by animateFloatAsState(if (mark == BatchMark.LeftOut) 0.45f else 1f, Kelvin.motion.gentle(), label = "in")
    val press = if (onClick != null) Modifier.kelvinPress(onClick, role = Role.Button, onClickLabel = clickLabel) else Modifier
    Row(
        modifier.groupDividerInset(16.dp + 40.dp + 12.dp).fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, $detail"
                stateDescription = note
            }
            .then(press).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KelvinAvatar(name, Modifier.size(40.dp).graphicsLayer { alpha = presence })
        // The note sits under the detail, in the reading column, so the detail keeps the row's width.
        Column(Modifier.weight(1f).graphicsLayer { alpha = presence }) {
            KText(name, style = Kelvin.type.name, maxLines = 1)
            KText(detail, style = Kelvin.type.summary, color = c.ink2, maxLines = 1)
            KText(note, Modifier.padding(top = 2.dp), Kelvin.type.caption, noteInk, maxLines = 1)
        }
        BatchGlyph(mark)
    }
}

@Composable
private fun BatchGlyph(mark: BatchMark) {
    val c = Kelvin.colors
    val size = 22.dp
    when (mark) {
        BatchMark.Included, BatchMark.LeftOut -> {
            val on = mark == BatchMark.Included
            val fill by animateColorAsState(if (on) c.accent else c.group, Kelvin.motion.gentle(), label = "fill")
            Box(
                Modifier.size(size).clip(CircleShape).background(fill)
                    .then(if (on) Modifier else Modifier.border(1.5.dp, c.ink3.copy(alpha = 0.5f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (on) KIcon(KelvinIcons.Check, null, size = 14.dp, color = c.onAccent) }
        }
        BatchMark.Working -> KelvinSpinner(size = 18.dp, strokeWidth = 2.dp)
        BatchMark.Done -> KIcon(KelvinIcons.CircleCheck, null, size = size, color = c.positive)
        BatchMark.NeedsYou -> KIcon(KelvinIcons.ChevronRight, null, size = size, color = c.caution)
        BatchMark.Failed -> KIcon(KelvinIcons.Alert, null, size = size, color = c.critical)
    }
}

/**
 * A change that has been approved but can still be taken back: what is about to happen, an Undo, and a line that
 * drains as the time runs out. [remaining] runs from 1 to 0. When it reaches 0 the caller carries the change out and
 * removes the bar. Sits where the result is read, not floating over other content.
 */
@Composable
fun KelvinUndoBar(
    text: String,
    actionText: String,
    onAction: () -> Unit,
    remaining: Float,
    modifier: Modifier = Modifier,
) {
    val c = Kelvin.colors
    Column(modifier.fillMaxWidth().clip(KelvinShapes.md).background(c.sunken)) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KText(text, Modifier.weight(1f).padding(vertical = 14.dp), Kelvin.type.body, maxLines = 2)
            KText(
                actionText,
                Modifier.kelvinPress(onAction, shape = CapsuleShape).padding(horizontal = 12.dp, vertical = 14.dp),
                Kelvin.type.action,
                c.accent.legibleOn(c.sunken),
            )
        }
        KelvinProgressBar(remaining.coerceIn(0f, 1f), height = 2.dp, track = c.sunken)
    }
}

package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One record an app read out of a person's mail (an invoice, a receipt, a bill), waiting for them to check it. Reading
 * mail is a guess, so a record is never filed silently: the person sees what was read and where from, and confirms,
 * corrects or dismisses it.
 *
 * The card is the group surface, like a stat card. [kind] is its eyebrow ("Invoice"); [amount] the figure, in the
 * figure grammar (₹ and the decimal mark in the accent); [title] who it is from or for; [facts] short ones joined
 * into a line under it (a due date, an invoice number). [source] names the email it came from and is an action:
 * [onOpenSource] opens that email, so every amount can be checked against what was written. [reason] says why it
 * needs checking, led by the info glyph in quiet words. The actions are a tinted Confirm, never Primary, since a list holds several
 * cards and a screen one Primary, then Edit and Dismiss as text buttons. Give [tone] Caution for a record that is
 * due soon and Critical for one that is overdue: the card takes a light lemon or a bright red wash, its eyebrow and
 * currency mark the tone's bright colour, and every line on it is held to 4.5:1 on that fill.
 */
@Composable
fun KelvinRecordCard(
    kind: String,
    amount: String,
    title: String,
    source: String,
    onOpenSource: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    editText: String,
    onEdit: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    facts: List<String> = emptyList(),
    reason: String? = null,
    tone: Tone? = null,
) {
    val c = Kelvin.colors
    val dark = Kelvin.dark
    val toned = tone != null && tone != Tone.Neutral
    // A due record (Caution) is a light lemon wash, an overdue one (Critical) a bright red wash, both over white in
    // light so they stay clean; other tones a pastel of their bright colour.
    val wash = if (toned) recordWash(tone!!, dark) else null
    val fill = remember(wash, c.group, c.raised, dark) {
        when {
            wash == null -> c.group
            dark -> wash.first.copy(alpha = wash.second).compositeOver(c.group)
            else -> wash.first.copy(alpha = wash.second).compositeOver(c.raised)
        }
    }
    val toneInk = if (toned) tone!!.bright().legibleOn(fill) else null
    val quiet = c.ink2.legibleOn(fill)
    Column(
        modifier.fillMaxWidth().background(fill, KelvinShapes.lg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KelvinEyebrow(kind, color = toneInk ?: quiet)
        KelvinFigure(
            amount,
            Modifier.padding(top = 4.dp),
            mark = toneInk ?: c.figureAccent,
            unit = toneInk ?: c.figureAccent,
            unitWord = toneInk ?: quiet,
        )
        KText(title, style = Kelvin.type.name, maxLines = 2)
        if (facts.isNotEmpty()) KText(facts.joinToString(FACT_GAP), style = Kelvin.type.summary, color = quiet)
        RecordSource(source, onOpenSource, fill)
        if (reason != null) RecordReason(reason, fill)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KelvinButton(confirmText, onConfirm, kind = ButtonKind.Tinted, icon = KelvinIcons.Check)
            KelvinButton(editText, onEdit, kind = ButtonKind.Text)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            KelvinButton(dismissText, onDismiss, kind = ButtonKind.Text)
        }
    }
}

/**
 * Why a record needs checking, led by the info glyph so it reads as information; the words stay quiet `ink-2`, since
 * the card already carries the accent in its source and actions and a third coloured line would shout.
 */
@Composable
private fun RecordReason(reason: String, surface: Color) {
    val c = Kelvin.colors
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KIcon(KelvinIcons.Info, null, Modifier.padding(top = 1.dp), size = 16.dp, color = c.info.legibleOn(surface))
        KText(reason, style = Kelvin.type.summary, color = c.ink2.legibleOn(surface))
    }
}

/** The email a record came from, as a quiet action: the mail glyph and its name in the accent, opening it. */
@Composable
private fun RecordSource(source: String, onOpen: () -> Unit, surface: Color) {
    val ink = Kelvin.colors.accent.legibleOn(surface)
    Row(
        Modifier
            .padding(top = 4.dp)
            .heightIn(min = 32.dp)
            .kelvinPress(onOpen, role = Role.Button, shape = KelvinShapes.pill)
            .semantics(mergeDescendants = true) { contentDescription = source },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(KelvinIcons.Mail, null, size = 16.dp, color = ink)
        KText(source, style = Kelvin.type.label, color = ink, maxLines = 1)
    }
}

/**
 * One record in a ledger: [title] (who), [facts] under it (what and when), its [status] as a pill in [statusTone],
 * and [amount] at the end as a row figure. On a phone it sits in a `KelvinGroup`; on a desk it is a line in a pane.
 * A click opens the record. [selected] washes it, as a chosen mail line is. A record with no amount (a build that
 * failed, a review waiting) passes null and the pill ends the line.
 */
@Composable
fun KelvinRecordLine(
    title: String,
    amount: String?,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    facts: List<String> = emptyList(),
    statusTone: Tone = Tone.Neutral,
    selected: Boolean = false,
) {
    val c = Kelvin.colors
    val fill = if (selected) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(fill, KelvinShapes.md)
            .kelvinPress(onClick, shape = KelvinShapes.md)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(title, style = Kelvin.type.rowTitle, maxLines = 1)
            if (facts.isNotEmpty()) KText(facts.joinToString(FACT_GAP), style = Kelvin.type.summary, color = c.ink2, maxLines = 1)
        }
        KelvinPill(status, tone = statusTone)
        if (amount != null) KelvinFigure(amount, style = Kelvin.type.figure, rolling = false)
    }
}

private const val FACT_GAP = "  ·  "

/** A record card's wash: the colour and how much of it the card keeps over its surface. */
@Composable
private fun recordWash(tone: Tone, dark: Boolean): Pair<Color, Float> = when (tone) {
    // A light lemon (near the paint GLD609): a pale cream like GLD601, but yellower. Lemon leans green, which turns
    // olive on a dark grey, so dark takes a warmer yellow, thinner.
    Tone.Caution -> if (dark) LEMON_DARK to 0.12f else LEMON to 0.16f
    // A bright red, washed to the same lightness as the lemon so neither card shouts over the other.
    Tone.Critical -> if (dark) Kelvin.colors.critical to 0.12f else Kelvin.colors.critical to 0.10f
    else -> tone.bright() to if (dark) 0.26f else 0.24f
}

private val LEMON = Color(0xFFFAE84C)
private val LEMON_DARK = Color(0xFFFFC93D)

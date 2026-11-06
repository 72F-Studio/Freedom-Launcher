package studio72f.kelvin

import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Grey header above a group: sentence case, marked as a heading. */
@Composable
fun KelvinSectionHeader(text: String, modifier: Modifier = Modifier) {
    KText(
        text,
        modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        Kelvin.type.section,
        Kelvin.colors.ink2,
    )
}

/**
 * A group on the canvas, inset by the gutter: one continuous `group` well with 28dp continuous
 * corners holding its children (a [KelvinRow], a control, a picker). Between children the group
 * draws a divider as ColorOS does, but barely there: one physical pixel of `ink` at 6% (8% in
 * dark), inset to where the row's text starts ([groupDividerInset]; 16dp if the child does not
 * say) and stopping 16dp short of the end. [contentPadding] insets each child,
 * for children holding text and controls rather than full-width rows; [gutter] is the inset from
 * the screen edges (0 where the caller already pads).
 */
@Composable
fun KelvinGroup(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = Kelvin.colors.group
    val line = Kelvin.colors.ink.copy(alpha = if (Kelvin.dark) 0.08f else 0.06f)
    val dividers = remember { GroupDividers() }
    CompositionLocalProvider(LocalInKelvinGroup provides true) {
        Layout(
            content = { GroupScope.content() },
            modifier = modifier.padding(horizontal = gutter).fillMaxWidth().drawBehind {
                drawOutline(WellShape.createOutline(size, layoutDirection, this), fill)
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                val end = 16.dp.toPx()
                for (i in 0 until dividers.count) {
                    val y = dividers.ys[i] + 0.5f
                    val start = dividers.insets[i]
                    val a = if (rtl) size.width - start else start
                    val b = if (rtl) end else size.width - end
                    // One physical pixel, whatever the density.
                    drawLine(line, Offset(a, y), Offset(b, y), 1f)
                }
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val pad = contentPadding.roundToPx()
            val inset = WellInset.roundToPx()
            val inner = (w - 2 * pad).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints(minWidth = inner, maxWidth = inner)) }
            // A spacer or hairline left over from a single-card layout is not a row of its own.
            val thin = 6.dp.roundToPx()
            val shown = placeables.filter { it.height > thin }
            val height = shown.sumOf { it.height + 2 * pad } + 2 * inset
            layout(w, height) {
                var y = inset
                var n = 0
                dividers.reset(shown.size)
                placeables.forEachIndexed { index, p ->
                    if (p.height <= thin) return@forEachIndexed
                    if (n > 0) {
                        val dividerInset = (measurables[index].layoutId as? GroupDividerInset)?.inset ?: 16.dp
                        dividers.add(y.toFloat(), dividerInset.toPx())
                    }
                    // Only the first and last rows meet the well's corners; a press stays inside them.
                    val shape = SegmentShape(if (n == 0) WellRadius - WellInset else 0.dp, if (n == shown.lastIndex) WellRadius - WellInset else 0.dp)
                    val tile = p.height + 2 * pad
                    if (pad == 0) p.placeWithLayer(0, y) { this.shape = shape; clip = true } else p.place(pad, y + pad)
                    y += tile
                    n++
                }
            }
        }
    }
}

// One continuous well per group, as on HyperOS 4.
private val WellRadius = 28.dp
private val WellShape = ContinuousShape(WellRadius)
private val WellInset = 4.dp

/** True inside a [KelvinGroup], which draws the dividers between its children itself. */
val LocalInKelvinGroup = androidx.compose.runtime.staticCompositionLocalOf { false }

private data class GroupDividerInset(val inset: androidx.compose.ui.unit.Dp)

/**
 * Inside a [KelvinGroup]: where this child's text starts, so the divider above it is inset to
 * the text rather than running under an icon or avatar.
 */
fun Modifier.groupDividerInset(inset: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.layoutId(GroupDividerInset(inset)))

private class GroupDividers {
    var count = 0
    var ys = FloatArray(0)
    var insets = FloatArray(0)
    fun reset(n: Int) {
        count = 0
        if (ys.size < n) { ys = FloatArray(n); insets = FloatArray(n) }
    }
    fun add(y: Float, inset: Float) { ys[count] = y; insets[count] = inset; count++ }
}

// Children are stacked by the group itself; weight and align have no meaning in it.
private object GroupScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    override fun Modifier.alignBy(alignmentLine: androidx.compose.ui.layout.VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (androidx.compose.ui.layout.Measured) -> Int): Modifier = this
}

/**
 * One row in a group: optional colour icon tile, title, optional summary, then a trailing value,
 * control or chevron. [divider] draws the hairline above the row, inset to the text.
 */
@Composable
fun KelvinRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    tile: Color = KelvinTiles.Blue,
    value: String? = null,
    chevron: Boolean = false,
    danger: Boolean = false,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Kelvin.colors
    // In a group the gap between cards does a divider's job.
    val inGroup = LocalInKelvinGroup.current
    val insetStart = if (icon != null) 60.dp else 16.dp
    Row(
        modifier
            .groupDividerInset(insetStart)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.kelvinPress(onClick, role = Role.Button) else Modifier)
            .drawBehind {
                if (divider && !inGroup) drawLine(c.hairline, Offset(insetStart.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            KelvinGlassTile(icon, tile)
        }
        Column(Modifier.weight(1f)) {
            KText(title, style = if (danger) Kelvin.type.rowTitle.copy(fontWeight = FontWeight.SemiBold) else Kelvin.type.rowTitle, color = if (danger) c.critical.legibleOn(c.group) else c.ink)
            if (summary != null) KText(summary, style = Kelvin.type.summary, color = c.ink2)
        }
        if (value != null) {
            if (value.any { it.isDigit() }) KelvinFigure(value, style = Kelvin.type.figure, color = c.ink2)
            else KText(value, style = Kelvin.type.body, color = c.ink2, maxLines = 1)
        }
        // The row's title names its control for TalkBack: "Reduce motion, switch, off".
        if (trailing != null) CompositionLocalProvider(LocalControlLabel provides title) { trailing() }
        if (chevron) KIcon(KelvinIcons.ChevronRight, null, size = 20.dp, color = c.ink3)
    }
}

/**
 * Media tile with the Kelvin notch: the cover's bottom-end corner is cut away around a round
 * glass [badge] that refracts the cover beneath it. [cover] draws the image.
 */
@Composable
fun KelvinTile(
    name: String,
    count: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: ImageVector? = null,
    badgeLabel: String? = null,
    cover: @Composable BoxScope.() -> Unit,
) {
    val c = Kelvin.colors
    val local = rememberKelvinBackdrop()
    Column(modifier.kelvinPress(onClick, shape = KelvinShapes.tile)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val shape = if (badge != null) NotchedShape(22.dp, 44.dp) else KelvinShapes.tile
            Box(Modifier.fillMaxSize().clip(shape).kelvinBackdrop(local), content = cover)
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(44.dp).glass(local, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { KIcon(badge, badgeLabel, size = 22.dp) }
            }
        }
        KText(name, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), Kelvin.type.name, maxLines = 1)
        if (count != null) KelvinFigure(count, Modifier.padding(horizontal = 4.dp), Kelvin.type.figure.copy(fontSize = Kelvin.type.summary.fontSize), c.ink2, unit = c.ink2, rolling = false)
    }
}

/**
 * Cards side by side ([KelvinStatCard]s, or anything that fills its height) at one shared height:
 * the tallest card's. Give each child `Modifier.weight(1f)`. Ragged rows are for photo and video
 * timelines only; in an app's own cards they read as a mistake.
 */
@Composable
fun KelvinStatRow(
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = Row(
    modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(gap),
    content = content,
)

/**
 * A glanceable figure on a group card: label, big value (a [KelvinFigure]: light numbers, accent
 * marks, small units), optional detail and extra content. In a [KelvinStatRow] it takes the
 * row's height, label at the top and detail pinned to the bottom, so neighbouring cards line up
 * at both edges. A [hero] value is set larger, its numbers warming into the accent.
 *
 * Given a [tone], the card is filled with the tone's soft colour, its label becomes an eyebrow and
 * its value takes the tone's ink: for figures that are categories of their own (income and
 * spending, in and out), never just to decorate.
 */
@Composable
fun KelvinStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    detail: String? = null,
    tone: Tone? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Kelvin.colors
    val t = tone?.colors()
    Column(
        modifier.fillMaxHeight().background(t?.fill ?: c.group, KelvinShapes.lg).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (t != null) 8.dp else 2.dp)) {
            if (t != null) KelvinEyebrow(label, color = t.ink)
            else KText(label, style = Kelvin.type.section, color = c.ink2)
            KelvinFigure(
                value,
                style = if (hero) Kelvin.type.hero else Kelvin.type.numeral,
                color = t?.ink ?: c.ink,
                heat = hero && t == null,
                mark = t?.ink ?: c.figureAccent,
                unit = t?.ink?.copy(alpha = 0.72f) ?: c.figureAccent,
                unitWord = t?.ink?.copy(alpha = 0.72f) ?: c.ink2,
            )
            extra()
        }
        if (detail != null) KText(detail, Modifier.padding(top = 4.dp), Kelvin.type.summary, c.ink2)
    }
}

/** Avatar: initials on one of the accent dial's soft colours, picked from the name. */
@Composable
fun KelvinAvatar(name: String, modifier: Modifier = Modifier) {
    val dark = Kelvin.dark
    val family = AccentPreset.entries[(name.hashCode() and 0x7fffffff) % AccentPreset.entries.size].let { if (dark) it.dark else it.light }
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(44.dp).clip(CircleShape).background(family.soft), contentAlignment = Alignment.Center) {
        // Initials are sized to the avatar, not the text setting: they are a picture of a name, which
        // TalkBack reads in full from the row.
        val initialsSize = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toSp() }
        KText(initials, style = Kelvin.type.name.copy(fontSize = initialsSize, lineHeight = initialsSize), color = family.onSoft.legibleOn(family.soft), maxLines = 1)
    }
}

/** Inbox row: avatar, sender, time, subject, two-line preview. Unread rows are heavier and dotted. */
@Composable
fun KelvinMessageRow(
    sender: String,
    time: String,
    subject: String,
    preview: String,
    unread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mark: UnreadMark = UnreadMark.Trailing,
    /** Marks about the message, at the end of the subject line. */
    attachment: Boolean = false,
    important: Boolean = false,
    starred: Boolean = false,
    /** Makes the star a toggle (a 48dp target); give it [starLabel] for TalkBack. */
    onStar: (() -> Unit)? = null,
    starLabel: String? = null,
    /** A mark about the sender, on the avatar's corner: [SenderBadge.Verified], [SenderBadge.Vip] or an app's own. */
    badge: SenderBadge? = null,
) {
    val c = Kelvin.colors
    val heavy = if (unread) FontWeight.SemiBold else FontWeight.Normal
    val inline = mark == UnreadMark.Inline && unread
    // In a group the divider starts where the text does: past the avatar, and past the dot too
    // on an Inline unread row.
    val textStart = 16.dp + 44.dp + 12.dp + if (inline) 16.dp else 0.dp
    Row(
        modifier.groupDividerInset(textStart).fillMaxWidth().kelvinPress(onClick).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Inline: only an unread row makes room for its dot, so read rows sit flush.
        if (inline) Box(Modifier.size(4.dp, 44.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.requiredSize(8.dp).clip(CircleShape).background(c.accent))
        }
        Box {
            KelvinAvatar(sender)
            if (badge != null) Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(20.dp)
                    .background(c.group, CircleShape).padding(2.dp).background(badge.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) { KIcon(badge.icon, badge.label, size = 11.dp, color = Color.White) }
            // Avatar: the dot sits on the avatar's top-end corner, ringed in the group's grey.
            if (unread && mark == UnreadMark.Avatar) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(14.dp)
                    .background(c.group, CircleShape).padding(3.dp).background(c.accent, CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(sender, Modifier.weight(1f), Kelvin.type.name.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
                // Times follow the figure grammar: an accent colon when unread, a quiet one when read.
                val timeStyle = Kelvin.type.figure.copy(fontSize = Kelvin.type.summary.fontSize, fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium)
                if (time.any { it.isDigit() }) KelvinFigure(time, style = timeStyle, color = if (unread) c.ink else c.ink2, mark = if (unread) c.figureAccent else c.ink3, unit = c.ink2, rolling = false)
                else KText(time, style = timeStyle, color = if (unread) c.accent else c.ink2)
                // Trailing: the dot sits after the time.
                if (unread && mark == UnreadMark.Trailing) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(c.accent))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(subject, Modifier.weight(1f), Kelvin.type.body.copy(fontWeight = heavy), maxLines = 1)
                // Marks about the message sit at the end of the subject line: what it carries,
                // whether it matters, whether it is kept.
                if (attachment) KIcon(KelvinIcons.Paperclip, null, Modifier.padding(start = 6.dp), size = 16.dp, color = c.ink2)
                if (important) KIcon(KelvinIcons.Flag, null, Modifier.padding(start = 6.dp), size = 16.dp, color = c.critical.legibleOn(c.group, 3f))
                val starInk = if (starred) c.caution.legibleOn(c.group, 3f) else c.ink3
                if (onStar != null) Box(
                    Modifier.size(32.dp).kelvinPull(onStar, role = androidx.compose.ui.semantics.Role.Switch, onClickLabel = starLabel)
                        .semantics { stateDescription = if (starred) "on" else "off"; if (starLabel != null) contentDescription = starLabel },
                    contentAlignment = Alignment.Center,
                ) { KIcon(KelvinIcons.Star, null, size = 18.dp, color = starInk, style = if (starred) IconStyle.Filled else IconStyle.Outline) }
                else if (starred) KIcon(KelvinIcons.Star, null, Modifier.padding(start = 6.dp), size = 16.dp, color = starInk, style = IconStyle.Filled)
            }
            KText(preview, style = Kelvin.type.summary, color = c.ink2, maxLines = 2)
        }
    }
}

/**
 * The name of the control a container holds, for TalkBack: [KelvinRow] provides its title, so a
 * switch, slider or segmented control inside it is announced by name without repeating it.
 */
val LocalControlLabel = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** Where a [KelvinMessageRow] shows that it is unread. */
enum class UnreadMark {
    /** A dot on the avatar's corner. Rows all start at the same place. */
    Avatar,
    /** A dot after the time, at the end of the sender's line. */
    Trailing,
    /** A dot before the avatar on unread rows only; read rows sit flush to the start. */
    Inline,
}

/** A mark about a message's sender, on the avatar's corner. */
@androidx.compose.runtime.Immutable
data class SenderBadge(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val label: String) {
    companion object {
        /** A verified sender: a rosette with a tick, in `info`. */
        @Composable fun Verified(label: String) = SenderBadge(KelvinIcons.Verified, Kelvin.colors.info, label)
        /** Someone the person marked as important to them: a star, in the accent. */
        @Composable fun Vip(label: String) = SenderBadge(KelvinIcons.Star, Kelvin.colors.accent, label)
    }
}

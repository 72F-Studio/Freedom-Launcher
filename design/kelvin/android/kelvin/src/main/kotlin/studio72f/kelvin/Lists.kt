package studio72f.kelvin

import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Grey header above a group: sentence case, marked as a heading. */
@Composable
fun KelvinSectionHeader(text: String, modifier: Modifier = Modifier) {
    KText(
        text,
        modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        Kelvin.type.summary.copy(fontWeight = FontWeight.SemiBold),
        Kelvin.colors.ink2,
    )
}

/**
 * A group on the canvas, inset by the gutter: each child (a [KelvinRow], a control, a picker) on
 * its own white card, 2dp apart, 24dp outer and 6dp inner corners. [contentPadding] insets each
 * child within its card, for cards holding text and controls rather than full-width rows;
 * [gutter] is the inset from the screen edges (0 where the caller already pads).
 */
@Composable
fun KelvinGroup(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = Kelvin.colors.group
    val tiles = remember { GroupTiles() }
    CompositionLocalProvider(LocalInKelvinGroup provides true) {
        Layout(
            content = { GroupScope.content() },
            modifier = modifier.padding(horizontal = gutter).fillMaxWidth().drawBehind {
                for (i in 0 until tiles.count) {
                    val top = tiles.tops[i]
                    val h = tiles.heights[i]
                    translate(top = top) { drawOutline(tiles.shapes[i].createOutline(androidx.compose.ui.geometry.Size(size.width, h), layoutDirection, this), fill) }
                }
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val gap = GroupGap.roundToPx()
            val pad = contentPadding.roundToPx()
            val inner = (w - 2 * pad).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints(minWidth = inner, maxWidth = inner)) }
            // A spacer or hairline left over from a single-card layout is not a card of its own.
            val thin = 6.dp.roundToPx()
            val shown = placeables.filter { it.height > thin }
            val height = shown.sumOf { it.height + 2 * pad } + gap * (shown.size - 1).coerceAtLeast(0)
            layout(w, height) {
                tiles.reset(shown.size)
                var y = 0
                var n = 0
                placeables.forEach { p ->
                    // Not placed at all: a divider would otherwise draw across the next card.
                    if (p.height <= thin) return@forEach
                    val shape = SegmentShape(if (n == 0) GroupOuter else GroupInner, if (n == shown.lastIndex) GroupOuter else GroupInner)
                    val tile = p.height + 2 * pad
                    tiles.set(n, y.toFloat(), tile.toFloat(), shape)
                    // Full-bleed rows are clipped to their card so a press stays inside its corners.
                    if (pad == 0) p.placeWithLayer(0, y) { this.shape = shape; clip = true } else p.place(pad, y + pad)
                    y += tile + gap
                    n++
                }
            }
        }
    }
}

// Each child of a group is its own card: the page shows through a fine gap between them, the
// group's outer corners stay round and the cards meet with small corners.
private val GroupGap = 2.dp
private val GroupOuter = 24.dp
private val GroupInner = 6.dp

/** True inside a [KelvinGroup], where cards are separated by gaps rather than hairlines. */
val LocalInKelvinGroup = androidx.compose.runtime.staticCompositionLocalOf { false }

private class GroupTiles {
    var count = 0
    var tops = FloatArray(0)
    var heights = FloatArray(0)
    val shapes = mutableListOf<SegmentShape>()
    fun reset(n: Int) {
        count = n
        if (tops.size < n) { tops = FloatArray(n); heights = FloatArray(n) }
        while (shapes.size < n) shapes.add(SegmentShape(GroupOuter, GroupOuter))
    }
    fun set(i: Int, top: Float, height: Float, shape: SegmentShape) { tops[i] = top; heights[i] = height; shapes[i] = shape }
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
            Box(Modifier.size(32.dp).background(tile, KelvinShapes.xs), contentAlignment = Alignment.Center) {
                KIcon(icon, null, size = 20.dp, color = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            KText(title, style = Kelvin.type.rowTitle, color = if (danger) c.critical else c.ink)
            if (summary != null) KText(summary, style = Kelvin.type.summary, color = c.ink2)
        }
        if (value != null) KText(value, style = Kelvin.type.body, color = c.ink2, maxLines = 1)
        trailing?.invoke()
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
        KText(name, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), Kelvin.type.rowTitle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        if (count != null) KText(count, Modifier.padding(horizontal = 4.dp), Kelvin.type.summary, c.ink2, maxLines = 1)
    }
}

/** A glanceable figure on a group card: label, big value, optional detail and extra content. */
@Composable
fun KelvinStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    detail: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Kelvin.colors
    Column(modifier.background(c.group, KelvinShapes.lg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KText(label, style = Kelvin.type.summary.copy(fontWeight = FontWeight.SemiBold), color = c.ink2)
        KText(value, style = if (hero) Kelvin.type.hero else Kelvin.type.numeral)
        extra()
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
        KText(initials, style = Kelvin.type.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = family.onSoft)
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
) {
    val c = Kelvin.colors
    val heavy = if (unread) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier.fillMaxWidth().kelvinPress(onClick).padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp, 44.dp), contentAlignment = Alignment.Center) {
            if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
        }
        KelvinAvatar(sender)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KText(sender, Modifier.weight(1f), Kelvin.type.rowTitle.copy(fontWeight = heavy), maxLines = 1)
                KText(time, style = Kelvin.type.summary.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal), color = if (unread) c.accent else c.ink2)
            }
            KText(subject, style = Kelvin.type.body.copy(fontWeight = heavy), maxLines = 1)
            KText(preview, style = Kelvin.type.summary, color = c.ink2, maxLines = 2)
        }
    }
}

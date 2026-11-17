package studio72f.kelvin

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A desk-sized window: a narrow rail of icons where a phone has a drawer, and lists whose items are one line each,
// because a wide window has the room to say who, what and when side by side. Nothing here is outlined or filled
// with a solid colour: the rail is the group grey, a chosen item the accent's wash.

/** The width of [KelvinRail]. */
val KelvinRailWidth = 64.dp
private val RailItemSize = 44.dp
private val LineHeight = 48.dp
private val LineLead = 32.dp
private val LineWho = 190.dp
private val LineWhoCompact = 132.dp

/**
 * The rail down a desktop window's leading edge: the app's places as icons ([KelvinRailItem]), its one action at
 * the top, the account at the foot (put a weighted spacer before it). It is the group grey from top to bottom and
 * never an accent or brand colour: the colour in a window belongs to its content.
 *
 * [expanded] widens it to [KelvinRailExpandedWidth] and names every place beside its icon, for someone who does not
 * know the icons yet; the width eases between the two. Give the person a way to choose, [KelvinRailToggle] at the
 * top, and remember their choice. The rail stays in every window size: on the narrowest,
 * lay the expanded rail over the content (with a scrim that closes it) rather than squeezing the content beside it.
 */
@Composable
fun KelvinRail(modifier: Modifier = Modifier, expanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val width by animateDpAsState(
        if (expanded) KelvinRailExpandedWidth else KelvinRailWidth,
        tween(RailEaseMillis, easing = FastOutSlowInEasing),
    )
    CompositionLocalProvider(LocalKelvinRailExpanded provides expanded) {
        Column(
            modifier.width(width).fillMaxHeight().background(Kelvin.colors.group)
                .padding(vertical = 12.dp, horizontal = if (expanded) 10.dp else 0.dp),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/**
 * The button at the top of a [KelvinRail] that expands it to name its places, and collapses it again: the menu icon
 * alone, in the same place in both states, as mail and office apps on the web have it. Its words ([expandLabel],
 * [collapseLabel]) are never shown beside it; they are what a screen reader says and what the tooltip shows.
 */
@Composable
fun KelvinRailToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    expandLabel: String,
    collapseLabel: String,
    modifier: Modifier = Modifier,
) {
    val label = if (expanded) collapseLabel else expandLabel
    KelvinTooltip(label, modifier) {
        Box(
            Modifier.size(RailItemSize).clip(KelvinShapes.md).kelvinHover()
                .kelvinPress(onClick, role = Role.Button, shape = KelvinShapes.md)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { KIcon(KelvinIcons.Menu, null, color = Kelvin.colors.ink2) }
    }
}

/** Whether the [KelvinRail] around is expanded, so its items name themselves. */
private val LocalKelvinRailExpanded = compositionLocalOf { false }

/**
 * One icon on a [KelvinRail]. Collapsed it has no visible name, so [label] is what a screen reader says; expanded,
 * [label] stands beside the icon. The [selected] place sits on the accent's wash, as a chosen folder does; [primary]
 * is the rail's one action (write, add), filled with the accent. [dot] marks a place with something new in it.
 * [dropTarget] is set while something dragged ([KelvinDragChip]) is over this place: it takes a deeper wash, so the
 * person sees where it will land.
 */
@Composable
fun KelvinRailItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    dot: Boolean = false,
    dropTarget: Boolean = false,
    selectedLabel: String = kelvinString(KelvinString.Selected),
) {
    val c = Kelvin.colors
    val fill = when {
        dropTarget -> c.accent.copy(alpha = DropWash)
        primary -> c.accent
        selected -> c.accent.copy(alpha = KelvinSelectedWash)
        else -> Color.Transparent
    }
    val ink = when {
        primary -> c.onAccent
        selected || dropTarget -> c.onAccentSoft
        else -> c.ink2
    }
    val words = when {
        primary -> c.onAccent
        selected || dropTarget -> c.onAccentSoft
        else -> c.ink
    }
    RailPlace(label, onClick, modifier, fill, words, selected, selectedLabel, dot) {
        KIcon(icon, null, color = ink, style = IconStyle.of(selected))
    }
}

/**
 * One account at a [KelvinRail]'s foot, for an app signed in to several: its avatar ([KelvinAvatar], from [name]),
 * on the accent's wash while it is the one shown, with [dot] while it has something new. [label] (the account's
 * address) is what a screen reader says, and what an expanded rail shows beside the avatar. Stack one per account
 * above the foot; the account shown opens its menu when clicked again, through [onClick].
 */
@Composable
fun KelvinRailAccount(
    name: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dot: Boolean = false,
    selectedLabel: String = kelvinString(KelvinString.Selected),
) {
    val c = Kelvin.colors
    val fill = if (selected) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent
    RailPlace(label, onClick, modifier, fill, if (selected) c.onAccentSoft else c.ink, selected, selectedLabel, dot) {
        KelvinAvatar(name, Modifier.size(RailAvatarSize))
    }
}

/** A place on the rail: a square holding [mark] when the rail is collapsed, a full-width row naming it when expanded. */
@Composable
private fun RailPlace(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    fill: Color,
    words: Color,
    selected: Boolean,
    selectedLabel: String,
    dot: Boolean,
    mark: @Composable () -> Unit,
) {
    val c = Kelvin.colors
    val expanded = LocalKelvinRailExpanded.current
    val shape = Modifier.height(RailItemSize).then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(RailItemSize))
    Box(
        modifier.then(shape).clip(KelvinShapes.md).background(fill).kelvinHover()
            .kelvinPress(onClick, role = Role.Button, shape = KelvinShapes.md)
            .semantics {
                contentDescription = label
                this.selected = selected
                if (selected) stateDescription = selectedLabel
            },
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
    ) {
        if (expanded) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(RailMarkSize), contentAlignment = Alignment.Center) { mark() }
                KText(
                    label,
                    Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
                    style = Kelvin.type.rowTitle,
                    color = words,
                    maxLines = 1,
                )
                if (dot) Box(Modifier.padding(end = 6.dp).size(8.dp).background(c.accent, CircleShape))
            }
        } else {
            mark()
            if (dot) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).background(c.accent, CircleShape))
        }
    }
}

/** How large an account's avatar is on the rail: smaller than its square, so the wash shows round it. */
private val RailAvatarSize = 32.dp

/** The square a rail place's icon or avatar sits in, the same expanded or not, so nothing jumps. */
private val RailMarkSize = 32.dp

/** The width of an expanded [KelvinRail]: room for a folder's or an address's name. */
val KelvinRailExpandedWidth = 232.dp

private const val RailEaseMillis = 200

/** How deep a place's wash is while something dragged is over it: deeper than the chosen place's. */
private const val DropWash = 0.38f

/**
 * A message on one line, for a wide list: an unread dot, the avatar, who, then the subject running straight into
 * how it begins, an optional [tag] and when. An unread line is dotted and heavier; the [selected] one sits on the
 * accent's wash. [compact] is the same line in a narrower list (an email open beside it): a narrower name and no tag.
 * Lines of every kind in one list ([KelvinBundleLine]) keep these columns. With [actions], the pointer over the line
 * trades the tag and time for those actions (archive, pin, snooze), as small icons named on hover.
 */
@Composable
fun KelvinMailLine(
    sender: String,
    time: String,
    subject: String,
    preview: String,
    unread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    compact: Boolean = false,
    tag: MessageTag? = null,
    selectedLabel: String = kelvinString(KelvinString.Selected),
    actions: List<KelvinAction> = emptyList(),
) {
    val c = Kelvin.colors
    val fill = if (selected) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent
    val (hover, hovered) = rememberHover()
    val acting = hovered && actions.isNotEmpty()
    Line(fill, onClick, modifier.hoverActions(hover).semantics { this.selected = selected; if (selected) stateDescription = selectedLabel }) {
        Box(Modifier.size(8.dp).background(if (unread) c.accent else Color.Transparent, CircleShape))
        KelvinAvatar(sender, Modifier.size(LineLead))
        KText(sender, Modifier.width(if (compact) LineWhoCompact else LineWho), if (unread) Kelvin.type.name else Kelvin.type.rowTitle, maxLines = 1)
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal)) { append(subject) }
                withStyle(SpanStyle(color = c.ink2, fontSize = Kelvin.type.summary.fontSize)) { append("   "); append(preview) }
            },
            Modifier.weight(1f),
            style = Kelvin.type.rowTitle.copy(color = c.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (acting) {
            KelvinRowActions(actions)
        } else {
            if (tag != null && !compact) KelvinPill(listOfNotNull(tag.text, tag.figure).joinToString(" "), tone = tag.tone, icon = tag.icon)
            KText(time, style = Kelvin.type.figure, color = if (unread) c.ink else c.ink2, maxLines = 1)
        }
    }
}

/**
 * Messages gathered into one line at the head of a list (bills, updates, promotions): the bundle's [icon] and
 * [title], how many it holds, and [senders], who is inside, already joined into one string. It is the group grey so
 * it reads as a container, not a message. A click opens it in place; say so with [expanded]. [unread] dots it when
 * something inside is new.
 */
@Composable
fun KelvinBundleLine(
    title: String,
    icon: ImageVector,
    count: Int,
    senders: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unread: Boolean = false,
    expanded: Boolean = false,
    compact: Boolean = false,
) {
    val c = Kelvin.colors
    Line(c.group, onClick, modifier.semantics { stateDescription = if (expanded) "expanded" else "collapsed" }) {
        Box(Modifier.size(8.dp).background(if (unread) c.accent else Color.Transparent, CircleShape))
        Box(Modifier.size(LineLead), contentAlignment = Alignment.Center) { KIcon(icon, null, color = c.ink2) }
        Row(Modifier.width(if (compact) LineWhoCompact else LineWho), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KText(title, style = Kelvin.type.name, maxLines = 1)
            KText(count.toString(), style = Kelvin.type.figure, color = c.ink2)
        }
        KText(senders, Modifier.weight(1f), Kelvin.type.summary, c.ink2, maxLines = 1)
        KIcon(if (expanded) KelvinIcons.ChevronUp else KelvinIcons.ChevronRight, null, size = 18.dp, color = c.ink2)
    }
}

/** A heading between the lines of a wide list (Today, Earlier this week), starting where the lines do. */
@Composable
fun KelvinLineHeading(text: String, modifier: Modifier = Modifier, top: Dp = 16.dp) {
    KText(text, modifier.padding(start = 8.dp, top = top, bottom = 6.dp).semantics { heading() }, Kelvin.type.section, Kelvin.colors.ink2)
}

/**
 * What a click brings into a desktop window arrives: it fades in over a short slide (from the trailing side by
 * default; pass [fromY] for something that opens downwards), and again whenever [key] changes. It moves nothing
 * else and changes no layout. A desktop window otherwise holds still, so this is on unless the app asks for
 * reduced motion; pass [enabled] to decide yourself.
 */
fun Modifier.deskArrive(key: Any?, fromX: Dp = 12.dp, fromY: Dp = 0.dp, enabled: Boolean? = null): Modifier = composed {
    if (!(enabled ?: !Kelvin.reduceMotion)) return@composed this
    val shown = remember { Animatable(0f) }
    LaunchedEffect(key) {
        shown.snapTo(0f)
        shown.animateTo(1f, tween(DeskArriveMillis, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = shown.value
        translationX = (1f - shown.value) * fromX.toPx()
        translationY = (1f - shown.value) * fromY.toPx()
    }
}

private const val DeskArriveMillis = 180

/** The shape every line shares, so bundles and messages keep the same columns. */
@Composable
private fun Line(fill: Color, onClick: () -> Unit, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(LineHeight).clip(KelvinShapes.md).background(fill).kelvinHover()
            .kelvinPress(onClick, role = Role.Button, shape = KelvinShapes.md).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

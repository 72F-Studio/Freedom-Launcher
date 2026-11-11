package studio72f.kelvin

import androidx.compose.animation.core.Animatable
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
 */
@Composable
fun KelvinRail(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.width(KelvinRailWidth).fillMaxHeight().background(Kelvin.colors.group).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * One icon on a [KelvinRail]. It has no visible name, so [label] is what a screen reader says. The [selected] place
 * sits on the accent's wash, as a chosen folder does; [primary] is the rail's one action (write, add), filled with
 * the accent. [dot] marks a place with something new in it.
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
    selectedLabel: String = kelvinString(KelvinString.Selected),
) {
    val c = Kelvin.colors
    val fill = when {
        primary -> c.accent
        selected -> c.accent.copy(alpha = KelvinSelectedWash)
        else -> Color.Transparent
    }
    val ink = when {
        primary -> c.onAccent
        selected -> c.onAccentSoft
        else -> c.ink2
    }
    Box(
        modifier.size(RailItemSize).clip(KelvinShapes.md).background(fill).kelvinHover()
            .kelvinPress(onClick, role = Role.Button, shape = KelvinShapes.md)
            .semantics {
                contentDescription = label
                this.selected = selected
                if (selected) stateDescription = selectedLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        KIcon(icon, null, color = ink, style = IconStyle.of(selected))
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).background(c.accent, CircleShape))
    }
}

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

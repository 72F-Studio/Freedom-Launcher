package studio72f.kelvin.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import studio72f.kelvin.Accent
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinFinish
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinShapes
import studio72f.kelvin.KelvinTheme
import studio72f.kelvin.IconStyle
import studio72f.kelvin.kelvinPress

/*
 * Kelvin on a watch. The rules are the phone's, cut to a glance: a black page (the screen is the
 * bezel), `group` wells for anything pressable, Onest for names and figures, Montserrat for what
 * explains, the accent for the one action. Always dark and always Flat: nothing on a 1.4 inch
 * screen has room to swell, and a shader has no business on a watch battery.
 */

/**
 * Kelvin's theme for Wear OS: dark, Flat, outline glyphs (lighter at watch sizes), motion kept
 * still. [accent] is the app's.
 */
@Composable
fun KelvinWearTheme(accent: Accent = Accent.Ember, content: @Composable () -> Unit) {
    KelvinTheme(
        dark = true,
        accent = accent,
        finish = KelvinFinish.Flat,
        icons = IconStyle.Outline,
        reduceMotion = true,
        content = content,
    )
}

/** Whether the screen is round, where the corners of a square layout fall off the edge. */
val isRoundScreen: Boolean @Composable get() = LocalConfiguration.current.isScreenRound

/**
 * How far content keeps from the left and right edges: 5.2% of the screen on a round watch, as
 * Wear OS asks, a little less on a square one.
 */
val wearGutter: Dp
    @Composable get() {
        val width = LocalConfiguration.current.screenWidthDp
        return (width * if (isRoundScreen) 0.052f else 0.03f).dp
    }

/**
 * A page: a black screen and one list that scales and fades toward the top and bottom edges and
 * turns with the crown. The page opens with its title at the top of the screen, not half way
 * down it, and leaves room under the last row so it can be scrolled clear of a round screen's
 * bottom curve. Put a [KelvinWearTitle] first.
 */
@Composable
fun KelvinWearPage(
    modifier: Modifier = Modifier,
    state: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
    content: ScalingLazyListScope.() -> Unit,
) {
    val gutter = wearGutter
    val height = LocalConfiguration.current.screenHeightDp
    // A round screen is narrow at its top and bottom: the centred title fits there, a full row does not.
    val top = if (isRoundScreen) (height * 0.11f).dp else 12.dp
    val bottom = if (isRoundScreen) (height * 0.3f).dp else 24.dp
    Box(modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = gutter, end = gutter, top = top, bottom = bottom),
            // Not centred on its first row: the page starts at its top, as a page does.
            autoCentering = null,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** The page's name, centred at the top: small and in the accent, so the content below leads. */
@Composable
fun KelvinWearTitle(text: String, modifier: Modifier = Modifier) {
    KText(
        text,
        modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { heading() },
        Kelvin.type.section,
        Kelvin.colors.accent,
        maxLines = 1,
        align = TextAlign.Center,
    )
}

/** A quiet label over a group of rows. */
@Composable
fun KelvinWearSection(text: String, modifier: Modifier = Modifier) {
    KText(
        text,
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp).semantics { heading() },
        Kelvin.type.caption,
        Kelvin.colors.ink2,
        maxLines = 1,
    )
}

/** Reading text straight on the page: a sentence about the day, a message's body. */
@Composable
fun KelvinWearText(text: String, modifier: Modifier = Modifier, color: Color = Kelvin.colors.ink, align: TextAlign? = null) {
    KText(text, modifier.fillMaxWidth().padding(horizontal = 8.dp), Kelvin.type.body, color, align = align)
}

/**
 * A row in a `group` well: a [title] on one line, up to two lines of [summary], an optional
 * [overline] above (a sender over a subject), a [leading] glyph and a [trailing] note (a time, a
 * count). [unread] sets the title bold with a dot in the accent. Pressable when [onClick] is set.
 */
@Composable
fun KelvinWearRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    overline: String? = null,
    leading: ImageVector? = null,
    trailing: String? = null,
    unread: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Kelvin.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(KelvinShapes.lg)
            .background(c.group, KelvinShapes.lg)
            .then(if (onClick != null) Modifier.kelvinPress(onClick, role = Role.Button, shape = KelvinShapes.lg) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading != null) KIcon(leading, null, size = 20.dp, color = c.ink2)
        Column(Modifier.weight(1f)) {
            if (overline != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (unread) Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                    KText(overline.orEmpty(), Modifier.weight(1f), Kelvin.type.caption, if (unread) c.ink else c.ink2, maxLines = 1)
                    if (trailing != null) KText(trailing, style = Kelvin.type.caption, color = c.ink2, maxLines = 1)
                }
            }
            KText(
                title,
                style = Kelvin.type.label.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium),
                color = c.ink,
                maxLines = if (summary == null) 2 else 1,
            )
            if (summary != null) KText(summary, style = Kelvin.type.summary, color = c.ink2, maxLines = 2)
        }
    }
}

/** A figure and what it counts, side by side with others in a [Row]: light and large, after Kelvin's figure grammar. */
@Composable
fun KelvinWearStat(value: String, label: String, modifier: Modifier = Modifier, tone: Color = Kelvin.colors.ink) {
    val c = Kelvin.colors
    Column(
        modifier.clip(KelvinShapes.lg).background(c.group, KelvinShapes.lg).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A short figure is set large; a long one (an amount of money) steps down so it is never cut off.
        val figure = if (value.length <= LARGE_FIGURE_LENGTH) Kelvin.type.headline else Kelvin.type.name
        KText(value, style = figure.copy(fontWeight = FontWeight.Light), color = tone, maxLines = 1)
        KText(label, style = Kelvin.type.micro, color = c.ink2, maxLines = 1, align = TextAlign.Center)
    }
}

private const val LARGE_FIGURE_LENGTH = 4

enum class WearActionKind { Primary, Neutral, Danger }

/** A full-width action: a capsule with a glyph and its name. One [WearActionKind.Primary] per page. */
@Composable
fun KelvinWearAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: WearActionKind = WearActionKind.Neutral,
    enabled: Boolean = true,
) {
    val c = Kelvin.colors
    val (fill, ink) = when (kind) {
        WearActionKind.Primary -> c.accent to c.onAccent
        WearActionKind.Neutral -> c.group to c.ink
        WearActionKind.Danger -> c.criticalSoft to c.critical
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(KelvinShapes.full)
            .background(fill, KelvinShapes.full)
            .kelvinPress(onClick, enabled = enabled, role = Role.Button, shape = KelvinShapes.full)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KIcon(icon, null, size = 20.dp, color = ink)
        KText(text, Modifier.weight(1f), Kelvin.type.action, ink, maxLines = 2)
    }
}

/**
 * Something went wrong, in Kelvin's shape for it: a [headline], what happened and what to try
 * ([description]), a "details" row that opens to why and the [code], and one way out ([action]).
 * The raw error is never shown first.
 */
@Composable
fun KelvinWearProblem(
    headline: String,
    description: String,
    code: String,
    why: String,
    detailsLabel: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Kelvin.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KIcon(KelvinIcons.Alert, null, size = 22.dp, color = c.caution)
        KText(headline, Modifier.padding(horizontal = 12.dp), Kelvin.type.name, c.ink, align = TextAlign.Center)
        KText(description, Modifier.padding(horizontal = 12.dp), Kelvin.type.summary, c.ink2, align = TextAlign.Center)
        KelvinWearRow(
            title = detailsLabel,
            summary = if (open) "$why\n$code" else null,
            trailing = null,
            leading = if (open) KelvinIcons.ChevronUp else KelvinIcons.ChevronDown,
            onClick = { open = !open },
        )
        action?.invoke()
    }
}

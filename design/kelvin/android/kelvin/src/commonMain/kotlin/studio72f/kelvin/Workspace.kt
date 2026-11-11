package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

// The pieces work apps need on a desk-sized window: finding any action by name, tables with columns, keys shown as
// keys, where you are, pages of results, a strip that speaks to the whole window, folder trees, a dropdown, a toolbar
// and an activity log. They follow the rest of Kelvin: nothing outlined, a chosen thing on the accent's wash, a
// pointer answered with a hover wash, every action reachable from the keyboard. They draw in both finishes and both
// corner scales because they are built from Kelvin's own shapes, colours and type.

/** A key as a key cap, for menus, tooltips and the shortcuts list: "Ctrl K" draws two caps. */
@Composable
fun KelvinKbd(keys: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = keys }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.split(' ').filter { it.isNotBlank() }.forEach { key ->
            Box(
                Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).background(Kelvin.colors.sunken, KelvinShapes.xs)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { KText(key, style = Kelvin.type.micro, color = Kelvin.colors.ink2, maxLines = 1) }
        }
    }
}

/**
 * Says what an icon-only control does when the pointer rests on it, after a short wait, with its key if it has one.
 * A touch screen never sees it, so the control still needs its own name for screen readers.
 */
@Composable
fun KelvinTooltip(
    text: String,
    modifier: Modifier = Modifier,
    keys: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered) delay(TooltipWaitMillis)
        shown = hovered
    }
    Box(modifier.hoverable(source)) {
        content()
        if (shown) {
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -TooltipLift), properties = PopupProperties(focusable = false)) {
                Row(
                    Modifier.background(Kelvin.colors.ink, KelvinShapes.xs).padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KText(text, style = Kelvin.type.caption, color = Kelvin.colors.canvas, maxLines = 1)
                    if (keys != null) KText(keys, style = Kelvin.type.micro, color = Kelvin.colors.ink3, maxLines = 1)
                }
            }
        }
    }
}

private const val TooltipWaitMillis = 500L
private const val TooltipLift = 64

/** Where you are, as a path of places; each but the last goes there. The last is where you are, in ink. */
@Composable
fun KelvinBreadcrumbs(places: List<String>, onPlace: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        places.forEachIndexed { i, place ->
            val last = i == places.lastIndex
            KText(
                place,
                Modifier.clip(KelvinShapes.xs)
                    .then(if (last) Modifier else Modifier.kelvinHover(KelvinShapes.xs).kelvinPress({ onPlace(i) }, shape = KelvinShapes.xs))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                Kelvin.type.label,
                if (last) Kelvin.colors.ink else Kelvin.colors.ink2,
                maxLines = 1,
            )
            if (!last) KIcon(KelvinIcons.ChevronRight, null, size = 14.dp, color = Kelvin.colors.ink3)
        }
    }
}

/** One thing the command palette can do: its [label], an optional [icon], the [group] it is listed under, its [keys]. */
data class KelvinCommand(
    val label: String,
    val onRun: () -> Unit,
    val icon: ImageVector? = null,
    val group: String = "",
    val keys: String? = null,
    /** Other words it answers to: "trash" for Delete. */
    val also: List<String> = emptyList(),
)

/**
 * One box that finds any action, folder or setting by name (Ctrl K). It is how a simple window stays powerful: what
 * is not on screen is a few letters away. Arrows move, Enter runs, Esc closes. It filters as you type, every word
 * matching; with nothing typed it lists everything, by group.
 */
@Composable
fun KelvinCommandPalette(
    visible: Boolean,
    onDismiss: () -> Unit,
    commands: List<KelvinCommand>,
    placeholder: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    var query by remember { mutableStateOf("") }
    var at by remember { mutableIntStateOf(0) }
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val found = commands.filter { command ->
        val text = (listOf(command.label, command.group) + command.also).joinToString(" ").lowercase()
        words.all { it in text }
    }
    at = at.coerceIn(0, (found.size - 1).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(at) { if (found.isNotEmpty()) list.animateScrollToItem(at) }
    fun run(command: KelvinCommand) {
        onDismiss()
        command.onRun()
    }
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.fillMaxSize().background(Kelvin.colors.scrim).kelvinClick(onDismiss), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier.padding(top = 96.dp).width(560.dp).heightIn(max = 440.dp)
                    .glass(null, KelvinShapes.lg, GlassContent.Pane, elevated = true)
                    .kelvinClick({})
                    .semantics { paneTitle = placeholder }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { at = (at + 1).coerceAtMost(found.lastIndex.coerceAtLeast(0)); true }
                            Key.DirectionUp -> { at = (at - 1).coerceAtLeast(0); true }
                            Key.Enter -> { found.getOrNull(at)?.let(::run); true }
                            Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    },
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KIcon(KelvinIcons.Search, null, size = 20.dp, color = Kelvin.colors.ink2)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) KText(placeholder, style = Kelvin.type.body, color = Kelvin.colors.ink3, maxLines = 1)
                        BasicTextField(
                            query,
                            { query = it; at = 0 },
                            Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = placeholder },
                            textStyle = Kelvin.type.body.copy(color = Kelvin.colors.ink),
                            cursorBrush = SolidColor(Kelvin.colors.accent),
                            singleLine = true,
                        )
                    }
                    KelvinKbd("Esc")
                }
                KelvinDivider()
                if (found.isEmpty()) {
                    KText(emptyText, Modifier.padding(20.dp), Kelvin.type.summary, Kelvin.colors.ink2)
                } else {
                    LazyColumn(Modifier.padding(6.dp), state = list) {
                        itemsIndexed(found) { i, command ->
                            val heading = command.group.takeIf { query.isBlank() && it.isNotBlank() && (i == 0 || found[i - 1].group != it) }
                            if (heading != null) KelvinLineHeading(heading, top = if (i == 0) 4.dp else 12.dp)
                            CommandLine(command, chosen = i == at, onRun = { run(command) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandLine(command: KelvinCommand, chosen: Boolean, onRun: () -> Unit) {
    val c = Kelvin.colors
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(KelvinShapes.md)
            .background(if (chosen) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent)
            .kelvinHover(KelvinShapes.md).kelvinPress(onRun, shape = KelvinShapes.md)
            .semantics { selected = chosen }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            command.icon?.let { KIcon(it, null, size = 18.dp, color = if (chosen) c.onAccentSoft else c.ink2) }
        }
        KText(command.label, Modifier.weight(1f), Kelvin.type.rowTitle, maxLines = 1)
        command.keys?.let { KelvinKbd(it) }
    }
}

/** A column of a [KelvinTable]: its [title], how wide, how each row reads in it, and whether it sorts. */
class KelvinColumn<T>(
    val title: String,
    val text: (T) -> String,
    /** A share of the room left over, or [width] for a fixed column. */
    val weight: Float = 1f,
    val width: Dp? = null,
    val sortable: Boolean = true,
    /** Numbers and dates line up at the end. */
    val end: Boolean = false,
)

/**
 * Rows with columns, for people who want to see many things side by side and sort them: a classic mail list, an
 * audit log, seats in a team. The header sorts by a column when clicked, and again the other way. Rows are 40dp,
 * hover, and sit on the accent's wash when [selected]. With [onCheck] each row has a checkbox, for acting on many.
 * Sorting is the caller's: this draws [rows] in the order given and says which column it is sorted by.
 */
@Composable
fun <T> KelvinTable(
    rows: List<T>,
    columns: List<KelvinColumn<T>>,
    modifier: Modifier = Modifier,
    sortedBy: Int? = null,
    ascending: Boolean = true,
    onSort: ((Int) -> Unit)? = null,
    selected: (T) -> Boolean = { false },
    onRow: ((T) -> Unit)? = null,
    checked: (T) -> Boolean = { false },
    onCheck: ((T, Boolean) -> Unit)? = null,
) {
    val c = Kelvin.colors
    Column(modifier) {
        TableLine(Modifier.height(36.dp), onCheck != null) {
            columns.forEachIndexed { i, column ->
                val sorted = sortedBy == i
                Row(
                    cellOf(column).clip(KelvinShapes.xs)
                        .then(if (column.sortable && onSort != null) Modifier.kelvinHover(KelvinShapes.xs).kelvinPress({ onSort(i) }, shape = KelvinShapes.xs) else Modifier)
                        .semantics { heading(); if (sorted) stateDescription = if (ascending) "sorted ascending" else "sorted descending" }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = if (column.end) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KText(column.title, style = Kelvin.type.section, color = if (sorted) c.ink else c.ink2, maxLines = 1)
                    if (sorted) KIcon(if (ascending) KelvinIcons.ChevronUp else KelvinIcons.ChevronDown, null, Modifier.padding(start = 2.dp), size = 14.dp, color = c.ink)
                }
            }
        }
        rows.forEach { row ->
            val isSelected = selected(row)
            TableLine(
                Modifier.height(40.dp).clip(KelvinShapes.md)
                    .background(if (isSelected) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent)
                    .kelvinHover(KelvinShapes.md)
                    .then(if (onRow != null) Modifier.kelvinPress({ onRow(row) }, shape = KelvinShapes.md) else Modifier)
                    .semantics { this.selected = isSelected },
                check = onCheck != null,
                checkbox = { onCheck?.let { KelvinCheckbox(checked(row), { on -> it(row, on) }) } },
            ) {
                columns.forEach { column ->
                    KText(
                        column.text(row),
                        cellOf(column).padding(horizontal = 4.dp),
                        Kelvin.type.rowTitle,
                        maxLines = 1,
                        align = if (column.end) androidx.compose.ui.text.style.TextAlign.End else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableLine(
    modifier: Modifier,
    check: Boolean,
    checkbox: @Composable () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (check) Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { checkbox() }
        content()
    }
}

private fun <T> RowScope.cellOf(column: KelvinColumn<T>): Modifier =
    column.width?.let { Modifier.width(it) } ?: Modifier.weight(column.weight)

/**
 * Pages of results, said as a range ("1–50 of 1,240") with the way back and on. The words are the caller's, so the
 * numbers are written in the person's language.
 */
@Composable
fun KelvinPagination(
    range: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KText(range, Modifier.padding(end = 8.dp), Kelvin.type.figure, Kelvin.colors.ink2, maxLines = 1)
        KelvinIconButton(KelvinIcons.ChevronLeft, previousLabel, { onPrevious?.invoke() }, kind = IconButtonKind.Plain, enabled = onPrevious != null)
        KelvinIconButton(KelvinIcons.ChevronRight, nextLabel, { onNext?.invoke() }, kind = IconButtonKind.Plain, enabled = onNext != null)
    }
}

/**
 * A strip across the top of a window that speaks to all of it: offline, a trial ending, a mailbox that needs signing
 * in again. One line in the tone's soft fill, with one action and a way to close it. For something about one part of
 * the window, use [KelvinNotice] there instead.
 */
@Composable
fun KelvinBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    icon: ImageVector? = KelvinIcons.Info,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "",
) {
    val t = tone.colors()
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).background(t.fill).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { KIcon(it, null, size = 18.dp, color = t.ink) }
        KText(text, Modifier.weight(1f), Kelvin.type.label, t.ink, maxLines = 2)
        if (actionText != null && onAction != null) KelvinButton(actionText, onAction, kind = ButtonKind.Text)
        if (onDismiss != null) KelvinIconButton(KelvinIcons.X, dismissLabel, onDismiss, kind = IconButtonKind.Plain)
    }
}

/**
 * One place in a tree of places (folders inside folders), indented by its [depth]. A place with children has a
 * chevron that opens and closes it without choosing it; [count] is what is unread in it. The chosen one sits on the
 * accent's wash, as on the rail.
 */
@Composable
fun KelvinTreeItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = KelvinIcons.Folder,
    depth: Int = 0,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    selected: Boolean = false,
    count: Int = 0,
    toggleLabel: String = "",
) {
    val c = Kelvin.colors
    Row(
        modifier.fillMaxWidth().height(36.dp).clip(KelvinShapes.md)
            .background(if (selected) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent)
            .kelvinHover(KelvinShapes.md).kelvinPress(onClick, shape = KelvinShapes.md)
            .semantics {
                this.selected = selected
                if (expanded != null) stateDescription = if (expanded) "expanded" else "collapsed"
            }
            .padding(start = 4.dp + TreeIndent * depth, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (expanded != null && onToggle != null) {
                KIcon(
                    if (expanded) KelvinIcons.ChevronDown else KelvinIcons.ChevronRight,
                    toggleLabel,
                    Modifier.clip(CircleShape).kelvinPress(onToggle, shape = CircleShape),
                    size = 16.dp,
                    color = c.ink2,
                )
            }
        }
        icon?.let { KIcon(it, null, size = 18.dp, color = if (selected) c.onAccentSoft else c.ink2, style = IconStyle.of(selected)) }
        KText(label, Modifier.weight(1f), if (count > 0) Kelvin.type.name else Kelvin.type.rowTitle, maxLines = 1)
        if (count > 0) KText(count.toString(), style = Kelvin.type.figure, color = if (selected) c.onAccentSoft else c.ink2)
    }
}

private val TreeIndent = 16.dp

/**
 * A choice of one among a few, shown as its current value and opened as a list under it: a sort order, a time zone,
 * the account to send from. For two to four choices that should all be seen, use [KelvinSegmented] instead.
 */
@Composable
fun <T> KelvinSelect(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    text: (T) -> String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val c = Kelvin.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KText(label, style = Kelvin.type.label, color = c.ink2)
        Box {
            Row(
                Modifier.fillMaxWidth().height(KelvinSizes.field).clip(KelvinShapes.md).background(c.sunken)
                    .kelvinHover(KelvinShapes.md).kelvinPress({ open = !open }, shape = KelvinShapes.md)
                    .semantics { contentDescription = "$label, ${text(selected)}" }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KText(text(selected), Modifier.weight(1f), Kelvin.type.body, maxLines = 1)
                KIcon(if (open) KelvinIcons.ChevronUp else KelvinIcons.ChevronDown, null, size = 18.dp, color = c.ink2)
            }
            if (open) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, SelectDrop),
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier.widthIn(min = 200.dp, max = 360.dp).glass(null, KelvinShapes.md, GlassContent.Pane, elevated = true)
                            .padding(4.dp)
                            .semantics { paneTitle = label },
                    ) {
                        options.forEach { option ->
                            val chosen = option == selected
                            Row(
                                Modifier.fillMaxWidth().height(36.dp).clip(KelvinShapes.sm)
                                    .background(if (chosen) c.accent.copy(alpha = KelvinSelectedWash) else Color.Transparent)
                                    .kelvinHover(KelvinShapes.sm)
                                    .kelvinPress({ onSelect(option); open = false }, shape = KelvinShapes.sm)
                                    .semantics { this.selected = chosen }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KText(text(option), Modifier.weight(1f), Kelvin.type.rowTitle, maxLines = 1)
                                if (chosen) KIcon(KelvinIcons.Check, null, size = 16.dp, color = c.onAccentSoft)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SelectDrop = 150

/**
 * A row of tools over what they act on (a list, an email, an editor): icon buttons in groups, a group parted from
 * the next by space, not a line. Put [KelvinTooltip] round each so its name and key show on hover.
 */
@Composable
fun KelvinToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** The space between two groups on a [KelvinToolbar]. */
@Composable
fun KelvinToolbarGap() {
    Spacer(Modifier.width(12.dp))
}

/**
 * One entry in an activity log or a thread's history: who ([actor]) did [what], and when. Entries stack into a line
 * down the leading edge, joined unless [last]; [tone] colours the dot for what matters (a failure, a sign-in).
 */
@Composable
fun KelvinActivityItem(
    actor: String,
    what: String,
    time: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    detail: String? = null,
    last: Boolean = false,
) {
    val c = Kelvin.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).background(if (tone == Tone.Neutral) c.ink3 else tone.base(), CircleShape))
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(c.hairline))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KText(actor, style = Kelvin.type.name, maxLines = 1)
                KText(what, Modifier.weight(1f), Kelvin.type.rowTitle, c.ink2, maxLines = 1)
                KText(time, style = Kelvin.type.figure, color = c.ink2, maxLines = 1)
            }
            detail?.let { KText(it, style = Kelvin.type.summary, color = c.ink2) }
        }
    }
}

/** The keys of a window, listed by what they do, for the `?` sheet: each action on a line with its key caps. */
@Composable
fun KelvinShortcutList(shortcuts: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shortcuts.forEach { (keys, does) ->
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                KText(does, Modifier.weight(1f), Kelvin.type.rowTitle, maxLines = 1)
                KelvinKbd(keys)
            }
        }
    }
}

/**
 * The actions a row offers while the pointer is over it (archive, pin, snooze, more), in place of its time, as small
 * plain icons with their names on hover. A row shows them with [Modifier.hoverActions] or by checking its own hover.
 */
@Composable
fun KelvinRowActions(actions: List<KelvinAction>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        actions.forEach { action ->
            KelvinTooltip(action.label) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).kelvinHover(CircleShape)
                        .kelvinPress(action.onClick, enabled = action.enabled, shape = CircleShape)
                        .semantics { contentDescription = action.label },
                    contentAlignment = Alignment.Center,
                ) {
                    action.icon?.let {
                        KIcon(it, null, size = 18.dp, color = if (action.destructive) Kelvin.colors.critical else Kelvin.colors.ink2)
                    }
                }
            }
        }
    }
}

/** Whether the pointer is over this element, for rows that trade their time for [KelvinRowActions] on hover. */
@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

/** Lets a row report the pointer over it to [rememberHover]. */
fun Modifier.hoverActions(source: MutableInteractionSource): Modifier = hoverable(source)

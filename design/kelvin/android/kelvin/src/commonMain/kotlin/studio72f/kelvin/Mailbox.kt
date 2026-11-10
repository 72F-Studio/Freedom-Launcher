package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * One folder in a mailbox tree. [depth] indents nested folders (0 is top level). [icon] is the folder's glyph (Inbox,
 * Sent, Trash; a plain folder for the person's own). The [selected] folder sits on a firm wash of the accent ([KelvinSelectedWash]), as a chosen message does. The state is
 * announced to TalkBack, never colour alone.
 *
 * [unread] is a quiet figure after the name: a list of folders is a list of counts, and a signal badge on every one
 * would drown the one that matters. Give [alertUnread] to the folder whose unread mail needs the person (the inbox) and
 * it takes the accent's badge instead. A folder with subfolders passes [expanded] and [onToggle]: a chevron at the end
 * opens and closes them, turning as it goes.
 */
@Composable
fun KelvinFolderRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    unread: Int = 0,
    selected: Boolean = false,
    selectedLabel: String = kelvinString(KelvinString.Selected),
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    alertUnread: Boolean = false,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    toggleLabel: String? = null,
) {
    val c = Kelvin.colors
    val ink = if (selected) c.onAccentSoft else c.ink
    val indent = 16.dp + 20.dp * depth.coerceIn(0, 6)
    // Concentric with the well it sits in (well radius minus the gap), else Kelvin's medium corner.
    val shape = LocalGroupInnerRadius.current?.let { ContinuousShape(it) } ?: KelvinShapes.md
    Row(
        // In a group the divider starts where the name does: past the indent and the glyph.
        modifier.groupDividerInset(indent + if (icon != null) 34.dp else 0.dp).fillMaxWidth().heightIn(min = 42.dp)
            // A chosen folder takes the same wash as a chosen message: it has to stand off the well's grey.
            .background(if (selected) c.accent.copy(alpha = KelvinSelectedWash) else androidx.compose.ui.graphics.Color.Transparent, shape)
            .kelvinPress(onClick, role = androidx.compose.ui.semantics.Role.Button, shape = shape)
            .semantics { this.selected = selected; if (selected) stateDescription = selectedLabel }
            .padding(start = indent, end = if (expanded != null) 4.dp else 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) KIcon(icon, null, size = 22.dp, color = if (selected) c.onAccentSoft else c.ink2)
        KText(
            name,
            Modifier.weight(1f),
            if (selected || unread > 0) Kelvin.type.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Kelvin.type.rowTitle,
            ink,
            maxLines = 1,
        )
        if (unread > 0) {
            if (alertUnread) KelvinBadge(unread)
            else KelvinRollingText(unread.toString(), style = Kelvin.type.figure, color = if (selected) c.onAccentSoft else c.ink2)
        }
        if (expanded != null && onToggle != null) {
            val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, Kelvin.motion.gentle(), label = "folder chevron")
            androidx.compose.foundation.layout.Box(
                Modifier.size(40.dp).kelvinPull(onToggle, onClickLabel = toggleLabel, shape = androidx.compose.foundation.shape.CircleShape)
                    .semantics { stateDescription = if (expanded) "expanded" else "collapsed" },
                contentAlignment = Alignment.Center,
            ) {
                KIcon(KelvinIcons.ChevronDown, null, Modifier.graphicsLayer { rotationZ = turn }, size = 18.dp, color = c.ink3)
            }
        }
    }
}

/** An account the person can switch to. */
data class KelvinAccountItem(val name: String, val address: String, val unread: Int = 0)

/**
 * The account list at the top of the folder drawer: an avatar, the account's name and address, an
 * unread count, and a mark on the current one. Tapping a row switches to it.
 */
@Composable
fun KelvinAccountSwitcher(
    accounts: List<KelvinAccountItem>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    currentLabel: String = kelvinString(KelvinString.CurrentAccount),
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
) {
    KelvinGroup(modifier, gutter = gutter) {
        accounts.forEachIndexed { i, a ->
            val current = i == currentIndex
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .kelvinPress({ onSelect(i) }, role = androidx.compose.ui.semantics.Role.Button)
                    .semantics { this.selected = current; if (current) stateDescription = currentLabel }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KelvinAvatar(a.name)
                Column(Modifier.weight(1f)) {
                    KText(a.name, style = Kelvin.type.rowTitle, maxLines = 1)
                    KText(a.address, style = Kelvin.type.summary, color = Kelvin.colors.ink2, maxLines = 1)
                }
                KelvinBadge(a.unread)
                if (current) KelvinGlassTile(KelvinIcons.CircleCheck, Kelvin.colors.accent)
            }
        }
    }
}

/**
 * Shown while messages are selected in the list: how many, and the actions that apply to all of
 * them. Place it over the list's header in a full-screen Box. [onClose] leaves selection mode.
 */
@Composable
fun KelvinSelectionBar(
    count: Int,
    countText: String,
    actions: List<KelvinAction>,
    onClose: () -> Unit,
    closeLabel: String,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KelvinIconButton(KelvinIcons.X, closeLabel, onClose, kind = IconButtonKind.Plain)
            KText(countText, Modifier.weight(1f).semantics { heading() }, Kelvin.type.name)
        }
        if (count > 0) KelvinActionBar(actions, backdrop)
    }
}

/**
 * Where the person is in a short flow (account setup): "Step 2 of 4", the step's title and a bar.
 * Steps are words the person can read, never the names of server protocols.
 */
@Composable
fun KelvinStepHeader(
    step: Int,
    steps: Int,
    title: String,
    stepText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KelvinEyebrow(stepText)
        KText(title, Modifier.semantics { heading() }, Kelvin.type.name)
        KelvinProgressBar(step.coerceIn(0, steps).toFloat() / steps.coerceAtLeast(1))
    }
}

/** The wash behind a chosen row (a message, a folder): firm enough to read against a group's grey. */
const val KelvinSelectedWash = 0.2f

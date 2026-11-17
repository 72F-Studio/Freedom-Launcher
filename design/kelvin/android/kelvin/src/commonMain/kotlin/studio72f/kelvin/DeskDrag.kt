package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The edge between two panes of a desktop window, dragged sideways to share the width between them. At rest it is
 * nothing but the gap between the panes; under the pointer, and while dragged, a short grip shows in its middle and the
 * pointer becomes the sideways arrows. [onDrag] gets each step of the drag (positive to the end side); the app keeps
 * the widths within what each pane needs, and remembers them in [onDragEnd]. [label] is what a screen reader says.
 */
@Composable
fun KelvinSplitHandle(
    onDrag: (Dp) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onDragEnd: () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val dragged by source.collectIsDraggedAsState()
    val density = LocalDensity.current
    val state = rememberDraggableState { px -> onDrag(with(density) { px.toDp() }) }
    Box(
        modifier.width(SplitHandleWidth).fillMaxHeight().pointerHoverIcon(kelvinResizeCursor()).hoverable(source)
            .draggable(state, Orientation.Horizontal, interactionSource = source, onDragStopped = { onDragEnd() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (hovered || dragged) {
            Box(Modifier.width(4.dp).height(40.dp).background(Kelvin.colors.ink3, KelvinShapes.full))
        }
    }
}

/** How wide a [KelvinSplitHandle] is to the pointer: the gap between two panes. */
val SplitHandleWidth = 12.dp

/**
 * What follows the pointer while something is dragged to a place (an email to a folder): its [icon] and [text] on a
 * raised pill, lifted off the window by a soft shadow. Draw it in a layer above the window at the pointer.
 */
@Composable
fun KelvinDragChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, maxWidth: Dp = 280.dp) {
    val c = Kelvin.colors
    Row(
        modifier.widthIn(max = maxWidth).shadow(8.dp, KelvinShapes.full).background(c.raised, KelvinShapes.full)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) KIcon(icon, null, size = 18.dp, color = c.ink2)
        KText(text, style = Kelvin.type.rowTitle, maxLines = 1)
    }
}

package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The words and glyph-less labels a [KelvinMessageBar] needs, supplied by the app so they translate. */
data class MessageBarLabels(
    val reply: String,
    val archive: String,
    val delete: String,
    val more: String,
)

/**
 * The message screen's actions, within the thumb's reach at the foot (the control bar of Kelvin's guidelines):
 * Reply as the accent pebble in the middle, Archive and Delete either side of it, and a More circle at the end that
 * opens [moreItems] (Reply all, Forward, Mark unread, Move) as a glass menu. The page scrolls beneath it. Archive is
 * left out when [onArchive] is null, Delete when [onDelete] is null.
 */
@Composable
fun KelvinMessageBar(
    labels: MessageBarLabels,
    onReply: () -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    moreItems: List<KelvinAction>,
    modifier: Modifier = Modifier,
    backdrop: KelvinBackdrop? = null,
) {
    var menu by remember { mutableStateOf(false) }
    // The page scrolls beneath the bar: it fades into the canvas so text never shows between the controls.
    val canvas = Kelvin.colors.canvas
    val fade = Brush.verticalGradient(listOf(Color.Transparent, canvas.copy(alpha = 0.94f), canvas))
    Box(modifier.background(fade).padding(top = 28.dp).navigationBarsPadding()) {
        KelvinControlBar(
            center = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // An absent action leaves its slot empty, so Reply stays in the middle.
                    if (onArchive != null) KelvinIconButton(KelvinIcons.Archive, labels.archive, onArchive, backdrop = backdrop) else Box(Modifier.size(48.dp))
                    KelvinActionOrb(KelvinIcons.Reply, labels.reply, onReply)
                    if (onDelete != null) KelvinIconButton(KelvinIcons.Trash, labels.delete, onDelete, backdrop = backdrop) else Box(Modifier.size(48.dp))
                }
            },
            trailing = {
                if (moreItems.isNotEmpty()) {
                    KelvinIconButton(KelvinIcons.Dots, labels.more, { menu = true }, backdrop = backdrop)
                }
            },
        )
        KelvinMenu(
            visible = menu,
            onDismiss = { menu = false },
            items = moreItems.map { item -> item.copy(onClick = { menu = false; item.onClick() }) },
            backdrop = backdrop,
            title = labels.more,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp),
        )
    }
}

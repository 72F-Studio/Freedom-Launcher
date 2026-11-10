package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One of a few choices shown side by side as tall cards: a glass tile at the top, then the choice's
 * name and an optional line under it, at the same height in every card. For a first decision with two to four answers (which
 * kind of account, which plan), where a list would read as settings. Put them in a [KelvinStatRow]
 * with `Modifier.weight(1f)` each, so they share one height. Keep [title] to one short word or two;
 * [summary] wraps.
 */
@Composable
fun KelvinChoiceCard(
    title: String,
    icon: ImageVector,
    tile: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val c = Kelvin.colors
    Column(
        modifier.fillMaxHeight().heightIn(min = 148.dp).background(c.group, KelvinShapes.lg)
            .kelvinPress(onClick, shape = KelvinShapes.lg).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(14.dp),
    ) {
        KelvinGlassTile(icon, tile, size = 44.dp)
        // The name sits a fixed step under the tile, so names line up across cards whatever follows them.
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(title, style = Kelvin.type.name, color = c.ink)
            if (summary != null) KText(summary, style = Kelvin.type.caption, color = c.ink2)
        }
    }
}

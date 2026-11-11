package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
 * One of a few choices shown side by side as tall cards: its picture centred at the top, large, then the
 * choice's name and an optional line under it, centred too and at the same height in every card. For a first
 * decision with two to four answers (which kind of account, which plan), where a list would read as settings.
 * Put them in a [KelvinStatRow] with `Modifier.weight(1f)` each, so they share one height. Keep [title] to one
 * short word or two; [summary] wraps.
 *
 * This form draws a glass tile. When the choice has a mark of its own that people know it by (a mail provider,
 * a bank, a store), use the form that takes [art] and show that mark as it is.
 */
@Composable
fun KelvinChoiceCard(
    title: String,
    icon: ImageVector,
    tile: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = KelvinChoiceCard(title, onClick, modifier, summary) { KelvinGlassTile(icon, tile, size = ChoiceArt) }

/**
 * A choice card whose picture is the app's own [art]: a provider's or a product's mark in its real colours,
 * never tinted and never on a tile, or a large glyph for the answer that has no mark ("Other"). [art] is
 * centred in a [ChoiceArt] square; size what you draw to fit it.
 */
@Composable
fun KelvinChoiceCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    art: @Composable () -> Unit,
) {
    val c = Kelvin.colors
    Column(
        modifier.rowHeight().heightIn(min = 156.dp).background(c.group, KelvinShapes.lg)
            .kelvinPress(onClick, shape = KelvinShapes.lg).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(ChoiceArt), contentAlignment = Alignment.Center) { art() }
        // The name sits a fixed step under the picture, so names line up across cards whatever follows them.
        KText(title, Modifier.padding(top = 14.dp), style = Kelvin.type.name, color = c.ink, maxLines = 1, align = TextAlign.Center)
        if (summary != null) {
            KText(summary, Modifier.padding(top = 2.dp), style = Kelvin.type.caption, color = c.ink2, align = TextAlign.Center)
        }
    }
}

/** The square a choice card's picture is centred in. */
val ChoiceArt = 56.dp

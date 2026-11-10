package studio72f.kelvin

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A desktop scrollbar for a scrolling column: a thin capsule of ink that firms up under the pointer. A desktop
 * window is expected to show how long a list is and to let it be dragged; touch screens have no use for one.
 */
@Composable
fun KelvinScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    val ink = Kelvin.colors.ink
    val style = LocalScrollbarStyle.current.copy(
        thickness = 6.dp,
        shape = CircleShape,
        unhoverColor = ink.copy(alpha = 0.16f),
        hoverColor = ink.copy(alpha = 0.4f),
    )
    CompositionLocalProvider(LocalScrollbarStyle provides style) {
        VerticalScrollbar(rememberScrollbarAdapter(state), modifier)
    }
}

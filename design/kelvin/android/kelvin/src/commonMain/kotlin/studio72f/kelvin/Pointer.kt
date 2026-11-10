package studio72f.kelvin

import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Kelvin for a pointer and a keyboard: a desktop window, or a tablet with a mouse. Touch never sees any of this.

/**
 * What is under the pointer answers it: a faint wash of ink over the row or control, in its own [shape]. It is the
 * desktop's stand-in for the press a finger gives, so it is as quiet as a press dim and never moves anything.
 */
fun Modifier.kelvinHover(shape: Shape = RectangleShape, enabled: Boolean = true): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val ink = Kelvin.colors.ink
    val wash = if (Kelvin.dark) 0.07f else 0.045f
    hoverable(source, enabled).drawWithContent {
        drawContent()
        if (hovered) drawOutline(shape.createOutline(size, layoutDirection, this), ink.copy(alpha = wash))
    }
}

/**
 * Makes this element a stop for the Tab key and rings it in the accent while it holds the keyboard's focus, so someone
 * working without a pointer always sees where they are. The ring sits inside the element, in its own [shape].
 */
fun Modifier.kelvinFocusRing(shape: Shape = RectangleShape): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val accent = Kelvin.colors.accent
    focusable(interactionSource = source).drawWithContent {
        drawContent()
        if (focused) {
            val width = 2.dp.toPx()
            drawOutline(shape.createOutline(size, layoutDirection, this), accent, style = Stroke(width))
        }
    }
}

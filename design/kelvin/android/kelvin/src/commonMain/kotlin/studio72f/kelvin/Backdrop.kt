package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** True inside [KelvinOnBackdrop]: the page behind is coloured, so soft fills would sink into it. */
val LocalKelvinOnBackdrop = staticCompositionLocalOf { false }

/**
 * Kelvin on a coloured or drifting backdrop (an aurora, a wash, a photo). Soft tinted fills and grey
 * text are close in lightness to such a backdrop and disappear into it, so inside this:
 *
 * - a plain card is the raised surface, its label and figure in full ink;
 * - a card with a tone stays that raised surface, its tone a soft light in the corner and the colour of
 *   its label and marks;
 * - a notice that asks for something (accent) takes a pastel of that colour, a step deeper than a soft
 *   fill, with text held to 4.5:1 on it; the notice's action is a real button;
 * - secondary and tertiary text ([KelvinColors.ink2], [KelvinColors.ink3]) are darkened or
 *   lightened until they reach 4.5:1 on [strongest], the backdrop's most saturated colour, so
 *   captions written straight on the backdrop stay readable.
 */
@Composable
fun KelvinOnBackdrop(strongest: Color = Color.Unspecified, content: @Composable () -> Unit) {
    val base = Kelvin.colors
    val colors = remember(base, strongest) {
        if (strongest.isSpecified) {
            base.copy(ink2 = base.ink2.legibleOn(strongest), ink3 = base.ink3.legibleOn(strongest))
        } else {
            base
        }
    }
    CompositionLocalProvider(LocalKelvinOnBackdrop provides true, LocalKelvinColors provides colors, content = content)
}

/** A card's fill: [fill] on a plain page; the raised surface on a backdrop. Never an outline: Kelvin has no strokes. */
@Composable
internal fun Modifier.cardSurface(fill: Color, shape: Shape): Modifier =
    background(if (LocalKelvinOnBackdrop.current) backdropSurface() else fill, shape)

/** A plain card's surface on a backdrop. In dark the raised grey is close to a dark backdrop, so it is lifted a step. */
@Composable
internal fun backdropSurface(): Color {
    val c = Kelvin.colors
    return if (Kelvin.dark) lerp(c.raised, Color.White, 0.06f) else c.raised
}

/** A pastel card on a backdrop: its fill and the colour for toned text on it. */
internal class Pastel(val fill: Color, val ink: Color)

/**
 * A pastel of [color] over the backdrop card surface: clearly tinted, a step deeper than a soft fill so
 * it holds its own against a coloured backdrop with no outline, and toned text held to 4.5:1 on it.
 */
@Composable
internal fun pastelOf(color: Color): Pastel {
    val surface = backdropSurface()
    val dark = Kelvin.dark
    return remember(color, surface, dark) {
        val fill = color.copy(alpha = if (dark) 0.30f else 0.24f).compositeOver(surface)
        Pastel(fill, color.legibleOn(fill))
    }
}

/**
 * A notice on a backdrop that asks for something: a pastel of the accent, the title
 * in full ink, and its action a real button in the accent.
 */
@Composable
internal fun PastelNotice(
    title: String,
    message: String?,
    glyph: ImageVector,
    progress: Float?,
    actionText: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val c = Kelvin.colors
    val pastel = pastelOf(c.accent)
    // The button: the accent, shifted until its label reads at 4.5:1 on it.
    val button = remember(c.accent, c.onAccent) { c.accent.legibleOn(c.onAccent) }
    Row(
        modifier.fillMaxWidth().background(pastel.fill, KelvinShapes.md).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KelvinGlassTile(glyph, c.accent)
        Column(Modifier.weight(1f)) {
            KText(title, style = Kelvin.type.name.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            if (!message.isNullOrEmpty()) {
                KText(message, style = Kelvin.type.summary, color = c.ink.copy(alpha = 0.78f).compositeOver(pastel.fill))
            }
            if (progress != null) {
                KelvinProgressBar(
                    progress.takeIf { it >= 0f },
                    Modifier.padding(top = 10.dp),
                    color = pastel.ink,
                    track = pastel.ink.copy(alpha = 0.25f),
                )
            }
            if (actionText != null && onAction != null) {
                Box(
                    Modifier.padding(top = 12.dp).heightIn(min = 44.dp).background(button, CapsuleShape)
                        .kelvinPress(onAction, shape = CapsuleShape).padding(horizontal = 20.dp),
                    Alignment.Center,
                ) {
                    KText(actionText, style = Kelvin.type.action.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent)
                }
            }
        }
        trailing()
    }
}

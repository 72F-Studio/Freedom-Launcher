package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** What [KelvinTexturePicker] calls each material and how it says what it is. */
@Immutable
data class TextureLabels(
    val liquid: String = "Liquid glass",
    val liquidSummary: String = "Bends the page at its edges and catches the light",
    val frost: String = "Frost",
    val frostSummary: String = "Calm ground glass, easiest to read through",
)

/**
 * Choosing the glass: Liquid glass or Frost, side by side, each card a live sample of its
 * material (a glass pill over a field of the app's colour), so the choice is seen rather than
 * described. The chosen card carries the accent edge and its radio. Use it in onboarding (people
 * should know the choice exists) and at the top of Appearance's glass settings. The person's
 * Transparency setting still applies to whichever they pick; the samples show each at its own
 * default.
 */
@Composable
fun KelvinTexturePicker(
    texture: GlassTexture,
    onTexture: (GlassTexture) -> Unit,
    modifier: Modifier = Modifier,
    labels: TextureLabels = TextureLabels(),
) {
    val haptics = rememberKelvinHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple(GlassTexture.Liquid, labels.liquid, labels.liquidSummary),
            Triple(GlassTexture.Frost, labels.frost, labels.frostSummary),
        ).forEach { (t, name, summary) ->
            val chosen = t == texture
            val pick = { if (!chosen) { haptics.tick(); onTexture(t) } }
            val c = Kelvin.colors
            Column(
                Modifier.weight(1f)
                    .background(if (chosen) c.accentSoft else c.group, KelvinShapes.lg)
                    .then(if (chosen) Modifier.border(2.dp, c.accent, KelvinShapes.lg) else Modifier)
                    .kelvinPress(pick, role = Role.RadioButton, shape = KelvinShapes.lg)
                    .semantics(mergeDescendants = true) { selected = chosen }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextureSample(t)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    KelvinRadio(chosen, pick, Modifier.size(32.dp).clearAndSetSemantics {})
                    KText(name, style = Kelvin.type.name, maxLines = 1)
                }
                KText(summary, Modifier.padding(horizontal = 4.dp), Kelvin.type.caption, c.ink2)
            }
        }
    }
}

/** A glass pill over a field of colour, in [texture] at its default intensity, whatever the app's own setting. */
@Composable
private fun TextureSample(texture: GlassTexture) {
    val a11y = LocalKelvinAccessibility.current
    val level = if (texture == GlassTexture.Liquid) GlassLevel.Liquid else FrostLevel.Default
    val params = remember(texture, a11y) { level.resolve(a11y, texture, RefractionLevel.Default) }
    CompositionLocalProvider(LocalGlass provides params) {
        val c = Kelvin.colors
        val backdrop = rememberKelvinBackdrop()
        Box(Modifier.fillMaxWidth().height(112.dp).clip(KelvinShapes.md)) {
            // The field: the accent, its temperature partner and a few bars for the glass to bend.
            val accent = c.accent
            val partner = accent.temperaturePartner()
            Box(
                Modifier.fillMaxSize().kelvinBackdrop(backdrop).drawBehind {
                    drawRect(Brush.linearGradient(listOf(accent, partner), Offset.Zero, Offset(size.width, size.height)))
                    val bar = size.width / 9f
                    for (i in 0 until 9 step 2) {
                        drawRect(Color.White.copy(alpha = 0.28f), Offset(i * bar, 0f), androidx.compose.ui.geometry.Size(bar * 0.5f, size.height))
                    }
                    drawCircle(Color.White.copy(alpha = 0.55f), size.minDimension * 0.18f, Offset(size.width * 0.78f, size.height * 0.3f))
                },
            )
            Box(
                Modifier.align(Alignment.Center).size(width = 108.dp, height = 44.dp)
                    .glass(backdrop, CapsuleShape, GlassContent.Text, elevated = true),
                contentAlignment = Alignment.Center,
            ) { KText("Aa", style = Kelvin.type.name) }
        }
    }
}

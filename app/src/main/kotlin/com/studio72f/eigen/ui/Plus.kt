package com.studio72f.eigen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinSheet
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.tablerIcon

/**
 * What Eigen Plus adds. The free launcher is complete: these are only the extras. Gates sit where a thing is
 * chosen, never where it is shown, so anything already set up keeps working.
 */
object Plus {
    @JvmField val FREE_CLOCKS = setOf("ratio", "thin", "stacked")
    @JvmField val FREE_TEXTURES = setOf("ember", "silk")
    @JvmField val FREE_CARD_STYLES = setOf("classic")
    @JvmField val FREE_WIDGETS = setOf("media", "alarm", "agenda", "day", "battery")
    /** Contexts a free install can make besides everyday. */
    const val FREE_CONTEXTS = 1

    @JvmStatic fun clock(id: String) = id !in FREE_CLOCKS
    @JvmStatic fun texture(id: String) = id !in FREE_TEXTURES
    @JvmStatic fun cardStyle(id: String) = id !in FREE_CARD_STYLES
    @JvmStatic fun widget(id: String) = !id.startsWith("widget:") && id !in FREE_WIDGETS
}

/** A small mark beside something that comes with Plus. Hidden once Plus is on. */
@Composable
fun PlusMark(state: EigenState, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Kelvin.colors.accent) {
    if (!state.plus) KIcon(KelvinIcons.Sparkles, "Plus", modifier, size = 14.dp, color = color)
}

private val Layers by lazy { tablerIcon("stack-2", "M12 4l-8 4l8 4l8 -4l-8 -4", "M4 12l8 4l8 -4", "M4 16l8 4l8 -4") }
private val Message by lazy { tablerIcon("message", "M8 9h8", "M8 13h6", "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12") }
private val Clock by lazy { tablerIcon("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 7v5l3 3") }
private val Widgets by lazy { tablerIcon("layout-dashboard", "M5 4h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1", "M5 16h4a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1", "M15 12h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1", "M15 4h4a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1") }

/**
 * The Plus sheet: what it adds, one price, and Restore. Opened from Settings or by tapping something marked Plus,
 * in which case it names that thing first.
 */
@Composable
fun PlusSheet(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    // Keep the reason through the closing animation.
    var reason by remember { mutableStateOf("") }
    state.plusSheet?.let { reason = it }
    val c = Kelvin.colors
    KelvinSheet(state.plusSheet != null, { state.plusSheet = null }, "Eigen Plus", "Close", backdrop) {
        KText(
            when {
                state.plus -> "Plus is on. Thank you for backing Eigen."
                reason.isNotEmpty() -> "$reason is part of Plus. Eigen is free; Plus lets it change with your day."
                else -> "Eigen is free. Plus lets it change with your day."
            },
            Modifier.fillMaxWidth(), Kelvin.type.summary, c.ink2, align = TextAlign.Center,
        )
        KelvinGroup(gutter = 0.dp) {
            KelvinRow("Every context", summary = "Morning, work, night, headphones: as many as you like", icon = Layers, tile = KelvinTiles.Green)
            KelvinRow("Every clock and texture", summary = "Outline, Kesar split and Binary; all the textures", icon = Clock, tile = KelvinTiles.Violet)
            KelvinRow("Card styles", summary = "Stained and Lit cards, and wide cards", icon = KelvinIcons.Sparkles, tile = KelvinTiles.Orange)
            KelvinRow("More widgets", summary = "Screen time, Focus timer, World clocks, Calculator, Notes", icon = Widgets, tile = KelvinTiles.Blue)
            KelvinRow("Conversations", summary = "Every chat app in one timeline, beside home", icon = Message, tile = KelvinTiles.Teal)
        }
        if (state.plus) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                KIcon(KelvinIcons.Check, null, size = 18.dp, color = c.accent)
                KText("Plus is on", Modifier.padding(start = 6.dp), Kelvin.type.label)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KelvinButton("Get Plus · ${state.plusPrice}", host::buyPlus, Modifier.fillMaxWidth(), icon = KelvinIcons.Sparkles)
                KelvinButton("Restore purchase", host::restorePlus, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }
        }
        KText("One payment, no subscription. It stays with your Play account. Tips under Support Eigen don't unlock anything.",
            Modifier.fillMaxWidth(), Kelvin.type.caption, c.ink2, align = TextAlign.Center)
    }
}

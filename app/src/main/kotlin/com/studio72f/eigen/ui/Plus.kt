package com.studio72f.eigen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import com.studio72f.eigen.EigenBilling
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
 * What Eigen Plus adds. The free launcher is complete: these are only the extras. Besides the ids below, Plus gates
 * private apps, automation (commands, announcements, Tasker), gestures that open an app, the context triggers and
 * actions beyond time and the original four, and the living texture; each is checked where it is chosen. Gates sit where a thing is
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

/** A small mark beside something that comes with Plus: Kelvin's plan mark, the sparkle alone. Hidden once Plus is on. */
@Composable
fun PlusMark(state: EigenState, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Kelvin.colors.accent) {
    if (!state.plus) studio72f.kelvin.KelvinPlanMark("Plus", modifier, compact = true, color = color)
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
    val glyphs = listOf(
        KelvinIcons.Coffee to KelvinTiles.Orange, KelvinIcons.Gift to KelvinTiles.Green,
        KelvinIcons.Heart to KelvinTiles.Red, KelvinIcons.Star to KelvinTiles.Violet,
    )
    // Kelvin's plan sheet: why it opened, one price and Restore, what Plus adds, then tips.
    studio72f.kelvin.KelvinPlanSheet(
        visible = state.plusSheet != null,
        onDismiss = { state.plusSheet = null },
        closeLabel = "Close",
        backdrop = backdrop,
        plan = "Eigen Plus",
        title = if (state.plus) "Thank you" else "Let it change with your day",
        plans = listOf(studio72f.kelvin.PlanOption("plus", "Plus", price = state.plusPrice, period = "once")),
        selected = "plus",
        onSelect = {},
        buyText = "Get Plus · ${state.plusPrice}",
        onBuy = host::buyPlus,
        restoreText = "Restore purchase",
        onRestore = host::restorePlus,
        benefits = listOf(
            studio72f.kelvin.PlanBenefit(Layers, "Contexts that automate", "As many as you like, triggered by battery, Wi-Fi, a meeting or your car; change colour or run Tasker", KelvinTiles.Green),
            studio72f.kelvin.PlanBenefit(KelvinIcons.Lock, "Private apps", "Out of drawers and search, behind your fingerprint or PIN", KelvinTiles.Orange),
            studio72f.kelvin.PlanBenefit(KelvinIcons.Adjustments, "Automation", "Tasker and MacroDroid can hold contexts and hear when they change; gestures open any app", KelvinTiles.Teal),
            studio72f.kelvin.PlanBenefit(Clock, "Every clock and texture", "Outline, Kesar split and Binary; every texture, and textures that follow the day", KelvinTiles.Violet),
            studio72f.kelvin.PlanBenefit(KelvinIcons.Sparkles, "Card styles", "Stained and Lit cards, and wide cards", KelvinTiles.Orange),
            studio72f.kelvin.PlanBenefit(Widgets, "More widgets", "Screen time, Focus timer, World clocks, Calculator, Notes", KelvinTiles.Blue),
            studio72f.kelvin.PlanBenefit(Message, "Conversations", "Every chat app in one timeline, beside home", KelvinTiles.Teal),
        ),
        reason = if (reason.isNotEmpty() && !state.plus) "$reason is part of Plus. Eigen is free; Plus lets it change with your day."
            else if (!state.plus) "Eigen is free. Plus lets it change with your day." else null,
        note = "One payment, no subscription",
        owned = state.plus,
        ownedMessage = "Plus is on. Thank you for backing Eigen.",
        terms = "It stays with your Play account. Tips below don't unlock anything.",
    ) {
        // Tips sit last, together in their own card. They unlock nothing, so they come after everything Plus does.
        KelvinGroup(gutter = 0.dp) {
            studio72f.kelvin.KelvinTipJar(
                tiers = EigenBilling.TIERS.mapIndexed { i, tier ->
                    studio72f.kelvin.TipTier(tier.id, tier.title, tier.description, state.tipPrices[tier.id], tier.fallbackPrice, glyphs[i % 4].first, glyphs[i % 4].second)
                },
                onTip = host::tip,
                note = "Tips unlock nothing. They keep Eigen independent.",
            )
        }
    }
}

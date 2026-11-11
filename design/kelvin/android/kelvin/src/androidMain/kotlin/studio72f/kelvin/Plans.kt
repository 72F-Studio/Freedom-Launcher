package studio72f.kelvin

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * Plans and payments: everything a 72F app shows around buying a plan or leaving a tip, one way
 * in every app. Kelvin draws; the app keeps billing (prices come from the store, the app says what
 * a purchase does). The rules:
 *
 * - Offered where a feature is, never pushed: a plan opens from a tap on something it adds, or from
 *   Settings, and names that thing first ([KelvinPlanHero] reason).
 * - The free app is complete. Say what the plan adds, never what the free app lacks.
 * - One thing to tap: the purchase bar's button. Restore is always beside it.
 * - Prices are figures ("₹699", "₹99/mo"), set from the store's own formatted price; a preview
 *   price says it is one until the store answers.
 * - Tips unlock nothing, and say so. They sit after everything the plan does.
 * - Owned is a state, not a sale: a tick, the plan's name, and Manage for subscriptions.
 */

/**
 * The small mark beside something a plan adds ("Plus"): the accent sparkle and the plan's name.
 * [compact] is the sparkle alone, for marks inside a chip or on a swatch, in [color]. Show nothing
 * once the plan is owned; TalkBack reads the plan's name.
 */
@Composable
fun KelvinPlanMark(plan: String, modifier: Modifier = Modifier, compact: Boolean = false, color: Color = Color.Unspecified) {
    val c = Kelvin.colors
    val ink = if (color != Color.Unspecified) color else c.accent.legibleOn(c.group)
    if (compact) {
        KIcon(KelvinIcons.Sparkles, plan, modifier, size = 14.dp, color = ink)
        return
    }
    Row(
        modifier.background(c.accentSoft, CapsuleShape).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = plan },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        KIcon(KelvinIcons.Sparkles, null, size = 12.dp, color = ink)
        KText(plan, style = Kelvin.type.eyebrow, color = c.onAccentSoft, maxLines = 1)
    }
}

/**
 * The top of a plan sheet or screen: the plan's glass tile and name as an eyebrow, a calm title,
 * and the [reason] it opened ("Picture search is part of Plus."), on the aurora.
 */
@Composable
fun KelvinPlanHero(
    plan: String,
    title: String,
    modifier: Modifier = Modifier,
    reason: String? = null,
    icon: ImageVector = KelvinIcons.Crown,
) {
    val c = Kelvin.colors
    Column(
        modifier.fillMaxWidth().background(c.group, KelvinShapes.lg).horizon(0.9f, inset = true).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KelvinGlassTile(icon, c.accent, size = 36.dp)
            KelvinEyebrow(plan, color = c.onAccentSoft)
        }
        KText(title, Modifier.semantics { heading() }, Kelvin.type.display)
        if (reason != null) KText(reason, style = Kelvin.type.body, color = c.ink2)
    }
}

/**
 * One plan in a [KelvinPlanPicker]. [price] is the store's formatted price (null shows
 * [previewPrice] until it answers); [period] its unit ("/mo", "/yr", "once"). [badge] marks one
 * plan ("Best value", "Save 40%"); [note] is a line in the accent (an introductory offer and when it
 * ends). [current] marks the plan the person has; it cannot be chosen again.
 */
@Immutable
data class PlanOption(
    val id: String,
    val name: String,
    val detail: String? = null,
    val price: String? = null,
    val period: String? = null,
    val previewPrice: String? = null,
    val badge: String? = null,
    val note: String? = null,
    val current: Boolean = false,
    val currentLabel: String? = null,
)

/**
 * Choosing between plans (lifetime and monthly, monthly and yearly): a card each, a radio drawn in
 * it so the choice never rests on colour, the price as a figure at the end. The chosen card takes
 * the accent's soft fill and an accent edge. Pair it with a [KelvinPurchaseBar] that buys the chosen one.
 */
@Composable
fun KelvinPlanPicker(plans: List<PlanOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val currentPlanDefault = stringResource(R.string.kelvin_current_plan)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plans.forEach { plan ->
            val chosen = plan.id == selected
            val currentPlan = plan.currentLabel ?: currentPlanDefault
            val pick = { if (!chosen && !plan.current) { haptics.tick(); onSelect(plan.id) } }
            Box {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        .background(if (chosen) c.accentSoft else c.group, KelvinShapes.lg)
                        .then(if (chosen) Modifier.border(2.dp, c.accent, KelvinShapes.lg) else Modifier)
                        .kelvinPress(pick, enabled = !plan.current, role = Role.RadioButton, shape = KelvinShapes.lg)
                        .semantics(mergeDescendants = true) {
                            this.selected = chosen
                            if (plan.current) stateDescription = currentPlan
                        }
                        .padding(start = 8.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KelvinRadio(chosen, pick, Modifier.clearAndSetSemantics {}, enabled = !plan.current)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KText(plan.name, style = Kelvin.type.name)
                        if (plan.detail != null) KText(plan.detail, style = Kelvin.type.summary, color = c.ink2)
                        if (plan.current) KText(currentPlan, style = Kelvin.type.caption, color = c.positive.legibleOn(c.group))
                        else if (plan.note != null) KText(plan.note, style = Kelvin.type.caption, color = c.onAccentSoft)
                    }
                    PlanPrice(plan)
                }
                if (plan.badge != null) {
                    KelvinPill(plan.badge, Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = (-10).dp), tone = Tone.Accent)
                }
            }
        }
    }
}

@Composable
private fun PlanPrice(plan: PlanOption) {
    val c = Kelvin.colors
    val price = plan.price ?: plan.previewPrice ?: "—"
    Column(horizontalAlignment = Alignment.End) {
        // "₹99/mo" in the figure grammar: the number light and large, ₹ and / in the accent.
        val unit = plan.period
        KelvinFigure(if (unit != null && unit.startsWith("/")) price + unit else price, style = Kelvin.type.figure.copy(fontSize = Kelvin.type.title.fontSize))
        if (unit != null && !unit.startsWith("/")) KText(unit, style = Kelvin.type.caption, color = c.ink2)
    }
}

/**
 * The foot of a plan sheet or screen: a line about the chosen plan ([note]: "One payment, yours to
 * keep"), the one button to tap ([buyText], with the price in it), and Restore beside it. [loading]
 * sweeps a bar and holds the button; [unavailable] replaces the button with a notice that says why
 * and what to do (the store is not reachable). Sits on the canvas above the navigation bar.
 */
@Composable
fun KelvinPurchaseBar(
    buyText: String,
    onBuy: () -> Unit,
    restoreText: String,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    unavailable: String? = null,
    backdrop: KelvinBackdrop? = null,
) {
    val c = Kelvin.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (note != null) KText(note, Modifier.fillMaxWidth(), Kelvin.type.summary, c.ink2, align = TextAlign.Center)
        if (unavailable != null) KelvinNotice(unavailable, null, tone = Tone.Caution, gutter = 0.dp)
        else KelvinButton(buyText, onBuy, Modifier.fillMaxWidth(), enabled = enabled && !loading, large = true, backdrop = backdrop)
        if (loading) KelvinProgressBar(null, Modifier.padding(horizontal = 24.dp))
        KelvinButton(restoreText, onRestore, kind = ButtonKind.Text, enabled = !loading)
    }
}

/** One thing a plan adds, for [KelvinPlanBenefits]: its glyph and tile colour, a name and a line. */
@Immutable
data class PlanBenefit(val icon: ImageVector, val title: String, val summary: String? = null, val tile: Color = KelvinTiles.Blue)

/** What a plan adds, as rows with glass tiles in a group: the thing first, then what it does for you. */
@Composable
fun KelvinPlanBenefits(benefits: List<PlanBenefit>, modifier: Modifier = Modifier, gutter: androidx.compose.ui.unit.Dp = 16.dp) {
    KelvinGroup(modifier, gutter = gutter) {
        benefits.forEach { b -> KelvinRow(b.title, summary = b.summary, icon = b.icon, tile = b.tile) }
    }
}

// The branded plan card is a bright surface in both themes with dark text, so it reads the same at night: a light
// wash of the app's accent, never the aurora (whose glow muddies text in dark mode).
private val BrandInk = Color(0xFF10161F)
private val BrandInk2 = Color(0xFF3B4756)
private val BrandStatusFill = Color(0xFFD9F4E1)
private val BrandStatusInk = Color(0xFF0B6B2B)

private fun brandSurface(accent: Color): Brush = Brush.linearGradient(
    listOf(lerp(accent, Color.White, 0.80f), lerp(accent, Color.White, 0.93f)),
)

/** A small capsule on the bright card: a glyph and a word in dark ink. */
@Composable
private fun BrandChip(text: String, icon: ImageVector, fill: Color, ink: Color) {
    Row(
        Modifier.background(fill, CapsuleShape).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        KIcon(icon, null, size = 12.dp, color = ink)
        KText(text, style = Kelvin.type.eyebrow, color = ink, maxLines = 1)
    }
}

// Controls on the bright card keep its dark ink in both themes; the theme's own text and plain buttons would turn
// light at night and vanish on it.
@Composable
private fun BrandTextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = KelvinSizes.touch).kelvinPress(onClick, shape = CapsuleShape, role = Role.Button)
            .padding(horizontal = KelvinSizes.textButtonPadding),
        contentAlignment = Alignment.Center,
    ) { KText(text, style = Kelvin.type.action, color = BrandInk, maxLines = 1) }
}

@Composable
private fun BrandDismiss(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(KelvinSizes.touch).kelvinPress(onClick, shape = CapsuleShape, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) { KIcon(KelvinIcons.X, label, size = KelvinSizes.icon, color = BrandInk2) }
}

/**
 * A plan the person has: the rosette, "[plan] is on", a line of thanks or what it covers, and
 * [manageText] for a subscription (opens the store's subscriptions page). A state, not a sale.
 *
 * The card wears the app's own look ([brand], the default): a bright wash of the accent with dark ink (the same in
 * light and dark), and an accent tile, so owning the plan is as much the app's as being offered it. Colour then only
 * marks status, in the [statusText] chip beside the name ("Active"). Without [brand] it is the status green.
 */
@Composable
fun KelvinPlanOwned(
    plan: String,
    message: String,
    modifier: Modifier = Modifier,
    manageText: String? = null,
    onManage: (() -> Unit)? = null,
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
    brand: Boolean = true,
    statusText: String? = null,
) {
    val c = Kelvin.colors
    val ground = if (brand) {
        Modifier.background(brandSurface(c.accent), KelvinShapes.lg)
    } else {
        Modifier.background(c.positiveSoft, KelvinShapes.lg)
    }
    Row(
        modifier.padding(horizontal = gutter).fillMaxWidth().then(ground).padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KelvinGlassTile(KelvinIcons.Rosette, if (brand) c.accent else c.positive)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KText(plan, style = Kelvin.type.name, color = if (brand) BrandInk else c.ink)
                if (brand && statusText != null) BrandChip(statusText, KelvinIcons.Check, BrandStatusFill, BrandStatusInk)
            }
            KText(message, style = Kelvin.type.summary, color = if (brand) BrandInk2 else c.ink2)
        }
        if (manageText != null && onManage != null) {
            if (brand) BrandTextAction(manageText, onManage) else KelvinButton(manageText, onManage, kind = ButtonKind.Text)
        }
    }
}

/**
 * A card that offers a plan where it fits (on home, in Settings, under a feature): the plan's
 * mark, what one feature does, and an action that opens it or the plan. [onDismiss] adds an x that
 * hides it (the app decides for how long). Never a popup; never shown to someone who has the plan. It uses the same
 * bright accent wash and dark ink as [KelvinPlanOwned], with a solid primary action ([brand], the default); without
 * [brand] it is a plain group card with a tinted action.
 */
@Composable
fun KelvinPlanCta(
    plan: String,
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "",
    icon: ImageVector = KelvinIcons.Sparkles,
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
    brand: Boolean = true,
) {
    val c = Kelvin.colors
    val ground = if (brand) Modifier.background(brandSurface(c.accent), KelvinShapes.lg) else Modifier.background(c.group, KelvinShapes.lg).horizon(0.6f, inset = true)
    Row(
        modifier.padding(horizontal = gutter).fillMaxWidth().then(ground)
            .kelvinPress(onAction, shape = KelvinShapes.lg)
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = if (onDismiss != null) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KelvinGlassTile(icon, c.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (brand) BrandChip(plan, KelvinIcons.Sparkles, Color.White.copy(alpha = 0.7f), BrandInk) else KelvinPlanMark(plan)
            KText(title, style = Kelvin.type.name, color = if (brand) BrandInk else c.ink)
            KText(message, style = Kelvin.type.summary, color = if (brand) BrandInk2 else c.ink2)
            KelvinButton(actionText, onAction, Modifier.padding(top = 6.dp), kind = if (brand) ButtonKind.Primary else ButtonKind.Tinted)
        }
        if (onDismiss != null) {
            if (brand) BrandDismiss(dismissLabel, onDismiss, Modifier.offset(y = (-12).dp))
            else KelvinIconButton(KelvinIcons.X, dismissLabel, onDismiss, Modifier.offset(y = (-12).dp), kind = IconButtonKind.Plain)
        }
    }
}

/** One tip in a [KelvinTipJar]: the store's [price] (or [fallbackPrice] until it answers), a name, a line, a glyph. */
@Immutable
data class TipTier(
    val id: String,
    val title: String,
    val description: String? = null,
    val price: String? = null,
    val fallbackPrice: String? = null,
    val icon: ImageVector = KelvinIcons.Coffee,
    val tile: Color = KelvinTiles.Orange,
)

enum class TipJarLayout {
    /** A row per tip, its price on an accent button at the end: tips with a line each. */
    List,
    /** Two tiles a row, glyph, name and price: four or so short tips in a little room. */
    Grid,
}

/**
 * Optional tips: a [note] that says they unlock nothing and what they do, then the tiers, then a
 * [footnote] (who handles payment). A tip that cannot be bought yet (no price) dims and holds.
 * Put it in a [KelvinGroup] of its own under a [KelvinSectionHeader] ("Support Bento"), after
 * everything a plan does.
 */
@Composable
fun KelvinTipJar(
    tiers: List<TipTier>,
    onTip: (String) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    footnote: String? = null,
    layout: TipJarLayout = TipJarLayout.List,
) {
    val c = Kelvin.colors
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (note != null) KText(note, style = Kelvin.type.summary, color = c.ink2)
        when (layout) {
            TipJarLayout.List -> tiers.forEach { tier ->
                val price = tier.price ?: tier.fallbackPrice
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KelvinGlassTile(tier.icon, tier.tile)
                    Column(Modifier.weight(1f)) {
                        KText(tier.title, style = Kelvin.type.rowTitle)
                        if (tier.description != null) KText(tier.description, style = Kelvin.type.summary, color = c.ink2)
                    }
                    // Accent-filled: the one thing in the row to tap.
                    KelvinButton(price ?: "—", { onTip(tier.id) }, Modifier.widthIn(min = 96.dp), enabled = tier.price != null || tier.fallbackPrice != null)
                }
            }
            TipJarLayout.Grid -> tiers.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { tier ->
                        val price = tier.price ?: tier.fallbackPrice
                        Column(
                            Modifier.weight(1f).background(c.raised, KelvinShapes.md)
                                .kelvinPress({ onTip(tier.id) }, enabled = price != null, shape = KelvinShapes.md, onClickLabel = tier.title)
                                .padding(vertical = 14.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            KelvinGlassTile(tier.icon, tier.tile)
                            KText(tier.title, style = Kelvin.type.label, maxLines = 1)
                            KelvinFigure(price ?: "—", style = Kelvin.type.figure)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        if (footnote != null) KText(footnote, style = Kelvin.type.caption, color = c.ink2)
    }
}

/** The small print at the foot of a plan: renewal, refunds, where it is kept. Captions, centred. */
@Composable
fun KelvinPlanTerms(text: String, modifier: Modifier = Modifier) =
    KText(text, modifier.fillMaxWidth().padding(horizontal = 24.dp), Kelvin.type.caption, Kelvin.colors.ink2, align = TextAlign.Center)

/**
 * A whole plan in a sheet, built from the pieces above in their order: the hero with its reason,
 * the owned state or the plan picker, the purchase bar, what the plan adds, then [extra] (tips,
 * a link to supporters) and the terms. Apps with one price leave [plans] at one option and the
 * picker is skipped. For a full screen, put the same pieces in a page instead.
 */
@Composable
fun KelvinPlanSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    closeLabel: String,
    backdrop: KelvinBackdrop?,
    plan: String,
    title: String,
    plans: List<PlanOption>,
    selected: String,
    onSelect: (String) -> Unit,
    buyText: String,
    onBuy: () -> Unit,
    restoreText: String,
    onRestore: () -> Unit,
    benefits: List<PlanBenefit>,
    reason: String? = null,
    note: String? = null,
    loading: Boolean = false,
    unavailable: String? = null,
    owned: Boolean = false,
    ownedMessage: String = "",
    manageText: String? = null,
    onManage: (() -> Unit)? = null,
    terms: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    KelvinSheet(visible, onDismiss, plan, closeLabel, backdrop) {
        KelvinPlanHero(plan, title, reason = reason)
        if (owned) KelvinPlanOwned(plan, ownedMessage, manageText = manageText, onManage = onManage, gutter = 0.dp)
        else {
            if (plans.size > 1) KelvinPlanPicker(plans, selected, onSelect, Modifier.padding(top = 4.dp))
            KelvinPurchaseBar(buyText, onBuy, restoreText, onRestore, note = note, loading = loading, unavailable = unavailable, backdrop = backdrop)
        }
        KelvinPlanBenefits(benefits, gutter = 0.dp)
        extra()
        if (terms != null) KelvinPlanTerms(terms)
    }
}

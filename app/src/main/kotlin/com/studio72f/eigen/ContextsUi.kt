package com.studio72f.eigen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studio72f.eigen.ui.EigenState
import com.studio72f.eigen.ui.eigenPress
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.IconButtonKind
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinDetailHeader
import studio72f.kelvin.KelvinDialog
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinPill
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinSectionHeader
import studio72f.kelvin.KelvinSegmented
import studio72f.kelvin.KelvinSwitch
import studio72f.kelvin.KelvinTextField
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.Tone
import studio72f.kelvin.kelvinPill
import studio72f.kelvin.kelvinPillInk
import studio72f.kelvin.tablerIcon

/**
 * What the contexts editor needs from the activity, which owns the rules, their storage, widget hosting and the
 * platform time picker. The editor edits [ContextRules.Rule] fields directly, then calls [changed].
 */
interface ContextsHost {
    fun contexts(): List<ContextRules.Rule>
    fun activeId(): String?
    fun changed()
    fun addContext()
    fun holdMenu()
    fun hold(rule: ContextRules.Rule)
    fun delete(rule: ContextRules.Rule)
    fun pickTime(rule: ContextRules.Rule, start: Boolean)
    fun addItem(rule: ContextRules.Rule)
    fun removeItem(rule: ContextRules.Rule, item: String)
    fun itemLabel(item: String): String
    fun drawers(): List<String>
}

private val ArrowUp by lazy { tablerIcon("arrow-up", "M12 5l0 14", "M18 11l-6 -6", "M6 11l6 -6") }
private val ArrowDown by lazy { tablerIcon("arrow-down", "M12 5l0 14", "M18 13l-6 6", "M6 13l6 6") }
private val Layers by lazy { tablerIcon("stack-2", "M12 4l-8 4l8 4l8 -4l-8 -4", "M4 12l8 4l8 -4", "M4 16l8 4l8 -4") }

private fun sentence(s: String) = s.replaceFirstChar { it.uppercaseChar() }

/** "Hidden" items: the clock is always on home and the widgets page always shows what is playing. */
private fun shown(item: String) = item != "clock" && item != "media"

/** Contexts and widgets: the list, and the editor for one context, as Kelvin pages that slide in from the right. */
@Composable
fun ContextsOverlay(state: EigenState, host: ContextsHost) {
    val spatial = Kelvin.motion.spatial<androidx.compose.ui.unit.IntOffset>()
    val gentle = Kelvin.motion.gentle<Float>()
    AnimatedVisibility(
        state.contextsPage != null,
        enter = slideInHorizontally(spatial) { it } + fadeIn(gentle),
        exit = slideOutHorizontally(spatial) { it } + fadeOut(gentle),
    ) {
        Column(Modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
            AnimatedContent(
                state.contextsPage ?: "",
                transitionSpec = {
                    if (targetState.isNotEmpty()) slideInHorizontally(spatial) { it } togetherWith slideOutHorizontally(spatial) { -it / 3 }
                    else slideInHorizontally(spatial) { -it / 3 } togetherWith slideOutHorizontally(spatial) { it }
                },
                label = "contexts page",
            ) { page ->
                val rule = host.contexts().firstOrNull { it.id == page }
                if (page.isEmpty() || rule == null) ContextList(state, host) else ContextEditor(state, host, rule)
            }
        }
    }
}

@Composable
private fun ContextList(state: EigenState, host: ContextsHost) {
    @Suppress("UNUSED_VARIABLE") val version = state.contextsVersion
    val c = Kelvin.colors
    Column(Modifier.fillMaxSize()) {
        KelvinDetailHeader("Contexts and widgets", { state.contextsPage = null }, "Back",
            summary = "The first context that matches decides what the widgets page shows and which drawer home opens.")
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Spacer(Modifier.height(8.dp))
            KelvinGroup {
                val active = host.activeId()
                for (r in host.contexts()) {
                    val items = r.widgets.count(::shown)
                    val summary = (if (r.enabled || r.fallback) sentence(ContextRules.describe(r)) else "Off") + " · " +
                        (if (items == 0) "no widgets" else "$items widget" + if (items == 1) "" else "s")
                    KelvinRow(
                        sentence(r.name), summary = summary, icon = Layers,
                        tile = if (r.enabled || r.fallback) KelvinTiles.Green else KelvinTiles.Grey,
                        chevron = true, onClick = { state.contextsPage = r.id },
                        trailing = if (r.id == active) ({ KelvinPill("Active", tone = Tone.Accent) }) else null,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            KelvinGroup {
                KelvinRow("Add a context", summary = "Morning, work, headphones, night, charging or blank", icon = KelvinIcons.Plus, tile = KelvinTiles.Blue, onClick = host::addContext)
                KelvinRow("Hold a context", summary = "Use one for a while, or go back to automatic", icon = KelvinIcons.Lock, tile = KelvinTiles.Orange, onClick = host::holdMenu)
            }
            Spacer(Modifier.height(24.dp))
            KText("Contexts are checked only while home is on screen. Nothing runs in the background.", Modifier.padding(horizontal = 32.dp), Kelvin.type.summary, c.ink2)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContextEditor(state: EigenState, host: ContextsHost, r: ContextRules.Rule) {
    @Suppress("UNUSED_VARIABLE") val version = state.contextsVersion
    fun edit(change: () -> Unit) { change(); host.changed() }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        KelvinDetailHeader(sentence(r.name), { state.contextsPage = "" }, "Back",
            summary = if (r.fallback) "Used when no other context matches" else sentence(ContextRules.describe(r)))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            if (!r.fallback) {
                var name by remember(r.id) { mutableStateOf(sentence(r.name)) }
                KelvinGroup(contentPadding = 16.dp) {
                    KelvinTextField(name, { name = it.take(24); if (name.isNotBlank()) edit { r.name = name.trim() } }, "Name")
                }
                Spacer(Modifier.height(8.dp))
                KelvinGroup {
                    KelvinRow("Use this context", trailing = { KelvinSwitch(r.enabled, { on -> edit { r.enabled = on } }) })
                }
                Section("When")
                KelvinGroup(contentPadding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KText("Days", style = Kelvin.type.label)
                        Pills(listOf("Every day") + ContextRules.dayNames.map(::sentence), { i -> if (i == 0) r.days == 0 else r.days != 0 && r.days and (1 shl (i - 1)) != 0 }) { i ->
                            edit { if (i == 0) r.days = 0 else { r.days = r.days xor (1 shl (i - 1)); if (r.days == 127) r.days = 0 } }
                        }
                        KText("Time", style = Kelvin.type.label)
                        Pills(
                            listOf("All day", if (r.allDay()) "From" else "From ${ContextRules.time(r.start)}", if (r.allDay()) "Until" else "Until ${ContextRules.time(r.end)}"),
                            { i -> if (i == 0) r.allDay() else !r.allDay() },
                        ) { i -> if (i == 0) edit { r.start = 0; r.end = 0 } else host.pickTime(r, i == 1) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                KelvinGroup {
                    Condition("Headphones", r.headphones) { edit { r.headphones = it } }
                    Condition("Charging", r.charging) { edit { r.charging = it } }
                    Condition("Do not disturb", r.dnd) { edit { r.dnd = it } }
                    Condition("Media playing", r.playing) { edit { r.playing = it } }
                }
            }
            Section("Then")
            KelvinGroup(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KText("Open this drawer on home", style = Kelvin.type.label)
                    val targets = listOf("") + host.drawers()
                    Pills(targets.map { if (it.isEmpty()) "None" else sentence(it) }, { i -> r.category == targets[i] }) { i -> edit { r.category = targets[i] } }
                }
            }
            Section("On the widgets page")
            KelvinGroup {
                val items = r.widgets.filter(::shown)
                items.forEachIndexed { i, item ->
                    KelvinRow(sentence(host.itemLabel(item)), trailing = {
                        Row {
                            if (i > 0) KelvinIconButton(ArrowUp, "Move up", {
                                edit { val at = r.widgets.indexOf(item); val prev = r.widgets.indexOf(items[i - 1]); java.util.Collections.swap(r.widgets, at, prev) }
                            }, kind = IconButtonKind.Plain)
                            KelvinIconButton(KelvinIcons.X, "Remove", { host.removeItem(r, item); host.changed() }, kind = IconButtonKind.Plain)
                        }
                    })
                }
                KelvinRow("Add a widget", summary = "Next alarm, agenda, Smartspacer or any app widget", icon = KelvinIcons.Plus, tile = KelvinTiles.Blue, onClick = { host.addItem(r) })
            }
            if (!r.fallback) {
                Section("Priority")
                val all = host.contexts()
                val at = all.indexOf(r)
                KelvinGroup {
                    KelvinRow("Checked ${at + 1} of ${all.size - 1}", summary = "The first context that matches wins", trailing = {
                        Row {
                            KelvinIconButton(ArrowUp, "Check earlier", { edit { val p = all.indexOf(r); if (p > 0) java.util.Collections.swap(all, p, p - 1) } }, kind = IconButtonKind.Plain, enabled = at > 0)
                            KelvinIconButton(ArrowDown, "Check later", { edit { val p = all.indexOf(r); if (p < all.size - 2) java.util.Collections.swap(all, p, p + 1) } }, kind = IconButtonKind.Plain, enabled = at < all.size - 2)
                        }
                    })
                    KelvinRow("Use it now", summary = "For the next hour, whatever the conditions", icon = KelvinIcons.Lock, tile = KelvinTiles.Orange, onClick = { host.hold(r) })
                    KelvinRow("Delete context", danger = true, onClick = { confirmDelete = true })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    KelvinDialog(
        confirmDelete, { confirmDelete = false }, "Delete ${sentence(r.name)}?",
        "Its widgets are removed too. Other contexts are not affected.",
        "Delete", { confirmDelete = false; state.contextsPage = ""; host.delete(r) }, "Cancel", null, destructive = true,
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    KelvinSectionHeader(title)
}

/** A sideways row of pills; the chosen ones are accent pebbles. */
@Composable
private fun Pills(labels: List<String>, selected: (Int) -> Boolean, onClick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = selected(i)
            KText(label, Modifier.height(36.dp).kelvinPill(on, null).eigenPress({ onClick(i) }, shape = CapsuleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                Kelvin.type.label, kelvinPillInk(on), maxLines = 1)
        }
    }
}

@Composable
private fun Condition(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        KText(label, Modifier.weight(1f), Kelvin.type.rowTitle)
        KelvinSegmented(listOf("Any", "Yes", "No"), value, onChange, Modifier.width(186.dp))
    }
}

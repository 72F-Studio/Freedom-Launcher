package com.studio72f.eigen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.studio72f.eigen.EigenAccessibility
import com.studio72f.eigen.IconPacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinDetailHeader
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinNotice
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinSection
import studio72f.kelvin.KelvinSheet
import studio72f.kelvin.KelvinSwitch
import studio72f.kelvin.KelvinTextField
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.Tone
import studio72f.kelvin.kelvinPill
import studio72f.kelvin.kelvinPillInk

/** Settings pages for the power features, sliding in over Settings like the contexts pages. */
@Composable
fun PowerOverlay(state: EigenState, host: EigenHost) {
    val spatial = Kelvin.motion.spatial<androidx.compose.ui.unit.IntOffset>()
    val gentle = Kelvin.motion.gentle<Float>()
    // Keep the page through the closing slide.
    var page by remember { mutableStateOf("") }
    state.powerPage?.let { page = it }
    AnimatedVisibility(state.powerPage != null, enter = slideInHorizontally(spatial) { it } + fadeIn(gentle), exit = slideOutHorizontally(spatial) { it } + fadeOut(gentle)) {
        LaunchedEffect(page) { state.accessibilityOn = EigenAccessibility.enabled(host.context()) }
        Column(Modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
            val (title, summary) = when (page) {
                "gestures" -> "Gestures" to "What each gesture on home does."
                "search" -> "Search" to "What the search capsule looks through. Everything is found on this phone."
                "icons" -> "Icons" to "Shape and style for every app; hold an app to change just its icon."
                "private" -> "Private apps" to "Kept out of drawers and search, behind your screen lock."
                "automation" -> "Automation" to "Let Tasker, MacroDroid and similar apps work with your contexts."
                "assistant" -> "Assistant" to "Which model answers you. It runs on this phone."
                else -> "Share your setup" to "Your look, drawers and contexts in one file to keep or share."
            }
            KelvinDetailHeader(title, { state.powerPage = null }, "Back", summary = summary)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Spacer(Modifier.height(8.dp))
                when (page) {
                    "gestures" -> GesturesPage(state, host)
                    "search" -> SearchPage(state, host)
                    "icons" -> IconsPage(state, host)
                    "private" -> PrivatePage(state, host)
                    "automation" -> AutomationPage(state, host)
                    "assistant" -> AssistantPage(state, host)
                    else -> SetupsPage(state, host)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Note(text: String) = KText(text, Modifier.padding(horizontal = 32.dp, vertical = 12.dp), Kelvin.type.summary, Kelvin.colors.ink2)

@Composable
private fun GesturesPage(state: EigenState, host: EigenHost) {
    val needsService = Gestures.ALL.any { g -> Gestures.ACTIONS.firstOrNull { it.id == Gestures.actionFor(state, g.id) }?.system == true }
    if (needsService && !state.accessibilityOn) {
        KelvinNotice("Some gestures need Eigen's gesture service", "Lock, notifications, quick settings and recents go through it. It reads nothing on your screen.",
            Modifier.padding(bottom = 16.dp), Tone.Caution, actionText = "Turn on", onAction = { state.accessibilityAsk = "These gestures" })
    }
    KelvinGroup {
        for (g in Gestures.ALL) {
            val action = Gestures.actionFor(state, g.id)
            KelvinRow(g.name, value = Gestures.actionName(state, action), chevron = true, onClick = {
                val names = Gestures.ACTIONS.map { it.name } + (if (state.plus) "Open an app…" else "Open an app… · Plus")
                host.choose(g.name, names) { i ->
                    if (i < Gestures.ACTIONS.size) host.setGesture(g.id, Gestures.ACTIONS[i].id)
                    else if (!state.plus) state.plusSheet = "Opening an app with a gesture"
                    else {
                        val apps = state.allApps.sortedBy { it.title().lowercase() }
                        host.choose("Open which app?", apps.map { it.title() }) { n -> host.setGesture(g.id, "app:" + apps[n].id) }
                    }
                }
            })
        }
    }
    Note("Swipe down set to anything but drawers still lets you open them one by one. Two-finger swipes work anywhere on home.")
}

@Composable
private fun SearchPage(state: EigenState, host: EigenHost) {
    KelvinGroup {
        KelvinRow("Apps", summary = "Always on, work profile included", trailing = { KelvinSwitch(true, {}, enabled = false) })
        SourceRow(state, host, "shortcuts", "App shortcuts", "Compose, new tab, a contact's chat: every app's shortcuts")
        SourceRow(state, host, "contacts", "Contacts", if (state.contactsAllowed) "Names from your contacts; opens their card" else "Needs contacts permission; asked when you turn it on")
        SourceRow(state, host, "settings", "Android settings", "Wi-Fi, Bluetooth, battery, developer options and more")
        SourceRow(state, host, "maths", "Sums and units", "12 × 4, 18% of 2400, 5 km in miles, 72 f to c")
        SourceRow(state, host, "web", "Web, Maps and Play", "Hands your words to those apps when nothing on the phone matches")
    }
    Note("Search runs on the phone. Contacts are read only while you type and never stored. Web, Maps and Play are other apps; Eigen sends nothing you type anywhere.")
}

@Composable
private fun SourceRow(state: EigenState, host: EigenHost, id: String, title: String, summary: String) {
    val on = id in state.searchSources && (id != "contacts" || state.contactsAllowed)
    KelvinRow(title, summary = summary, trailing = { KelvinSwitch(on, { v -> if (v && id == "contacts" && !state.contactsAllowed) host.requestContacts(); host.setSearchSource(id, v) }) })
}

@Composable
private fun IconsPage(state: EigenState, host: EigenHost) {
    val accent = Kelvin.colors.accent.toArgb()
    KelvinSection("Shape") {
        CarouselRow(16.dp, Modifier.padding(horizontal = 16.dp)) {
            IconShaper.SHAPES.forEachIndexed { i, shape ->
                val chosen = state.iconShape == shape
                val preview = remember(shape, accent) { IconShaper.preview(shape, accent).asImageBitmap() }
                Column(
                    Modifier.clip(studio72f.kelvin.ContinuousShape(20.dp)).kelvinPill(chosen, null).eigenPress({ host.setIconShape(shape) }, shape = studio72f.kelvin.ContinuousShape(20.dp), label = IconShaper.NAMES[i])
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(preview, null, Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    KText(IconShaper.NAMES[i].substringBefore(" "), style = Kelvin.type.caption, color = kelvinPillInk(chosen), maxLines = 1)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    KelvinGroup {
        KelvinRow("Themed icons", summary = "The app's own monochrome glyph in your colour, Android 13 and later. Others stay as they are.",
            trailing = { KelvinSwitch(state.themedIcons, { host.setPref("themed_icons", it) }) })
        KelvinRow("Icon pack", value = host.iconPackName(), chevron = true, onClick = host::chooseIconPack)
    }
    Note("Shapes apply to adaptive icons, which is nearly every app made since 2018. Hold an app, then More → Change icon to pick any icon from a pack for it.")
}

@Composable
private fun PrivatePage(state: EigenState, host: EigenHost) {
    KelvinGroup {
        KelvinRow("Open private apps", summary = if (state.privateIds.isEmpty()) "None yet" else "${state.privateIds.size} app" + (if (state.privateIds.size == 1) "" else "s") + " · unlock to see them",
            icon = KelvinIcons.Lock, tile = KelvinTiles.Orange, chevron = true, onClick = host::openPrivateApps)
    }
    Note("To make an app private, hold it and choose More → Make private. It leaves its drawer, the dock and search, and its notification dots stay hidden. Set a gesture to Private apps to reach them quickly.")
    Note("Your screen lock guards them: Android checks it, and Eigen never sees your PIN or fingerprint. Private apps are still installed: they show in Android's own settings.")
}

@Composable
private fun AutomationPage(state: EigenState, host: EigenHost) {
    KelvinGroup {
        KelvinRow("Accept commands", summary = "Apps can hold a context, release it or change the colour",
            trailing = { KelvinSwitch(state.automationIn, { v -> if (!state.plus) state.plusSheet = "Automation" else host.setPref("automation_in", v) }) })
        KelvinRow("Announce context changes", summary = "Tells automation apps when the context changes, with its name",
            trailing = { KelvinSwitch(state.automationOut, { v -> if (!state.plus) state.plusSheet = "Automation" else host.setPref("automation_out", v) }) })
    }
    Note("Each context can also run a Tasker task when it starts: open a context and set it under Then.")
    KelvinSection("For your automation app") {
        KelvinGroup {
            KelvinRow("Hold a context", summary = "Broadcast com.studio72f.eigen.action.HOLD_CONTEXT\nExtras: context (name), minutes (optional, default 60)")
            KelvinRow("Back to automatic", summary = "Broadcast com.studio72f.eigen.action.RELEASE_CONTEXT")
            KelvinRow("Change the colour", summary = "Broadcast com.studio72f.eigen.action.SET_COLOUR\nExtra: colour (Kesar, Fire, Ladoo, Haldi or Greyscale)")
            KelvinRow("When the context changes", summary = "Eigen sends com.studio72f.eigen.event.CONTEXT_CHANGED\nExtras: context, previous")
        }
    }
    Note("Commands take effect the next time home is on screen. Eigen runs nothing in the background.")
}

@Composable
private fun SetupsPage(state: EigenState, host: EigenHost) {
    KelvinGroup {
        KelvinRow("Export your setup", summary = "Saves a .eigen file", icon = KelvinIcons.Upload, tile = KelvinTiles.Green, onClick = host::exportSetup)
        KelvinRow("Import a setup", summary = "See what changes before anything is applied", icon = KelvinIcons.Download, tile = KelvinTiles.Blue, onClick = host::importSetup)
    }
    Note("A setup holds your colour, clock, glass, icon shape, gestures, drawer names and order, which drawer each app is in, and your contexts. It never holds your photo, notes, messages, app widgets or private apps, so it's safe to post.")
    Note("Apps that aren't installed on the other phone are skipped. Backup and restore is still the way to move everything to a new phone.")
}

// ---- Sheets

/** What the gesture service can and can't do, shown before Android's accessibility settings (Play's disclosure). */
@Composable
fun AccessibilitySheet(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    var name by remember { mutableStateOf("") }
    state.accessibilityAsk?.let { name = it }
    KelvinSheet(state.accessibilityAsk != null, { state.accessibilityAsk = null }, "Turn on gesture service", "Not now", backdrop) {
        KText("$name needs Eigen's gesture service, which Android lists under Accessibility.", style = Kelvin.type.body)
        Spacer(Modifier.height(12.dp))
        KelvinGroup(gutter = 0.dp) {
            KelvinRow("What it does", summary = "Locks the screen, opens notifications or quick settings, or shows recent apps, when you make a gesture on home.", icon = KelvinIcons.Check, tile = KelvinTiles.Green)
            KelvinRow("What it never does", summary = "Read your screen, watch other apps, record what you type, or send anything anywhere. Eigen only goes online to download an assistant model you pick.", icon = KelvinIcons.Shield, tile = KelvinTiles.Blue)
        }
        Spacer(Modifier.height(12.dp))
        KText("In the next screen, choose Eigen Launcher and switch it on. You can turn it off there at any time.", style = Kelvin.type.summary, color = Kelvin.colors.ink2)
        KelvinButton("Open accessibility settings", { state.accessibilityAsk = null; host.openAccessibilitySettings() }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** Private apps, shown only after the screen lock said yes. Cleared when home leaves the screen. */
@Composable
fun PrivateAppsSheet(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    KelvinSheet(state.privateOpen, { state.privateOpen = false }, "Private apps", "Done", backdrop) {
        val apps = state.privateApps
        if (apps.isEmpty()) KText("Nothing here yet. Hold an app and choose More → Make private.", style = Kelvin.type.body, color = Kelvin.colors.ink2)
        else LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps, key = { it.id }) { app ->
                Column(
                    Modifier.clip(studio72f.kelvin.ContinuousShape(16.dp))
                        .eigenPress({ state.privateOpen = false; host.launch(app) }, { host.choose(app.title(), listOf("Stop keeping private")) { host.setPrivate(app, false) } }, studio72f.kelvin.ContinuousShape(16.dp), app.title())
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, 44.dp, state.monoIcons)
                    Spacer(Modifier.height(6.dp))
                    KText(app.title(), style = Kelvin.type.caption.copy(fontSize = 12.sp), maxLines = 1, align = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        KText("Hold an app to stop keeping it private.", style = Kelvin.type.caption, color = Kelvin.colors.ink2)
    }
}

/** Every icon in a pack, searchable; tapping one gives it to the app. */
@Composable
fun IconPickerSheet(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val context = LocalContext.current
    val app = state.iconPickerApp
    val pack = state.iconPickerPack
    var filter by remember(app, pack) { mutableStateOf("") }
    val loaded by produceState<IconPacks?>(null, pack) {
        value = if (pack == null) null else withContext(Dispatchers.IO) {
            try { IconPacks(context, pack) } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.iconPickerApp = null
                    Problems.show(state, context, Problems.ICN_PACK, "icons:open-pack", "Icon pack couldn't be read",
                        "Update the pack or choose another one. Your icons haven't changed.",
                        "The pack has no appfilter mapping Eigen understands, or Android couldn't open its resources.", e)
                }
                null
            }
        }
    }
    KelvinSheet(app != null, { state.iconPickerApp = null }, "Icon for ${app?.title() ?: ""}", "Cancel", backdrop) {
        KelvinTextField(filter, { filter = it.lowercase().take(40) }, "Find an icon")
        Spacer(Modifier.height(12.dp))
        val names = remember(loaded, filter) { loaded?.names()?.filter { filter.isBlank() || it.contains(filter.replace(' ', '_')) }?.take(400) ?: emptyList() }
        if (loaded == null) KText("Reading the pack…", style = Kelvin.type.summary, color = Kelvin.colors.ink2)
        else LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.heightIn(max = 380.dp)) {
            items(names, key = { it }) { name ->
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
                    value = withContext(Dispatchers.IO) { runCatching { loaded?.byName(name)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                }
                Box(Modifier.size(64.dp).clip(studio72f.kelvin.ContinuousShape(14.dp)).eigenPress({ if (app != null) host.setIconOverride(app, pack, name); state.iconPickerApp = null }, shape = studio72f.kelvin.ContinuousShape(14.dp), label = name), contentAlignment = Alignment.Center) {
                    bitmap?.let { Image(it, name, Modifier.size(48.dp)) }
                }
            }
        }
        if (app != null) KelvinButton("Use the app's own icon", { host.setIconOverride(app, null, null); state.iconPickerApp = null }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = ButtonKind.Secondary)
    }
}

/** What importing a setup will change, applied only on Apply. */
@Composable
fun SetupPreviewSheet(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    var shown by remember { mutableStateOf<SetupPreview?>(null) }
    state.setupPreview?.let { shown = it }
    val p = shown
    KelvinSheet(state.setupPreview != null, { state.setupPreview = null }, "Import ${p?.name ?: "setup"}?", "Cancel", backdrop) {
        if (p != null) {
            KText("This replaces:", style = Kelvin.type.label)
            Spacer(Modifier.height(8.dp))
            KelvinGroup(gutter = 0.dp) { for (line in p.changes) KelvinRow(line) }
            Spacer(Modifier.height(8.dp))
            KText("Your apps, photo, widgets, notes and private apps stay as they are.", style = Kelvin.type.summary, color = Kelvin.colors.ink2)
            KelvinButton("Apply", { state.setupPreview = null; host.applySetup(p) }, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

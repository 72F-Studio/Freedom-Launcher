package com.studio72f.eigen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio72f.eigen.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinSheet
import studio72f.kelvin.kelvinPill
import studio72f.kelvin.kelvinPillInk
import studio72f.kelvin.tablerIcon
import studio72f.kelvin.horizon
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.KelvinSwitch
import studio72f.kelvin.KelvinStatCard
import studio72f.kelvin.KelvinSegmented
import studio72f.kelvin.KelvinSectionHeader
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinNotice
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.IconButtonKind

private val Uninstall by lazy { tablerIcon("trash", "M4 7l16 0", "M10 11l0 6", "M14 11l0 6", "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12", "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3") }
private val Shortcuts by lazy { tablerIcon("bolt", "M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11") }
private val Wide by lazy { tablerIcon("arrows-horizontal", "M7 8l-4 4l4 4", "M17 8l4 4l-4 4", "M3 12l18 0") }
private val Dock by lazy { tablerIcon("layout-bottombar", "M4 4m0 2a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z", "M4 15l16 0") }

private val STYLES = listOf("classic" to "Classic", "marker" to "Marker", "bright" to "Bright")

/**
 * An app's dashboard as a Kelvin detail page: back circle and the app's name, then groups of cards. Today (time and
 * storage, hour by hour), On home (how its card looks and where it sits), and App (shortcuts, drawer, info, uninstall).
 */
@Composable
fun DashboardOverlay(state: EigenState, host: EigenHost) {
    AnimatedVisibility(
        state.dashboardApp != null,
        enter = fadeIn(Kelvin.motion.gentle()) + slideInVertically(Kelvin.motion.spatial()) { it / 8 },
        exit = fadeOut(Kelvin.motion.gentle()) + slideOutVertically(Kelvin.motion.spatial()) { it / 8 },
    ) {
        val app = remember { state.dashboardApp } ?: return@AnimatedVisibility
        Dashboard(state, host, app)
    }
}

@Composable
private fun Dashboard(state: EigenState, host: EigenHost, app: MainActivity.App) {
    val c = Kelvin.colors
    val context = LocalContext.current
    val pkg = app.component.packageName
    var hourly by remember { mutableStateOf(IntArray(24)) }
    var storage by remember { mutableStateOf<Long?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(app, state.resumeCount) {
        withContext(Dispatchers.IO) {
            hourly = Usage.hourly(context, pkg); storage = Usage.storageBytes(context, pkg, app.user); version = Usage.version(context, pkg)
        }
    }
    val today = state.usageMinutes[pkg] ?: hourly.sum()
    val style = state.cardStyles[app.id] ?: "classic"
    val wide = app.id in state.wideCards
    val docked = app.id in state.dockIds
    // The header scrolls with the page, so content never slides under a hard edge.
    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().horizon().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            KelvinIconButton(KelvinIcons.Back, "Back", { state.dashboardApp = null }, kind = IconButtonKind.Raised)
            Row(Modifier.padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 56.dp, state.monoIcons)
                Column(Modifier.padding(start = 14.dp)) {
                    KText(app.title(), style = Kelvin.type.headline, maxLines = 2)
                    KText(listOfNotNull(version?.let { "Version $it" }, host.drawerOf(app).replaceFirstChar { it.uppercaseChar() }).joinToString(" · "), style = Kelvin.type.summary, color = c.ink2, maxLines = 1)
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            KelvinSectionHeader("Today")
            if (!state.usageAccess) {
                KelvinNotice("See your screen time", "Allow usage access to see how long you spend in each app, hour by hour. It stays on this phone.",
                    Modifier.padding(horizontal = 16.dp), actionText = "Allow usage access", onAction = host::requestUsageAccess)
            } else {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KelvinStatCard("Time today", Usage.formatMinutes(today), Modifier.weight(1f), detail = "Most in one hour: ${Usage.formatMinutes(hourly.max())}")
                    KelvinStatCard("Storage", storage?.let(Usage::formatBytes) ?: "—", Modifier.weight(1f), detail = "App, data and cache")
                }
                Spacer(Modifier.height(8.dp))
                KelvinGroup(contentPadding = 20.dp) {
                    Column {
                        Bars(hourly, Modifier.fillMaxWidth().height(120.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { for (h in listOf("0:00", "6:00", "12:00", "18:00")) KText(h, Modifier.weight(1f), Kelvin.type.caption, c.ink2) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            KelvinSectionHeader("On home")
            KelvinGroup(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // A live preview of the card as it will look on home.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AppCard(state, host, app, 0, Modifier.width(if (wide) 168.dp else 84.dp), interactive = false)
                    }
                    KelvinSegmented(STYLES.map { it.second }, STYLES.indexOfFirst { it.first == style }.coerceAtLeast(0), { i -> host.setCardStyle(app, STYLES[i].first) })
                }
            }
            Spacer(Modifier.height(8.dp))
            KelvinGroup {
                KelvinRow("Wide card", summary = "Takes two columns, with the name beside the icon", icon = Wide, tile = KelvinTiles.Violet, trailing = { KelvinSwitch(wide, { host.setWide(app, it) }) })
                KelvinRow("In the dock", summary = if (docked) "Above the search bar" else "Up to four apps", icon = Dock, tile = KelvinTiles.Blue, trailing = { KelvinSwitch(docked, { host.toggleDock(app) }) })
                KelvinRow("Favourite", summary = "Shown first on home", icon = KelvinIcons.Heart, tile = KelvinTiles.Red, trailing = { KelvinSwitch(host.isFavorite(app), { host.toggleFavorite(app) }) })
            }
            Spacer(Modifier.height(24.dp))
            KelvinSectionHeader("App")
            KelvinGroup {
                KelvinRow("Shortcuts and more", icon = Shortcuts, tile = KelvinTiles.Orange, chevron = true, onClick = { host.appMenu(app) })
                KelvinRow("Move to drawer", value = host.drawerOf(app).replaceFirstChar { it.uppercaseChar() }, icon = KelvinIcons.Folder, tile = KelvinTiles.Teal, chevron = true, onClick = { host.moveApp(app) })
                KelvinRow("App info", icon = KelvinIcons.Info, tile = KelvinTiles.Grey, chevron = true, onClick = { host.appInfo(app) })
                KelvinRow("Uninstall", danger = true, onClick = { host.uninstall(app) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 24 hourly bars in the accent; empty hours show as faint dots on the baseline. */
@Composable
private fun Bars(values: IntArray, modifier: Modifier) {
    val accent = Kelvin.colors.accent; val faint = Kelvin.colors.ink.copy(alpha = 0.15f)
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val slot = size.width / 24f; val bar = slot * 0.42f
        for (i in 0 until 24) {
            val x = i * slot + (slot - bar) / 2f
            if (values[i] == 0) { drawCircle(faint, bar / 2f, Offset(x + bar / 2f, size.height - bar / 2f)); continue }
            val h = (values[i] / max.toFloat()) * size.height
            drawRoundRect(accent, Offset(x, size.height - h), Size(bar, h), CornerRadius(bar / 2f))
        }
    }
}

/** The clock style sheet from a long-press on the clock: a live preview, the styles, and a way to all settings. */
@Composable
fun ClockSheet(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    KelvinSheet(state.clockSheet, { state.clockSheet = false }, "Clock style", "Close", backdrop) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            HomeClock(state.clockStyle, onTime = {}, onDate = {}, scale = 0.6f)
        }
        CarouselRow(16.dp) {
            ClockStyles.IDS.forEachIndexed { i, id ->
                val on = state.clockStyle == id
                KText(ClockStyles.NAMES[i], Modifier.height(48.dp).kelvinPill(on, backdrop).eigenPress({ host.setClock(id) }, shape = studio72f.kelvin.CapsuleShape).padding(horizontal = 18.dp, vertical = 14.dp),
                    Kelvin.type.label, kelvinPillInk(on), maxLines = 1)
            }
        }
        KelvinButton("Launcher settings", { state.clockSheet = false; host.openSettings() }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = ButtonKind.Secondary, icon = KelvinIcons.Settings)
    }
}

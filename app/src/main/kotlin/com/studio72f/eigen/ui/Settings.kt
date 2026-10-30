package com.studio72f.eigen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import studio72f.kelvin.GlassRangeLabels
import studio72f.kelvin.IconButtonKind
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinGlassControl
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinPageHeader
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinSectionHeader
import studio72f.kelvin.KelvinSwitch
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.tablerIcon

/**
 * One settings row, built by the activity. [icon] and [tile] are names (see [settingsIcon], [settingsTile]);
 * a non-null [toggle] shows a switch that runs [action] when flipped.
 */
data class SettingsRow(
    val title: String,
    val summary: String?,
    val value: String?,
    val icon: String,
    val tile: String,
    val toggle: Boolean?,
    val action: Runnable?,
)

data class SettingsSection(val title: String, val rows: List<SettingsRow>)

private fun settingsTile(name: String): Color = when (name) {
    "orange" -> KelvinTiles.Orange
    "green" -> KelvinTiles.Green
    "red" -> KelvinTiles.Red
    "violet" -> KelvinTiles.Violet
    "teal" -> KelvinTiles.Teal
    "grey" -> KelvinTiles.Grey
    else -> KelvinTiles.Blue
}

// Tabler outline paths (MIT), unmodified, for glyphs KelvinIcons does not carry.
private val icons: Map<String, ImageVector> by lazy {
    mapOf(
        "palette" to tablerIcon("palette", "M12 21a9 9 0 0 1 0 -18c4.97 0 9 3.582 9 8c0 1.06 -.474 2.078 -1.318 2.828c-.844 .75 -1.989 1.172 -3.182 1.172h-2.5a2 2 0 0 0 -1 3.75a1.3 1.3 0 0 1 -1 2.25", "M8.5 10.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M12.5 7.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", "M16.5 10.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0"),
        "clock" to tablerIcon("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 7v5l3 3"),
        "bell" to tablerIcon("bell", "M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6", "M9 17v1a3 3 0 0 0 6 0v-1"),
        "eye-off" to tablerIcon("eye-off", "M10.585 10.587a2 2 0 0 0 2.829 2.828", "M16.681 16.673a8.717 8.717 0 0 1 -4.681 1.327c-3.6 0 -6.6 -2 -9 -6c1.272 -2.12 2.712 -3.678 4.32 -4.674m2.86 -1.146a9.055 9.055 0 0 1 1.82 -.18c3.6 0 6.6 2 9 6c-.666 1.11 -1.379 2.067 -2.138 2.87", "M3 3l18 18"),
        "layers" to tablerIcon("stack-2", "M12 4l-8 4l8 4l8 -4l-8 -4", "M4 12l8 4l8 -4", "M4 16l8 4l8 -4"),
        "sort" to tablerIcon("arrows-sort", "M3 9l4 -4l4 4m-4 -4v14", "M21 15l-4 4l-4 -4m4 4v-14"),
        "archive" to tablerIcon("archive", "M3 4m0 2a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v0a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2z", "M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-10", "M10 12l4 0"),
        "text" to tablerIcon("typography", "M4 20l3 0", "M14 20l7 0", "M6.9 15l6.9 0", "M10.2 6.3l5.8 13.7", "M5 20l6 -16l2 0l7 16"),
        "apps" to tablerIcon("apps", "M4 4m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M4 14m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M14 14m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M14 7l6 0", "M17 4l0 6"),
        "message" to tablerIcon("message", "M8 9h8", "M8 13h6", "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12"),
    )
}

private fun settingsIcon(name: String): ImageVector = icons[name] ?: when (name) {
    "photo" -> KelvinIcons.Photo
    "calendar" -> KelvinIcons.Calendar
    "sparkles" -> KelvinIcons.Sparkles
    "folder" -> KelvinIcons.Folder
    "home" -> KelvinIcons.Home
    "heart" -> KelvinIcons.Heart
    "refresh" -> KelvinIcons.Refresh
    "lock" -> KelvinIcons.Lock
    "adjust" -> KelvinIcons.Adjustments
    "info" -> KelvinIcons.Info
    else -> KelvinIcons.Settings
}

/**
 * Settings, as a Kelvin top-level page over the blurred wallpaper: large title on the Horizon, then groups of
 * rows with full-colour tiles. The Glass slider lives inline under Look. Rows are built by the activity.
 */
@Composable
fun SettingsOverlay(state: EigenState, host: EigenHost) {
    AnimatedVisibility(
        state.settingsOpen,
        enter = slideInVertically(Kelvin.motion.spatial()) { it / 6 } + fadeIn(Kelvin.motion.gentle()),
        exit = slideOutVertically(Kelvin.motion.spatial()) { it / 6 } + fadeOut(Kelvin.motion.gentle()),
    ) {
        val c = Kelvin.colors
        Column(Modifier.fillMaxSize().background(c.canvas)) {
            KelvinPageHeader(AnnotatedString("Settings"), actions = {
                KelvinIconButton(KelvinIcons.X, "Close settings", { state.settingsOpen = false }, kind = IconButtonKind.Raised)
            })
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                for ((i, section) in state.settings.withIndex()) {
                    KelvinSectionHeader(section.title, Modifier.padding(top = if (i == 0) 4.dp else 0.dp))
                    KelvinGroup {
                        for (row in section.rows) SettingsRowView(row)
                        if (section.title == "Look") GlassRow(state, host)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRowView(row: SettingsRow) {
    KelvinRow(
        row.title,
        summary = row.summary,
        icon = settingsIcon(row.icon),
        tile = settingsTile(row.tile),
        value = row.value,
        chevron = row.toggle == null && row.action != null,
        onClick = row.action?.let { a -> { a.run() } },
        trailing = row.toggle?.let { on -> { KelvinSwitch(on, { row.action?.run() }) } },
    )
}

@Composable
private fun GlassRow(state: EigenState, host: EigenHost) {
    var level by remember { mutableFloatStateOf(state.glass) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        studio72f.kelvin.KText("Glass", style = Kelvin.type.rowTitle)
        KelvinGlassControl(
            level, { level = it; state.glass = it },
            GlassRangeLabels("Opaque", "Clear", "Clear glass has no contrast protection. Text over photos may be hard to read."),
            "Glass",
            onLevelChangeFinished = { host.setGlassLevel(level) },
        )
    }
}

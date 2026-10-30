package com.studio72f.eigen.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.Onest
import studio72f.kelvin.kelvinPill
import studio72f.kelvin.kelvinPillInk
import studio72f.kelvin.tablerIcon

private const val PAGES = 5
private val Message by lazy { tablerIcon("message", "M8 9h8", "M8 13h6", "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12") }
private val Hand by lazy { tablerIcon("hand-finger", "M8 13v-8.5a1.5 1.5 0 0 1 3 0v7.5", "M11 11.5v-2a1.5 1.5 0 1 1 3 0v2.5", "M14 10.5a1.5 1.5 0 0 1 3 0v1.5", "M17 11.5a1.5 1.5 0 0 1 3 0v4.5a6 6 0 0 1 -6 6h-2h.208a6 6 0 0 1 -5.012 -2.7a69.74 69.74 0 0 1 -.196 -.3c-.312 -.479 -1.407 -2.388 -3.286 -5.728a1.5 1.5 0 0 1 .536 -2.022a1.867 1.867 0 0 1 2.28 .28l1.47 1.47") }
private val Apps by lazy { tablerIcon("apps", "M4 4m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M4 14m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M14 14m0 1a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1z", "M14 7l6 0", "M17 4l0 6") }

/**
 * The first-run tour, over the live (blurred) wallpaper so it already looks like home: progress dashes and Skip
 * on top, one idea per page in the middle, one pill at the bottom. Choices apply immediately.
 */
@Composable
fun TourOverlay(state: EigenState, host: EigenHost) {
    AnimatedVisibility(state.tourPage >= 0, enter = fadeIn(Kelvin.motion.gentle()), exit = fadeOut(Kelvin.motion.gentle())) {
        val page = state.tourPage.coerceIn(0, PAGES - 1)
        val c = Kelvin.colors
        val spatial = Kelvin.motion.spatial<androidx.compose.ui.unit.IntOffset>()
        val gentle = Kelvin.motion.gentle<Float>()
        Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until PAGES) {
                        val w by animateDpAsState(if (i == page) 28.dp else 10.dp, Kelvin.motion.spatial(), label = "dash")
                        val ink by animateColorAsState(if (i <= page) c.accent else c.ink.copy(alpha = 0.22f), Kelvin.motion.gentle(), label = "dash ink")
                        Box(Modifier.width(w).height(6.dp).clip(CapsuleShape).background(ink))
                    }
                }
                // Skip keeps its height on the last page so the dashes stay put.
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { if (page < PAGES - 1) KelvinButton("Skip", host::finishTour, kind = ButtonKind.Text) }
            }
            AnimatedContent(
                page, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState > initialState
                    ((slideInHorizontally(spatial) { if (forward) it / 3 else -it / 3 } + fadeIn(gentle)) togetherWith
                        (slideOutHorizontally(spatial) { if (forward) -it / 3 else it / 3 } + fadeOut(gentle))).using(SizeTransform(clip = false))
                },
                label = "tour page",
            ) { p ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    when (p) {
                        0 -> Welcome()
                        1 -> ColourPage(state, host)
                        2 -> ClockPage(state, host)
                        3 -> GesturesPage()
                        else -> FinishPage(state, host)
                    }
                }
            }
            val last = page == PAGES - 1
            val needsHome = page == 0 && !host.isDefaultHome()
            KelvinButton(
                when { needsHome -> "Make Eigen your home"; last -> "Start using Eigen"; else -> "Continue" },
                {
                    if (needsHome) host.makeDefaultHome()
                    if (last) host.finishTour() else state.tourPage = page + 1
                },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp), large = true,
            )
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                if (page > 0) KelvinButton("Back", { state.tourPage = page - 1 }, kind = ButtonKind.Text)
                else if (needsHome) KelvinButton("Not now", { state.tourPage = 1 }, kind = ButtonKind.Text)
            }
        }
    }
}

@Composable
private fun Title(text: String, body: String) {
    KText(text, style = Kelvin.type.display.copy(fontSize = 34.sp, lineHeight = 40.sp), align = TextAlign.Center)
    KText(body, Modifier.padding(top = 10.dp, bottom = 28.dp), Kelvin.type.body.copy(fontSize = 16.sp, lineHeight = 23.sp), Kelvin.colors.ink2, align = TextAlign.Center)
}

@Composable
private fun Welcome() {
    val c = Kelvin.colors
    androidx.compose.foundation.text.BasicText(
        buildAnnotatedString { append("Eigen"); withStyle(SpanStyle(color = c.accent)) { append(".") } },
        style = androidx.compose.ui.text.TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 76.sp, letterSpacing = (-0.05).em, color = c.ink),
    )
    KText(
        "A calm home screen. Your apps in drawers, your messages on the left, your widgets on the right, and an assistant that runs on this phone.",
        Modifier.padding(top = 16.dp), Kelvin.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp), c.ink2, align = TextAlign.Center,
    )
}

@Composable
private fun ColourPage(state: EigenState, host: EigenHost) {
    Title("Pick a colour", "One colour marks what you can act on. Greyscale keeps everything quiet, icons and wallpaper too.")
    val names = listOf("Kesar", "Fire", "Ladoo", "Haldi", "Greyscale")
    val colours = listOf(0xFFFF8A1F, 0xFFFF5A1F, 0xFFF7A21B, 0xFFFFC233, 0xFFECECEC).map { it.toInt() }
    CarouselRow(24.dp, spacing = 14.dp) {
        names.forEachIndexed { i, name ->
            val on = state.accent == colours[i]
            Column(Modifier.eigenPress({ host.setColour(i) }, shape = CircleShape, label = name), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).clip(CircleShape).border(if (on) 3.dp else 0.dp, Kelvin.colors.ink, CircleShape).padding(if (on) 6.dp else 0.dp).clip(CircleShape).background(Color(colours[i])))
                KText(name, Modifier.padding(top = 8.dp), Kelvin.type.caption, if (on) Kelvin.colors.ink else Kelvin.colors.ink2)
            }
        }
    }
}

@Composable
private fun ClockPage(state: EigenState, host: EigenHost) {
    Title("Pick a clock", "It sits at the top of home. Tap it any time to see your alarms.")
    HomeClock(state.clockStyle, onTime = {}, onDate = {}, scale = 0.62f)
    Spacer(Modifier.height(24.dp))
    CarouselRow(24.dp) {
        ClockStyles.IDS.forEachIndexed { i, id ->
            val on = state.clockStyle == id
            KText(ClockStyles.NAMES[i], Modifier.height(48.dp).kelvinPill(on, null).eigenPress({ host.setClock(id) }, shape = CapsuleShape).padding(horizontal = 18.dp, vertical = 14.dp),
                Kelvin.type.label, kelvinPillInk(on), maxLines = 1)
        }
    }
}

@Composable
private fun GesturesPage() {
    Title("How it moves", "Everything is a swipe or a tap from home.")
    KelvinGroup(gutter = 0.dp) {
        Hint(Apps, KelvinTiles.Blue, "Drawers", "Tap a name to open it. Hold an app for its shortcuts.")
        Hint(Message, KelvinTiles.Green, "Swipe right", "Conversations, and the assistant.")
        Hint(KelvinIcons.Calendar, KelvinTiles.Red, "Swipe left", "Your widgets, agenda and what's playing.")
        Hint(KelvinIcons.Search, KelvinTiles.Violet, "Search", "Type an app's name, or ask a question.")
        Hint(Hand, KelvinTiles.Orange, "Hold home", "Settings, wallpaper, clock and colour.")
    }
}

@Composable
private fun Hint(icon: ImageVector, tile: Color, title: String, body: String) = KelvinRow(title, summary = body, icon = icon, tile = tile)

@Composable
private fun FinishPage(state: EigenState, host: EigenHost) {
    Title("Make it yours", "Both are optional, and both can be changed later in Settings.")
    KelvinGroup(gutter = 0.dp) {
        KelvinRow("Choose a wallpaper", summary = "Eigen blurs it beautifully, inside the launcher only", icon = KelvinIcons.Photo, tile = KelvinTiles.Blue, chevron = true, onClick = host::chooseWallpaper)
        KelvinRow("Allow notification access", summary = "For Conversations, replies and app dots. Nothing leaves your phone.", icon = Message, tile = KelvinTiles.Green,
            value = if (state.notificationAccess) "On" else null, chevron = !state.notificationAccess, onClick = host::openNotificationAccess)
    }
}

package com.studio72f.eigen.ui

import androidx.core.content.edit
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.studio72f.eigen.MainActivity
import kotlinx.coroutines.delay
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.IconButtonKind
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.tablerIcon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Eigen's own widgets, offered in Add widget. The ids are saved in the page's order. */
object EigenWidgets {
    @JvmField val IDS = arrayOf("media", "alarm", "agenda", "day", "battery", "screen", "focus", "clocks", "calculator", "notes")
    @JvmField val NAMES = arrayOf("Now playing", "Next alarm", "Agenda", "Day", "Battery", "Screen time", "Focus timer", "World clocks", "Calculator", "Notes")
    /** What a new install shows, top to bottom. */
    @JvmField val DEFAULT = "agenda,alarm,battery,day,media"
}

private val CardShape = ContinuousShape(24.dp)
private val Prev by lazy { tablerIcon("player-skip-back", "M20 5v14l-12 -7l12 -7", "M4 5l0 14") }
private val Next by lazy { tablerIcon("player-skip-forward", "M4 5v14l12 -7l-12 -7", "M20 5l0 14") }
private val Pause by lazy { tablerIcon("player-pause", "M6 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -12", "M14 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -12") }
private val Alarm by lazy { tablerIcon("alarm", "M5 13a7 7 0 1 0 14 0a7 7 0 0 0 -14 0", "M12 10l0 3l2 0", "M7 4l-2.75 2", "M17 4l2.75 2") }
private val Bolt by lazy { tablerIcon("bolt", "M13 3l0 7l6 0l-8 11l0 -7l-6 0l8 -11") }

/** Lets the page's small cards reach the state for their frost without threading it through every call. */
private val LocalEigenState = androidx.compose.runtime.staticCompositionLocalOf<EigenState?> { null }

/**
 * The widgets page on the right. Widgets stack from the bottom, by the thumb, and grow upward. Hold any of them, an
 * app's widget too, to lift it: drag up or down to reorder, or onto Remove at the top to take it off.
 */
@Composable
fun WidgetsPage(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) =
    androidx.compose.runtime.CompositionLocalProvider(LocalEigenState provides state) { WidgetsContent(state, host) }

@Composable
private fun WidgetsContent(state: EigenState, host: EigenHost) {
    val c = Kelvin.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val list = rememberLazyListState()
    // The order while a widget is held; saved when it is let go.
    var working by remember(state.widgetOrder) { mutableStateOf(state.widgetOrder) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var overRemove by remember { mutableStateOf(false) }
    val removeReach = with(density) { 56.dp.toPx() }
    val shown = working.filter { id -> id != "media" || state.mediaTitle != null || dragging == id }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KText("Widgets", Modifier.weight(1f), Kelvin.type.display)
                KelvinButton("Add widget", host::addWidget, kind = ButtonKind.Tinted, icon = KelvinIcons.Plus)
            }
            // While a widget is held, the top of the page becomes the place to drop it to remove it.
            RemoveTarget(dragging != null, overRemove, Modifier.matchParentSize())
        }
        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                KText("No widgets yet. Add Eigen's own or any widget from your apps with Add widget.", Modifier.padding(16.dp), Kelvin.type.summary, c.ink2, align = TextAlign.Center)
            }
        } else LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
        ) {
            items(shown, key = { it }) { id ->
                val held = dragging == id
                Box(
                    Modifier
                        .then(if (held) Modifier else Modifier.animateItem(fadeInSpec = Kelvin.motion.gentle(), fadeOutSpec = Kelvin.motion.gentle(), placementSpec = Kelvin.motion.spatial()))
                        .zIndex(if (held) 1f else 0f)
                        .graphicsLayer {
                            if (held) { translationY = dragOffset; scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 24.dp.toPx(); shape = CardShape; clip = false; alpha = if (overRemove) 0.6f else 1f }
                        }
                        .holdToDrag(
                            onStart = { working = shown; dragging = id; dragOffset = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrag = { dy ->
                                dragOffset += dy
                                val items = list.layoutInfo.visibleItemsInfo
                                val me = items.firstOrNull { it.key == id }
                                if (me != null) {
                                    val centre = me.offset + me.size / 2f + dragOffset
                                    val target = items.firstOrNull { it.key != id && centre > it.offset && centre < it.offset + it.size }
                                    if (target != null) {
                                        val from = working.indexOf(id); val to = working.indexOf(target.key as String)
                                        if (from >= 0 && to >= 0) {
                                            working = working.toMutableList().apply { add(to, removeAt(from)) }
                                            dragOffset -= (target.offset - me.offset).toFloat()
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    val nowOver = me.offset + dragOffset < -removeReach / 2f
                                    if (nowOver != overRemove) { overRemove = nowOver; if (nowOver) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
                                }
                            },
                            onEnd = {
                                val id0 = dragging
                                if (overRemove && id0 != null) { working = working - id0; host.removeWidget(id0) } else host.setWidgetOrder(working)
                                dragging = null; dragOffset = 0f; overRemove = false
                            },
                        ),
                ) { Widget(state, host, id) }
            }
        }
    }
}

@Composable
private fun RemoveTarget(visible: Boolean, over: Boolean, modifier: Modifier) {
    val c = Kelvin.colors
    AnimatedVisibility(visible, modifier, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
        val fill by animateColorAsState(if (over) c.critical else c.criticalSoft, Kelvin.motion.gentle(), label = "remove")
        Row(Modifier.fillMaxSize().clip(CapsuleShape).background(fill), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            KIcon(KelvinIcons.Trash, null, size = 22.dp, color = if (over) c.onAccent else c.critical)
            KText(if (over) "Let go to remove" else "Drop here to remove", Modifier.padding(start = 10.dp), Kelvin.type.label, if (over) c.onAccent else c.critical)
        }
    }
}

@Composable
private fun Widget(state: EigenState, host: EigenHost, id: String) {
    when (id) {
        "media" -> NowPlaying(state, host)
        "alarm" -> AlarmCard(state, host)
        "agenda" -> Card { Label("Today"); KText(state.agenda ?: "Nothing in the next 24 hours", style = Kelvin.type.body, maxLines = 4) }
        "day" -> DayProgress()
        "battery" -> Battery()
        "screen" -> ScreenTime(state, host)
        "focus" -> Focus(state)
        "clocks" -> Clocks()
        "calculator" -> Calculator()
        "notes" -> Notes()
        else -> state.hostedWidgets[id]?.let { HostedWidget(state, it) } ?: Card { Label("Widget unavailable"); KText("Its app may have been removed. Hold to remove it.", style = Kelvin.type.summary, color = Kelvin.colors.ink2) }
    }
}

/**
 * Hold, then drag: after a long press without moving, the gesture is this element's (an app widget underneath gets a
 * cancel), and vertical movement is reported until the finger lifts. Taps and scrolls pass through untouched.
 */
private fun Modifier.holdToDrag(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!ch.pressed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (cancelled == true) return@awaitEachGesture
        onStart()
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) { ch.consume(); break }
            val dy = ch.position.y - ch.previousPosition.y
            ch.consume()
            if (dy != 0f) onDrag(dy)
        }
        onEnd()
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = LocalEigenState.current
    Column(
        Modifier.fillMaxWidth().then(if (state != null) Modifier.frostSurface(state, CardShape) else Modifier.clip(CardShape).background(Kelvin.colors.ink.copy(alpha = 0.075f))).then(modifier).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
private fun Label(text: String) = KText(text, style = Kelvin.type.summary.copy(fontWeight = FontWeight.SemiBold), color = Kelvin.colors.ink2)

@Composable
private fun NowPlaying(state: EigenState, host: EigenHost) {
    Card {
        Label("Now playing")
        KText(state.mediaTitle ?: "Nothing playing", style = Kelvin.type.title, maxLines = 1)
        state.mediaArtist?.let { KText(it, style = Kelvin.type.body, color = Kelvin.colors.ink2, maxLines = 1) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            KelvinIconButton(Prev, "Previous", { host.media(0) }, kind = IconButtonKind.Plain)
            KelvinIconButton(if (state.mediaPlaying) Pause else KelvinIcons.Play, if (state.mediaPlaying) "Pause" else "Play", { host.media(1) }, kind = IconButtonKind.Accent)
            KelvinIconButton(Next, "Next", { host.media(2) }, kind = IconButtonKind.Plain)
        }
    }
}

@Composable
private fun AlarmCard(state: EigenState, host: EigenHost) {
    Card(Modifier.eigenPress(host::openAlarms, shape = CardShape)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KIcon(Alarm, null, size = 22.dp, color = Kelvin.colors.accent)
            KText(state.alarm ?: "No alarm set", Modifier.padding(start = 12.dp), Kelvin.type.rowTitle, if (state.alarm == null) Kelvin.colors.ink2 else Kelvin.colors.ink, maxLines = 1)
        }
    }
}

@Composable
private fun DayProgress() {
    var fraction by remember { mutableFloatStateOf(dayFraction()) }
    LaunchedEffect(Unit) { while (true) { fraction = dayFraction(); delay(60_000) } }
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Label("Day")
            Spacer(Modifier.weight(1f))
            KText("${(fraction * 100).toInt()}%", style = Kelvin.type.numeral.copy(fontSize = 22.sp), maxLines = 1)
        }
        Bar(fraction)
    }
}

@Composable
private fun Bar(fraction: Float, colour: androidx.compose.ui.graphics.Color = Kelvin.colors.accent) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CapsuleShape).background(Kelvin.colors.ink.copy(alpha = 0.10f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(CapsuleShape).background(colour))
    }
}

private fun dayFraction(): Float {
    val now = Calendar.getInstance()
    return (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 1440f
}

/** Battery level and whether it is charging, read from the system's sticky battery broadcast. */
@Composable
private fun Battery() {
    val context = LocalContext.current
    var level by remember { mutableFloatStateOf(-1f) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l / s.toFloat()
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(30_000)
        }
    }
    val c = Kelvin.colors
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Label(if (charging) "Battery · charging" else "Battery")
            Spacer(Modifier.weight(1f))
            if (charging) KIcon(Bolt, null, size = 20.dp, color = c.accent)
            KText(if (level < 0) "—" else "${(level * 100).toInt()}%", Modifier.padding(start = 4.dp), Kelvin.type.numeral.copy(fontSize = 22.sp), maxLines = 1)
        }
        Bar(level.coerceAtLeast(0f), if (level in 0f..0.15f && !charging) c.critical else c.accent)
    }
}

/** Today's screen time and the three apps that took most of it. */
@Composable
private fun ScreenTime(state: EigenState, host: EigenHost) {
    val c = Kelvin.colors
    Card {
        Label("Screen time today")
        if (!state.usageAccess) {
            KText("Allow usage access to see how long you spend in your apps. It stays on this phone.", style = Kelvin.type.body, color = c.ink2)
            KelvinButton("Allow usage access", host::requestUsageAccess, Modifier.padding(top = 6.dp), kind = ButtonKind.Tinted)
        } else {
            val total = state.usageMinutes.values.sum()
            KText(Usage.formatMinutes(total), style = Kelvin.type.numeral, maxLines = 1)
            val top = remember(state.usageMinutes, state.allApps) {
                state.usageMinutes.entries.sortedByDescending { it.value }.mapNotNull { e -> state.allApps.firstOrNull { it.component.packageName == e.key }?.let { it to e.value } }.take(3)
            }
            for ((app, min) in top) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 28.dp, state.monoIcons)
                KText(app.title(), Modifier.weight(1f).padding(start = 12.dp), Kelvin.type.body, maxLines = 1)
                KText(Usage.formatMinutes(min), style = Kelvin.type.summary, color = c.ink2, maxLines = 1)
            }
        }
    }
}

/** A 25-minute focus timer with a ring that empties as it runs; it keeps time across the page leaving the screen. */
@Composable
private fun Focus(state: EigenState) {
    val c = Kelvin.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = state.focusEndsAt > 0L
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); if (now >= state.focusEndsAt) { state.focusEndsAt = 0L; state.focusLeftMs = 25 * 60_000L }; delay(500) } }
    val left = if (running) (state.focusEndsAt - now).coerceAtLeast(0L) else state.focusLeftMs
    val total = 25 * 60_000f
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                val ring = c.accent; val track = c.ink.copy(alpha = 0.12f)
                Canvas(Modifier.fillMaxSize()) {
                    val w = 6.dp.toPx()
                    drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(ring, -90f, 360f * (left / total), false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                KText("%d:%02d".format(left / 60_000, (left / 1000) % 60), style = Kelvin.type.label.copy(fontSize = 15.sp), maxLines = 1)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Label("Focus")
                KText(if (running) "Stay with it" else if (left < total) "Paused" else "25 minutes, one thing", style = Kelvin.type.rowTitle, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KelvinButton(if (running) "Pause" else if (left < total) "Resume" else "Start", {
                if (running) { state.focusLeftMs = (state.focusEndsAt - System.currentTimeMillis()).coerceAtLeast(0L); state.focusEndsAt = 0L }
                else state.focusEndsAt = System.currentTimeMillis() + state.focusLeftMs
            }, Modifier.weight(1f), kind = if (running) ButtonKind.Secondary else ButtonKind.Primary)
            if (running || left < total) KelvinButton("Reset", { state.focusEndsAt = 0L; state.focusLeftMs = 25 * 60_000L }, kind = ButtonKind.Text)
        }
    }
}

/** The time here and in three other cities, with "tomorrow"/"yesterday" where the day differs. */
@Composable
private fun Clocks() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val here = TimeZone.getDefault()
    val cities = listOf("London" to "Europe/London", "New York" to "America/New_York", "Tokyo" to "Asia/Tokyo", "Kolkata" to "Asia/Kolkata")
        .filter { TimeZone.getTimeZone(it.second).getOffset(now) != here.getOffset(now) }.take(3)
    val c = Kelvin.colors
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    Card {
        Label("World clocks")
        for ((name, zone) in cities) {
            val tz = TimeZone.getTimeZone(zone)
            val fmt = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", locale).apply { timeZone = tz }
            val dayHere = Calendar.getInstance(here).apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
            val dayThere = Calendar.getInstance(tz).apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
            val note = when { dayThere > dayHere || (dayHere > 360 && dayThere == 1) -> "Tomorrow"; dayThere < dayHere || (dayThere > 360 && dayHere == 1) -> "Yesterday"; else -> "Today" }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    KText(name, style = Kelvin.type.rowTitle, maxLines = 1)
                    KText(note, style = Kelvin.type.caption, color = c.ink2, maxLines = 1)
                }
                KText(fmt.format(Date(now)), style = Kelvin.type.numeral.copy(fontSize = 24.sp), maxLines = 1)
            }
        }
    }
}

/** A calculator with precedence (× and ÷ before + and −), brackets left out to keep the keys large. */
@Composable
private fun Calculator() {
    val c = Kelvin.colors
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val keys = listOf(listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("0", ".", "±", "="))
    fun press(k: String) {
        when (k) {
            "C" -> { expr = ""; result = null }
            "⌫" -> expr = expr.dropLast(1)
            "=" -> calculate(expr)?.let { expr = it; result = null }
            "±" -> expr = if (expr.startsWith("−")) expr.drop(1) else "−$expr"
            else -> { expr += k; result = calculate(expr) }
        }
    }
    Card {
        Label("Calculator")
        KText(expr.ifEmpty { "0" }, Modifier.fillMaxWidth(), Kelvin.type.numeral.copy(fontSize = 32.sp), maxLines = 1, align = TextAlign.End)
        KText(result?.let { "= $it" } ?: " ", Modifier.fillMaxWidth(), Kelvin.type.body, c.ink2, maxLines = 1, align = TextAlign.End)
        for (row in keys) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in row) {
                val op = k in listOf("÷", "×", "−", "+", "=")
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp).clip(CapsuleShape)
                        .background(if (k == "=") c.accent else if (op) c.accent.copy(alpha = 0.18f) else c.ink.copy(alpha = 0.08f))
                        .eigenPress({ press(k) }, shape = CapsuleShape, label = k),
                    contentAlignment = Alignment.Center,
                ) { KText(k, style = Kelvin.type.title.copy(fontSize = 20.sp), color = if (k == "=") c.onAccent else if (op) c.accent else c.ink, maxLines = 1) }
            }
        }
    }
}

/** Evaluates digits with + − × ÷ % (× and ÷ bind tighter). Returns null while the expression is incomplete. */
private fun calculate(raw: String): String? = runCatching {
    val s = raw.replace("−", "-").replace("×", "*").replace("÷", "/")
    if (s.isEmpty() || s.last() in "+-*/") return null
    val nums = ArrayList<Double>(); val ops = ArrayList<Char>()
    var i = 0
    fun number(): Double {
        val start = i; if (i < s.length && s[i] == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        var v = s.substring(start, i).toDouble()
        if (i < s.length && s[i] == '%') { v /= 100; i++ }
        return v
    }
    nums += number()
    while (i < s.length) { val op = s[i++]; val n = number(); if (op == '*' || op == '/') nums[nums.lastIndex] = if (op == '*') nums.last() * n else nums.last() / n else { ops += op; nums += n } }
    var v = nums[0]; for (k in ops.indices) v = if (ops[k] == '+') v + nums[k + 1] else v - nums[k + 1]
    if (v.isNaN() || v.isInfinite()) return null
    if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else "%.6f".format(v).trimEnd('0').trimEnd('.')
}.getOrNull()

@Composable
private fun Notes() {
    val c = Kelvin.colors
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher_root_widgets", Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(prefs.getString("scratchpad_text", "") ?: "") }
    Card {
        Label("Notes")
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            BasicTextField(text, { text = it; prefs.edit { putString("scratchpad_text", it) } }, Modifier.fillMaxWidth(),
                textStyle = Kelvin.type.body.copy(color = c.ink, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            if (text.isEmpty()) KText("Jot something down", style = Kelvin.type.body.copy(fontSize = 16.sp), color = c.ink2)
        }
    }
}

/**
 * An Android app widget, hosted by the activity: edge to edge with no padding of Android's, rounded to the same
 * corners as Eigen's cards, over frost for widgets that are partly transparent.
 */
@Composable
private fun HostedWidget(state: EigenState, view: View) {
    Box(Modifier.fillMaxWidth().frostSurface(state, CardShape).clip(CardShape)) {
        AndroidView(
            factory = { (view.parent as? ViewGroup)?.removeView(view); view.setPadding(0, 0, 0, 0); view },
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(CardShape),
            update = { it.setPadding(0, 0, 0, 0) },
        )
    }
}

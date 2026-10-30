package com.studio72f.eigen.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.IconButtonKind
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.tablerIcon
import java.util.Calendar

private val CardShape = ContinuousShape(24.dp)
private val Prev by lazy { tablerIcon("player-skip-back", "M20 5v14l-12 -7l12 -7", "M4 5l0 14") }
private val Next by lazy { tablerIcon("player-skip-forward", "M4 5v14l12 -7l-12 -7", "M20 5l0 14") }
private val Pause by lazy { tablerIcon("player-pause", "M6 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -12", "M14 6a1 1 0 0 1 1 -1h2a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1l0 -12") }
private val Alarm by lazy { tablerIcon("alarm", "M5 13a7 7 0 1 0 14 0a7 7 0 0 0 -14 0", "M12 10l0 3l2 0", "M7 4l-2.75 2", "M17 4l2.75 2") }

/**
 * The widgets page on the right: what is playing, the next alarm and agenda, the day's progress, a scratch note,
 * and the Android app widgets the person has added.
 */
@Composable
fun WidgetsPage(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) = androidx.compose.runtime.CompositionLocalProvider(LocalEigenState provides state) { WidgetsContent(state, host, backdrop) }

/** Lets the page's small cards reach the state for their frost without threading it through every call. */
private val LocalEigenState = androidx.compose.runtime.staticCompositionLocalOf<EigenState?> { null }

@Composable
private fun WidgetsContent(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            KText("Widgets", Modifier.weight(1f), Kelvin.type.display)
            KelvinButton("Add widget", host::addWidget, kind = ButtonKind.Tinted, icon = KelvinIcons.Plus)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.mediaTitle != null) NowPlaying(state, host)
            state.alarm?.takeIf { it.isNotBlank() }?.let { a ->
                Card(Modifier.eigenPress(host::openAlarms, shape = CardShape)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        studio72f.kelvin.KIcon(Alarm, null, size = 22.dp, color = Kelvin.colors.accent)
                        KText(a, Modifier.padding(start = 12.dp), Kelvin.type.rowTitle, maxLines = 1)
                    }
                }
            }
            state.agenda?.takeIf { it.isNotBlank() }?.let { a ->
                Card { Label("Today"); KText(a, style = Kelvin.type.body, maxLines = 4) }
            }
            DayProgress()
            Notes()
            for (view in state.widgetViews) HostedWidget(state, view)
            if (state.widgetViews.isEmpty()) KText(
                "Add any widget from your apps with Add widget. Hold a widget to resize it or remove it.",
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp), Kelvin.type.summary, Kelvin.colors.ink2,
            )
            Spacer(Modifier.height(12.dp))
        }
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
private fun Label(text: String) = KText(text, style = Kelvin.type.summary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Kelvin.colors.ink2)

@Composable
private fun NowPlaying(state: EigenState, host: EigenHost) {
    Card {
        Label("Now playing")
        KText(state.mediaTitle ?: "", style = Kelvin.type.title, maxLines = 1)
        state.mediaArtist?.let { KText(it, style = Kelvin.type.body, color = Kelvin.colors.ink2, maxLines = 1) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            KelvinIconButton(Prev, "Previous", { host.media(0) }, kind = IconButtonKind.Plain)
            KelvinIconButton(if (state.mediaPlaying) Pause else KelvinIcons.Play, if (state.mediaPlaying) "Pause" else "Play", { host.media(1) }, kind = IconButtonKind.Accent)
            KelvinIconButton(Next, "Next", { host.media(2) }, kind = IconButtonKind.Plain)
        }
    }
}

@Composable
private fun DayProgress() {
    var fraction by remember { mutableFloatStateOf(dayFraction()) }
    LaunchedEffect(Unit) { while (true) { fraction = dayFraction(); delay(60_000) } }
    val c = Kelvin.colors
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Label("Day")
            Spacer(Modifier.weight(1f))
            KText("${(fraction * 100).toInt()}%", style = Kelvin.type.numeral.copy(fontSize = 22.sp), maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CapsuleShape).background(c.ink.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(CapsuleShape).background(c.accent))
        }
    }
}

private fun dayFraction(): Float {
    val now = Calendar.getInstance()
    return (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 1440f
}

@Composable
private fun Notes() {
    val c = Kelvin.colors
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher_root_widgets", Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(prefs.getString("scratchpad_text", "") ?: "") }
    Card {
        Label("Notes")
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            BasicTextField(text, { text = it; prefs.edit().putString("scratchpad_text", it).apply() }, Modifier.fillMaxWidth(),
                textStyle = Kelvin.type.body.copy(color = c.ink, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            if (text.isEmpty()) KText("Jot something down", style = Kelvin.type.body.copy(fontSize = 16.sp), color = c.ink2)
        }
    }
}

/** An Android app widget, hosted by the activity, in a card. */
@Composable
private fun HostedWidget(state: EigenState, view: View) {
    Box(Modifier.fillMaxWidth().frostSurface(state, CardShape)) {
        AndroidView(
            factory = { (view.parent as? ViewGroup)?.removeView(view); view },
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

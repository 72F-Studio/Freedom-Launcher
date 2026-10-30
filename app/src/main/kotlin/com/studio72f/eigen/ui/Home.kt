package com.studio72f.eigen.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio72f.eigen.CategoryRules
import com.studio72f.eigen.MainActivity
import com.studio72f.eigen.ai.EigenAssistant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.GlassContent
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.glass

private val CardShape = ContinuousShape(16.dp)
private const val COLUMNS = 5

/**
 * Home: the centred clock, the drawers as large centred names that open in place into vertical cards, and one
 * glass search capsule at the bottom. While searching, results stack upward above the capsule, nearest the thumb.
 */
@Composable
fun HomePage(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val gentle = Kelvin.motion.gentle<Float>()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        AnimatedContent(
            state.searching, Modifier.weight(1f),
            transitionSpec = { fadeIn(gentle) togetherWith fadeOut(gentle) },
            label = "home or search",
        ) { searching -> if (searching) SearchResults(state, host, backdrop) else Drawers(state, host) }
        SearchCapsule(state, host, backdrop, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp))
    }
}

@Composable
private fun Drawers(state: EigenState, host: EigenHost) {
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { host.homeMenu() }) }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeClock(state.clockStyle, onTime = host::openAlarms, onDate = host::openSettings, modifier = Modifier.padding(top = 28.dp))
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (drawer in state.drawers) if (drawer.apps.isNotEmpty()) DrawerSection(state, host, drawer)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrawerSection(state: EigenState, host: EigenHost, drawer: Drawer) {
    val c = Kelvin.colors
    val open = state.openDrawer == drawer.name
    val locale = LocalConfiguration.current.locales[0]
    val ink by animateColorAsState(if (state.openDrawer == null || open) c.ink else c.ink.copy(alpha = 0.42f), Kelvin.motion.gentle(), label = "drawer ink")
    // An opened drawer scrolls itself into view once it has grown, so its cards never hide under the search capsule.
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(open) { if (open) { delay(320); reveal.bringIntoView() } }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(reveal), horizontalAlignment = Alignment.CenterHorizontally) {
    KText(
        drawer.name.replaceFirstChar { it.titlecase(locale) },
        Modifier.eigenPress(
            onClick = { state.openDrawer = if (open) null else drawer.name },
            onLongClick = host::homeMenu,
            shape = CardShape,
            label = if (open) "Close" else "Open",
        ).padding(horizontal = 20.dp, vertical = 10.dp),
        Kelvin.type.display.copy(fontSize = 31.sp, lineHeight = 38.sp),
        ink, maxLines = 1, align = TextAlign.Center,
    )
    AnimatedVisibility(
        open,
        enter = expandVertically(Kelvin.motion.spatial()) + fadeIn(Kelvin.motion.gentle()),
        exit = shrinkVertically(Kelvin.motion.spatial()) + fadeOut(Kelvin.motion.gentle()),
    ) { AppGrid(state, host, drawer.apps) }
    }
}

@Composable
private fun AppGrid(state: EigenState, host: EigenHost, apps: List<MainActivity.App>) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp)) {
        apps.chunked(COLUMNS).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth()) {
                chunk.forEachIndexed { col, app -> AppCard(state, host, app, row * COLUMNS + col, Modifier.weight(1f)) }
                repeat(COLUMNS - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A vertical card: icon over label on a faint frosted fill. Cards rise in one after another as a drawer opens. */
@Composable
private fun AppCard(state: EigenState, host: EigenHost, app: MainActivity.App, index: Int, modifier: Modifier) {
    val c = Kelvin.colors
    val reduce = Kelvin.reduceMotion
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    val spatial = Kelvin.motion.spatial<Float>()
    LaunchedEffect(Unit) { delay(minOf(index, 15) * 22L); appear.animateTo(1f, spatial) }
    val dot = remember(state.dotsVersion, app) { host.hasDot(app) }
    Box(
        modifier.padding(3.dp).height(98.dp)
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 22.dp.toPx()
                scaleX = 0.94f + 0.06f * v; scaleY = scaleX
            }
            .clip(CardShape).background(c.ink.copy(alpha = 0.075f))
            .eigenPress({ host.launch(app) }, { host.appMenu(app) }, CardShape, app.title()),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AppIcon(app, 40.dp, state.monoIcons)
            Spacer(Modifier.height(8.dp))
            KText(app.title(), style = Kelvin.type.caption.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium), color = c.ink, maxLines = 2, align = TextAlign.Center)
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(c.accent))
    }
}

/**
 * The one capsule at the bottom. At rest it reads "Tap to search" with the assistant spark and a magnifier; tapped,
 * it becomes the field itself, in place, with the keyboard up.
 */
@Composable
private fun SearchCapsule(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop, modifier: Modifier) {
    val c = Kelvin.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.searching) {
        if (state.searching) { delay(60); runCatching { focus.requestFocus() }; keyboard?.show() }
        else { focusManager.clearFocus(); keyboard?.hide() }
    }
    Row(
        modifier.fillMaxWidth().height(56.dp)
            .glass(backdrop, CapsuleShape, GlassContent.Text, elevated = true)
            .then(if (!state.searching) Modifier.eigenPress({ state.searching = true }, { state.openAssistant(null) }, CapsuleShape, "Search") else Modifier)
            .padding(start = 22.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (state.searching) {
                BasicTextField(
                    state.query, { state.query = it },
                    Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = Kelvin.type.body.copy(color = c.ink, fontSize = 17.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitSearch(state, host) }),
                )
                if (state.query.isEmpty()) KText("Search apps, or ask anything", style = Kelvin.type.body.copy(fontSize = 17.sp), color = c.ink2, maxLines = 1)
            } else {
                KText("Tap to search", style = Kelvin.type.body.copy(fontSize = 17.sp), color = c.ink2, maxLines = 1)
            }
        }
        if (state.searching) {
            Box(Modifier.size(44.dp).eigenPress({ state.searching = false; state.query = "" }, shape = CircleShape, label = "Close search"), contentAlignment = Alignment.Center) {
                KIcon(KelvinIcons.X, null, size = 20.dp, color = c.ink)
            }
        } else {
            Box(Modifier.size(44.dp).eigenPress({ state.openAssistant(null) }, shape = CircleShape, label = "Ask the assistant"), contentAlignment = Alignment.Center) {
                KIcon(KelvinIcons.Sparkles, null, size = 22.dp, color = c.accent)
            }
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { KIcon(KelvinIcons.Search, null, size = 22.dp, color = c.ink) }
        }
    }
}

private fun results(state: EigenState): List<MainActivity.App> {
    val q = CategoryRules.normalized(state.query.trim())
    if (q.isEmpty()) return emptyList()
    return state.allApps.filter { CategoryRules.normalized(it.title()).contains(q) || (it.label != null && CategoryRules.normalized(it.label).contains(q)) }
        .sortedBy { if (CategoryRules.normalized(it.title()).startsWith(q)) 0 else 1 }
        .take(24)
}

private fun naturalLanguage(q: String) = q.endsWith("?") || q.trim().split(Regex("\\s+")).size >= 3

private fun submitSearch(state: EigenState, host: EigenHost) {
    val first = results(state).firstOrNull()
    if (first != null) { host.launch(first); state.searching = false; state.query = "" }
    else if (state.query.isNotBlank()) state.openAssistant(state.query.trim())
}

@Composable
private fun SearchResults(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val c = Kelvin.colors
    val q = state.query.trim()
    val found = remember(q, state.allApps) { results(state) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Bottom) {
        if (q.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                KText("Type an app's name, or ask a question.\nAnswers come from the model on this phone.", style = Kelvin.type.body, color = c.ink2, align = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
                itemsIndexed(found, key = { _, a -> a.id }) { i, app ->
                    Row(
                        Modifier.fillMaxWidth().clip(ContinuousShape(18.dp))
                            .eigenPress({ host.launch(app); state.searching = false; state.query = "" }, { host.appMenu(app) }, ContinuousShape(18.dp), app.title())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 40.dp, state.monoIcons)
                        Spacer(Modifier.size(16.dp))
                        KText(app.title(), style = Kelvin.type.rowTitle.copy(fontSize = 18.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
                    }
                }
            }
            if (naturalLanguage(q) || found.isEmpty()) AskCard(state, q, backdrop)
        }
    }
}

/**
 * The on-device answer under search. Natural-language queries are asked automatically after a short pause; the
 * answer streams in. Tapping the card carries the question into the Assistant tab.
 */
@Composable
private fun AskCard(state: EigenState, q: String, backdrop: KelvinBackdrop) {
    val c = Kelvin.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun ask(question: String) {
        asked = question; busy = true; answer = null; source = null
        scope.launch {
            val a = EigenAssistant.ask(context, question, conversational = false) { if (asked == question) answer = it }
            if (asked == question) { answer = a.text; source = a.source; busy = false }
        }
    }
    LaunchedEffect(q) { if (naturalLanguage(q) && q != asked) { delay(900); ask(q) } }
    val showing = asked == q
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .glass(backdrop, ContinuousShape(24.dp), GlassContent.Text)
            .eigenPress({ if (showing && !busy) state.openAssistant(q) else ask(q) }, shape = ContinuousShape(24.dp), label = "Ask Eigen")
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KIcon(KelvinIcons.Sparkles, null, size = 18.dp, color = c.accent)
            Spacer(Modifier.size(8.dp))
            KText(if (showing) "Eigen" else "Ask Eigen “$q”", style = Kelvin.type.label, maxLines = 1)
        }
        if (showing) KText(answer ?: "Thinking…", style = Kelvin.type.body.copy(fontSize = 16.sp), color = if (answer == null) c.ink2 else c.ink, maxLines = 8)
        val note = when {
            showing && !busy && source != null -> "$source · tap to continue"
            !showing -> EigenAssistant.statusLabel()
            else -> null
        }
        if (note != null) KText(note, style = Kelvin.type.caption, color = c.ink2, maxLines = 1)
    }
}

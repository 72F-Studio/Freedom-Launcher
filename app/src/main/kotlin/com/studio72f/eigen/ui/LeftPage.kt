package com.studio72f.eigen.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio72f.eigen.MediaAccessService
import com.studio72f.eigen.ai.EigenAssistant
import kotlinx.coroutines.launch
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.GlassContent
import studio72f.kelvin.IconButtonKind
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinAvatar
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinEmptyState
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinSearchField
import studio72f.kelvin.KelvinSegmented
import studio72f.kelvin.glass
import studio72f.kelvin.kelvinPill
import studio72f.kelvin.kelvinPillInk
import studio72f.kelvin.tablerIcon

private val ArrowUp by lazy { tablerIcon("arrow-up", "M12 5l0 14", "M18 11l-6 -6", "M6 11l6 -6") }
private val Message by lazy { tablerIcon("message", "M8 9h8", "M8 13h6", "M18 4a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-5l-5 3v-3h-2a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12") }
private val RowShape = ContinuousShape(22.dp)

/** The left page: the unified messaging timeline and the on-device assistant, one segmented control apart. */
@Composable
fun LeftPage(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val gentle = Kelvin.motion.gentle<Float>()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        AnimatedContent(state.leftTab, transitionSpec = { fadeIn(gentle) togetherWith fadeOut(gentle) }, label = "left title") { tab ->
            KText(if (tab == EigenState.TAB_ASSISTANT) "Assistant" else "Conversations", Modifier.padding(start = 4.dp, top = 20.dp, bottom = 14.dp), Kelvin.type.display)
        }
        KelvinSegmented(listOf("Conversations", "Assistant"), state.leftTab, { state.leftTab = it })
        Spacer(Modifier.height(14.dp))
        AnimatedContent(state.leftTab, Modifier.weight(1f), transitionSpec = { fadeIn(gentle) togetherWith fadeOut(gentle) }, label = "left body") { tab ->
            if (tab == EigenState.TAB_ASSISTANT) Assistant(state, backdrop) else Conversations(host, backdrop)
        }
    }
}

@Composable
private fun Conversations(host: EigenHost, backdrop: KelvinBackdrop) {
    val c = Kelvin.colors
    var items by remember { mutableStateOf(MediaAccessService.getConversations().toList()) }
    var connected by remember { mutableStateOf(MediaAccessService.isServiceConnected()) }
    var filter by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        MediaAccessService.setConversationListener { list -> items = list.toList(); connected = MediaAccessService.isServiceConnected() }
        onDispose { MediaAccessService.setConversationListener(null) }
    }
    if (!connected) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            KelvinEmptyState(Message, "See your conversations here", "Turn on notification access for Eigen to read and reply to messages from one place. Nothing leaves your phone.", actionText = "Turn on access", onAction = host::openNotificationAccess)
        }
        return
    }
    val shown = remember(items, filter) {
        val f = filter.trim().lowercase()
        if (f.isEmpty()) items else items.filter { listOf(it.sender, it.text, it.appName).any { s -> s?.lowercase()?.contains(f) == true } }
    }
    Column(Modifier.fillMaxSize()) {
        KelvinSearchField(filter, { filter = it }, "Search conversations", "Clear", backdrop = backdrop)
        Spacer(Modifier.height(12.dp))
        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                KelvinEmptyState(Message, if (filter.isBlank()) "All caught up" else "No matches", if (filter.isBlank()) "New messages show up here, and you can reply without leaving home." else "Try a name or a word from the message.")
            }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.key }) { item ->
                ConversationRow(item, open == item.key) { open = if (open == item.key) null else item.key }
            }
        }
    }
}

@Composable
private fun ConversationRow(item: MediaAccessService.ConversationItem, expanded: Boolean, onToggle: () -> Unit) {
    val c = Kelvin.colors
    val context = LocalContext.current
    var reply by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RowShape).background(c.ink.copy(alpha = 0.07f)).eigenPress(onToggle, shape = RowShape).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            KelvinAvatar(item.sender ?: item.appName ?: "?")
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KText(item.sender ?: "", Modifier.weight(1f), Kelvin.type.rowTitle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    KText(DateUtils.getRelativeTimeSpanString(item.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), style = Kelvin.type.summary, color = c.ink2, maxLines = 1)
                }
                val where = listOfNotNull(item.appName, item.conversationTitle?.takeIf { item.isGroup && it.isNotBlank() }?.let { "in $it" }).joinToString(" · ")
                if (where.isNotEmpty()) KText(where, style = Kelvin.type.caption, color = c.accent, maxLines = 1)
                KText(item.text ?: "", Modifier.padding(top = 2.dp), Kelvin.type.body, c.ink, maxLines = if (expanded) 12 else 2)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(Kelvin.motion.spatial()) + fadeIn(), exit = shrinkVertically(Kelvin.motion.spatial()) + fadeOut()) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.canReply()) Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(CapsuleShape).background(c.sunken).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        BasicTextField(reply, { reply = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = Kelvin.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (MediaAccessService.sendReply(context, item, reply)) { sent = reply; reply = "" } }))
                        if (reply.isEmpty()) KText(sent?.let { "Sent “$it”" } ?: "Reply to ${item.sender}", style = Kelvin.type.body, color = c.ink2, maxLines = 1)
                    }
                    KelvinIconButton(ArrowUp, "Send", { if (MediaAccessService.sendReply(context, item, reply)) { sent = reply; reply = "" } }, kind = IconButtonKind.Accent, enabled = reply.isNotBlank())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KelvinButton("Open", { runCatching { item.contentIntent?.send() } }, kind = ButtonKind.Secondary)
                    KelvinButton("Dismiss", { MediaAccessService.dismissNotification(item.key) }, kind = ButtonKind.Text)
                }
            }
        }
    }
}

private val SUGGESTIONS = listOf("How's my battery?", "Open camera", "Plan a calm evening", "What's 18% of 2400?")

@Composable
private fun Assistant(state: EigenState, backdrop: KelvinBackdrop) {
    val c = Kelvin.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by EigenAssistant.status.collectAsState()
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { EigenAssistant.refreshStatus() }
    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || state.thinking) return
        draft = ""
        state.chat.add(ChatLine(true, q))
        state.chat.add(ChatLine(false, ""))
        state.thinking = true
        val at = state.chat.lastIndex
        scope.launch {
            val answer = EigenAssistant.ask(context, q, conversational = true) { partial -> if (at < state.chat.size) state.chat[at] = ChatLine(false, partial) }
            if (at < state.chat.size) state.chat[at] = ChatLine(false, answer.text, answer.source, answer.failed)
            state.thinking = false
        }
    }
    LaunchedEffect(state.pendingQuestion) { state.pendingQuestion?.let { state.pendingQuestion = null; send(it) } }
    LaunchedEffect(state.chat.size, state.chat.lastOrNull()?.text?.length) { if (state.chat.isNotEmpty()) list.animateScrollToItem(state.chat.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        KText(when (status) {
            EigenAssistant.Status.Ready -> "Gemini Nano, running on this phone"
            EigenAssistant.Status.Preparing -> "Gemini Nano is getting ready on this phone"
            EigenAssistant.Status.Checking -> "Checking the on-device model"
            EigenAssistant.Status.Unavailable -> "Quick answers only: this phone has no on-device model"
        }, Modifier.padding(start = 4.dp, bottom = 8.dp), Kelvin.type.summary, c.ink2, maxLines = 1)
        if (state.chat.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                KText("Ask anything", style = Kelvin.type.headline.copy(fontSize = 30.sp), align = TextAlign.Center)
                KText("Answers come from the model on this phone. Nothing you type leaves it.", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), Kelvin.type.body, c.ink2, align = TextAlign.Center)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in SUGGESTIONS) KText(s, Modifier.height(40.dp).kelvinPill(false, backdrop).eigenPress({ send(s) }, shape = CapsuleShape).padding(horizontal = 16.dp, vertical = 10.dp), Kelvin.type.label, kelvinPillInk(false), maxLines = 1)
                }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.chat) { _, line -> Bubble(line) }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).heightIn(min = 56.dp)
                .glass(backdrop, ContinuousShape(28.dp), GlassContent.Text, elevated = true)
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(draft, { draft = it }, Modifier.fillMaxWidth(), maxLines = 4, textStyle = Kelvin.type.body.copy(color = c.ink, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(draft) }))
                if (draft.isEmpty()) KText("Message Eigen", style = Kelvin.type.body.copy(fontSize = 16.sp), color = c.ink2, maxLines = 1)
            }
            KelvinIconButton(ArrowUp, "Send", { send(draft) }, kind = IconButtonKind.Accent, enabled = draft.isNotBlank() && !state.thinking)
        }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    val c = Kelvin.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (line.mine) Alignment.End else Alignment.Start) {
        val shape = ContinuousShape(22.dp)
        SelectionContainer {
            KText(
                line.text.ifEmpty { "Thinking…" },
                Modifier.widthIn(max = 320.dp).clip(shape).background(if (line.mine) c.accentSoft else c.ink.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 11.dp),
                Kelvin.type.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
                if (line.mine) c.onAccentSoft else if (line.text.isEmpty() || line.failed) c.ink2 else c.ink,
            )
        }
        if (line.source != null) KText(line.source, Modifier.padding(start = 12.dp, top = 4.dp), Kelvin.type.caption, c.ink2, maxLines = 1)
    }
}

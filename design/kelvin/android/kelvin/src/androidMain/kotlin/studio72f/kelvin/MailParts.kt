package studio72f.kelvin

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** How a message was protected, as far as the app could check. */
enum class CryptoState { Encrypted, Signed, SignedUnverified, Failed }

/**
 * One line about a message's encryption or signature, above its body. Never colour alone: each state
 * has its own glyph (via [Tone]) and a sentence. The app supplies the words.
 */
@Composable
fun KelvinCryptoBanner(
    state: CryptoState,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tone = when (state) {
        CryptoState.Encrypted, CryptoState.Signed -> Tone.Positive
        CryptoState.SignedUnverified -> Tone.Caution
        CryptoState.Failed -> Tone.Critical
    }
    KelvinNotice(title, message, modifier, tone, actionText = actionText, onAction = onAction)
}

/**
 * The message body area. Kelvin draws the chrome (the remote-content notice and the page behind);
 * the app supplies the view that renders the message, so its security settings (no scripts, blocked
 * remote content, link handling) stay in one reviewed place and are not decided by a design system.
 *
 * [remoteContentBlocked] shows a notice with [loadRemoteText]; [onLoadRemote] lets the person load it.
 */
@Composable
fun KelvinMessageBody(
    factory: (Context) -> View,
    modifier: Modifier = Modifier,
    remoteContentBlocked: Boolean = false,
    remoteTitle: String = stringResource(R.string.kelvin_images_hidden),
    remoteMessage: String? = null,
    loadRemoteText: String = stringResource(R.string.kelvin_show_images),
    onLoadRemote: (() -> Unit)? = null,
    update: (View) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (remoteContentBlocked) {
            KelvinNotice(remoteTitle, remoteMessage, tone = Tone.Neutral, actionText = loadRemoteText, onAction = onLoadRemote)
        }
        AndroidView(factory = factory, update = update, modifier = Modifier.fillMaxWidth().testTag("message-body"))
    }
}

/**
 * The "From" line in compose: the address a message will be sent as, with a prompt to change it when
 * the account has more than one identity. [onClick] should open a [KelvinSheet] of [KelvinIdentityRow]s.
 */
@Composable
fun KelvinFromField(
    label: String,
    name: String,
    address: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .let { if (onClick != null) it.kelvinPress(onClick, role = Role.Button) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KelvinEyebrow(label)
        Column(Modifier.weight(1f)) {
            KText(name, style = Kelvin.type.rowTitle, maxLines = 1)
            KText(address, style = Kelvin.type.summary, color = Kelvin.colors.ink2, maxLines = 1)
        }
    }
}

/** One sending identity in a picker. The chosen one is marked in words and by a tick. */
@Composable
fun KelvinIdentityRow(
    name: String,
    address: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chosenLabel: String = stringResource(R.string.kelvin_chosen),
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .kelvinPress(onClick, role = Role.RadioButton)
            .semantics { if (chosen) stateDescription = chosenLabel }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KelvinAvatar(name)
        Column(Modifier.weight(1f)) {
            KText(name, style = Kelvin.type.rowTitle, maxLines = 1)
            KText(address, style = Kelvin.type.summary, color = Kelvin.colors.ink2, maxLines = 1)
        }
        if (chosen) KelvinGlassTile(KelvinIcons.CircleCheck, Kelvin.colors.accent)
    }
}

/**
 * The message text field in compose: plain, multi-line, in Kelvin's body type with the accent cursor.
 * Formatting (via [KelvinFormatBar]) is the app's job; this field holds the text and a hint.
 */
@Composable
fun KelvinBodyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val c = Kelvin.colors
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Kelvin.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = hint },
            decorationBox = { inner ->
                if (value.isEmpty()) KText(hint, style = Kelvin.type.body, color = c.ink3)
                inner()
            },
        )
    }
}

/**
 * An inbox row that can be selected. A long press starts selection; while [selectionMode] is on,
 * a tap toggles the row and a checkbox shows. The selected row sits on the accent's soft fill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KelvinSelectableMessageRow(
    sender: String,
    time: String,
    subject: String,
    preview: String,
    unread: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
    attachment: Boolean = false,
    starred: Boolean = false,
    selectLabel: String = stringResource(R.string.kelvin_select_message),
    selectedLabel: String = stringResource(R.string.kelvin_selected),
) {
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) Kelvin.colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect,
                role = Role.Button,
            )
            .semantics { if (selected) stateDescription = selectedLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            KelvinCheckbox(selected, { onToggleSelect() }, Modifier.padding(start = 16.dp), contentDescription = selectLabel)
        }
        KelvinMessageRow(
            sender = sender, time = time, subject = subject, preview = preview, unread = unread,
            onClick = { if (selectionMode) onToggleSelect() else onOpen() },
            modifier = Modifier.weight(1f),
            attachment = attachment, starred = starred,
        )
    }
}

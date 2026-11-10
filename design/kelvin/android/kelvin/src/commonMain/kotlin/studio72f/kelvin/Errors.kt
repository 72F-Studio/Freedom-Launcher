package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What a failed action tells the person, in the order they need it (72F error rule):
 * a short [headline], a plain [description] of what went wrong and what to try, then, collapsed under
 * "View more", why it happened, the specific [code] and a redacted technical detail, and a button that
 * emails the logs.
 *
 * The app owns every string, so it can translate them, and owns the logs: pass the exact text that would
 * be sent as [logPreview] so the person reads it before choosing to send. Nothing is uploaded here;
 * [onEmailLogs] should open the person's mail composer with that text. The logs must already be free of
 * credentials, tokens, addresses and message content.
 *
 * [code] is stable and specific to the failing stage; never pass a generic one.
 */
@Composable
fun KelvinErrorPanel(
    headline: String,
    description: String,
    code: String,
    modifier: Modifier = Modifier,
    /** Why it happened, in a sentence or two. Shown under View more. */
    why: String? = null,
    /** Redacted technical detail. Never a raw exception or protocol string as the only message. */
    detail: String? = null,
    /** The redacted log text that [onEmailLogs] will send, shown so nothing leaves unseen. */
    logPreview: String? = null,
    onEmailLogs: (() -> Unit)? = null,
    /** Optional first action ("Try again"), shown above the logs button. */
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
    tone: Tone = Tone.Critical,
    viewMoreText: String = kelvinString(KelvinString.ErrorViewMore),
    codeLabel: String = kelvinString(KelvinString.ErrorCode),
    whyLabel: String = kelvinString(KelvinString.ErrorWhy),
    detailLabel: String = kelvinString(KelvinString.ErrorDetail),
    logPreviewLabel: String = kelvinString(KelvinString.ErrorLogPreview),
    emailLogsText: String = kelvinString(KelvinString.ErrorEmailLogs),
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = Kelvin.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KelvinNotice(
            title = headline,
            message = description,
            tone = tone,
            gutter = gutter,
        )
        if (retryText != null && onRetry != null) {
            KelvinButton(retryText, onRetry, Modifier.padding(horizontal = gutter).fillMaxWidth())
        }
        KelvinGroup(gutter = gutter) {
            KelvinExpandable(title = viewMoreText, expanded = open, onToggle = { open = !open }) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (why != null) Section(whyLabel) { KText(why, style = Kelvin.type.summary, color = c.ink2) }
                    Section(codeLabel) { Mono(code, c.ink) }
                    if (detail != null) Section(detailLabel) { Mono(detail, c.ink2) }
                    if (logPreview != null) Section(logPreviewLabel) {
                        Mono(logPreview, c.ink2, Modifier.fillMaxWidth().background(c.canvas, KelvinShapes.md).padding(12.dp))
                    }
                }
            }
        }
        if (onEmailLogs != null) {
            KelvinButton(emailLogsText, onEmailLogs, Modifier.padding(horizontal = gutter).fillMaxWidth(), kind = ButtonKind.Tinted)
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KelvinEyebrow(label)
        content()
    }
}

@Composable
private fun Mono(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    KText(
        text,
        modifier,
        Kelvin.type.summary.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal),
        color,
    )
}

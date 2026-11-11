package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KelvinNotice(
            title = headline,
            message = description,
            tone = tone,
            gutter = gutter,
        )
        ErrorBody(
            code, why, detail, logPreview, onEmailLogs, retryText, onRetry,
            viewMoreText, codeLabel, whyLabel, detailLabel, logPreviewLabel, emailLogsText, gutter,
        )
    }
}

/** Everything under the headline: the first action, View more, and the logs button. */
@Composable
private fun ErrorBody(
    code: String,
    why: String?,
    detail: String?,
    logPreview: String?,
    onEmailLogs: (() -> Unit)?,
    retryText: String?,
    onRetry: (() -> Unit)?,
    viewMoreText: String,
    codeLabel: String,
    whyLabel: String,
    detailLabel: String,
    logPreviewLabel: String,
    emailLogsText: String,
    gutter: androidx.compose.ui.unit.Dp,
    startOpen: Boolean = false,
) {
    var open by rememberSaveable(code, startOpen) { mutableStateOf(startOpen) }
    val c = Kelvin.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

/**
 * One failure, described at the place it failed, ready for [KelvinErrorPanel] or [KelvinProblemSheet].
 *
 * [code] is stable and names the failing stage ("RDM_E04_FEED_SEARCH"), never a catch-all. [detail] is the
 * technical text, already redacted (see [kelvinProblemDetail]); it is also exactly what the logs button sends.
 * [headline], [description] and [why] are the app's own translated words, never an exception's.
 */
@Immutable
data class KelvinProblem(
    val headline: String,
    val description: String,
    val code: String,
    val why: String? = null,
    val detail: String? = null,
)

/** [KelvinErrorPanel] for a [KelvinProblem]. [onEmailLogs] receives the text the person was shown. */
@Composable
fun KelvinErrorPanel(
    problem: KelvinProblem,
    modifier: Modifier = Modifier,
    onEmailLogs: ((report: String) -> Unit)? = null,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
    tone: Tone = Tone.Critical,
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val report = problem.detail
    KelvinErrorPanel(
        headline = problem.headline,
        description = problem.description,
        code = problem.code,
        modifier = modifier,
        why = problem.why,
        logPreview = report,
        onEmailLogs = if (onEmailLogs != null && report != null) ({ onEmailLogs(report) }) else null,
        retryText = retryText,
        onRetry = onRetry,
        tone = tone,
        gutter = gutter,
    )
}

/**
 * A failure that has no place on the page (an action from a menu, a share, a background step that just ended):
 * the error rule in a sheet, in place of a toast. The headline is the sheet's title, the description leads, and
 * View more, the code and the logs button follow as in [KelvinErrorPanel]. Keep it composed and pass the
 * current [problem], or null for none.
 */
@Composable
fun KelvinProblemSheet(
    problem: KelvinProblem?,
    onDismiss: () -> Unit,
    closeLabel: String,
    backdrop: KelvinBackdrop?,
    onEmailLogs: ((report: String) -> Unit)? = null,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
    /** Opens with View more already expanded, for a sheet reached from a notice that already said the summary. */
    detailsOpen: Boolean = false,
) {
    // The sheet keeps showing the problem it opened with while it slides away.
    var shown by remember { mutableStateOf(problem) }
    if (problem != null) shown = problem
    val p = shown
    KelvinSheet(problem != null, onDismiss, p?.headline.orEmpty(), closeLabel, backdrop) {
        if (p != null) {
            Column(
                Modifier.heightIn(max = SheetBodyMax).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KText(p.description, style = Kelvin.type.body, color = Kelvin.colors.ink2)
                val report = p.detail
                ErrorBody(
                    code = p.code,
                    why = p.why,
                    detail = null,
                    logPreview = report,
                    onEmailLogs = if (onEmailLogs != null && report != null) ({ onEmailLogs(report) }) else null,
                    retryText = retryText,
                    onRetry = onRetry,
                    viewMoreText = kelvinString(KelvinString.ErrorViewMore),
                    codeLabel = kelvinString(KelvinString.ErrorCode),
                    whyLabel = kelvinString(KelvinString.ErrorWhy),
                    detailLabel = kelvinString(KelvinString.ErrorDetail),
                    logPreviewLabel = kelvinString(KelvinString.ErrorLogPreview),
                    emailLogsText = kelvinString(KelvinString.ErrorEmailLogs),
                    gutter = 0.dp,
                    startOpen = detailsOpen,
                )
            }
        }
    }
}

private val SheetBodyMax = 520.dp

/** Opens a [KelvinProblem] in the sheet of the [KelvinProblemHost] above. */
fun interface KelvinProblemPresenter {
    /** [detailsOpen] shows the code and the report at once, for a notice whose own text was the summary. */
    fun show(problem: KelvinProblem, detailsOpen: Boolean)
}

/** The page's problem sheet. Does nothing until a [KelvinProblemHost] provides it. */
val LocalKelvinProblems = staticCompositionLocalOf { KelvinProblemPresenter { _, _ -> } }

/**
 * Gives everything in [content] one problem sheet, so a failure anywhere in the app is told the same way.
 * Put it once around the app's pages. A failure with a place on the page shows [KelvinProblemNotice] there (or the
 * full [KelvinErrorPanel] when the failure is the page); one without a place calls
 * `LocalKelvinProblems.current.show(problem, false)` where a toast used to be.
 *
 * [onEmailLogs] gets the problem and the exact text the person was shown; open their mail app with it.
 */
@Composable
fun KelvinProblemHost(
    closeLabel: String,
    backdrop: KelvinBackdrop?,
    onEmailLogs: ((problem: KelvinProblem, report: String) -> Unit)?,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf<KelvinProblem?>(null) }
    var detailsOpen by remember { mutableStateOf(false) }
    val presenter = remember { KelvinProblemPresenter { problem, open -> detailsOpen = open; shown = problem } }
    CompositionLocalProvider(LocalKelvinProblems provides presenter, content = content)
    val current = shown
    KelvinProblemSheet(
        problem = current,
        onDismiss = { shown = null },
        closeLabel = closeLabel,
        backdrop = backdrop,
        onEmailLogs = if (onEmailLogs != null && current != null) ({ report -> onEmailLogs(current, report) }) else null,
        detailsOpen = detailsOpen,
    )
}

/**
 * A failure told where it happened, inside a list or a card: the headline and what to try, with View more opening
 * the host's sheet on the code, the reason and the logs button. [retryText] adds the action that tries again
 * under it; leave it out when the control that failed already offers one.
 */
@Composable
fun KelvinProblemNotice(
    problem: KelvinProblem,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Critical,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
    /** Adds an x, for a failure the person can put away (a banner over content that is still usable). */
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "",
    viewMoreText: String = kelvinString(KelvinString.ErrorViewMore),
    gutter: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val problems = LocalKelvinProblems.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KelvinNotice(
            title = problem.headline,
            message = problem.description,
            tone = tone,
            actionText = viewMoreText,
            onAction = { problems.show(problem, true) },
            onDismiss = onDismiss,
            dismissLabel = dismissLabel,
            gutter = gutter,
        )
        if (retryText != null && onRetry != null) {
            KelvinButton(retryText, onRetry, Modifier.padding(horizontal = gutter).fillMaxWidth(), kind = ButtonKind.Tinted)
        }
    }
}

/**
 * The technical text of a failure, safe to show and to send: the [code], the [stage] that failed, any [lines]
 * the app adds, each cause's class and message, the [app] ("Rediom 2.4.1") and the system. Every line the app or
 * an exception supplies goes through [kelvinRedact]. Do not add account names, addresses or content to [lines].
 */
fun kelvinProblemDetail(
    code: String,
    stage: String,
    app: String,
    error: Throwable? = null,
    lines: List<String> = emptyList(),
): String = buildList {
    add("Error code: $code")
    add("Stage: $stage")
    lines.mapNotNullTo(this) { kelvinRedact(it) }
    generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(MAX_CAUSES).forEach { cause ->
        val message = kelvinRedact(cause.message)
        add(if (message == null) "Exception: ${cause.javaClass.name}" else "Exception: ${cause.javaClass.name}: $message")
    }
    add("App: $app")
    add(kelvinSystemLine())
}.joinToString("\n")

/**
 * [text] with what must never be shown or sent taken out: sign-in tokens and keys, passwords, email addresses,
 * the sign-in part and the query of a link, and long opaque strings. Whitespace is collapsed and the result is
 * capped at [max] characters. Null for blank text.
 */
fun kelvinRedact(text: String?, max: Int = 300): String? {
    if (text.isNullOrBlank()) return null
    val clean = REDACTIONS.fold(text) { value, (pattern, replacement) -> pattern.replace(value, replacement) }
        .replace(Regex("\\s+"), " ").trim()
    return if (clean.length > max) clean.take(max) + "…" else clean
}

private const val MAX_CAUSES = 5

private val REDACTIONS: List<Pair<Regex, String>> = listOf(
    Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}") to "$1 [redacted]",
    Regex("(https?://)[^/@\\s]+@") to "$1[redacted]@",
    Regex("(https?://[^\\s?#]+)\\?[^\\s]*") to "$1?[query]",
    Regex(
        "(?i)\\b(access_token|refresh_token|id_token|token|password|passwd|pwd|secret|api[_-]?key|session|" +
            "authorization|otp|signature)(\"?\\s*[:=]\\s*\"?)[^\\s\"&,;}]+",
    ) to "$1$2[redacted]",
    Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "[email]",
    Regex("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}") to "[token]",
    Regex("\\b[A-Za-z0-9_-]{32,}\\b") to "[redacted]",
)

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

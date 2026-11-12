package com.studio72f.eigen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studio72f.eigen.ai.EigenAssistant
import com.studio72f.eigen.ai.GemmaModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio72f.aishare.AiShare
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinNotice
import studio72f.kelvin.KelvinProgressBar
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinSection
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.Tone

/**
 * Settings > Assistant: which model answers. Gemini Nano when the phone has it; otherwise Gemma, taken from another
 * 72F app when one already holds it (72F AI Share: no download, no extra storage), or downloaded once.
 */
@Composable
internal fun AssistantPage(state: EigenState, host: EigenHost) {
    val context = host.context()
    val status by EigenAssistant.status.collectAsState()
    val download by GemmaModels.state.collectAsState()
    val downloading by GemmaModels.downloading.collectAsState()
    val changes by GemmaModels.changes.collectAsState()
    LaunchedEffect(changes) { EigenAssistant.forgetGemma(); EigenAssistant.refreshStatus() }
    // Which models other 72F apps hold, by model id, to the first such app's name. Asking connects to those apps.
    val elsewhere by produceState(emptyMap<String, String>(), changes) {
        value = withContext(Dispatchers.IO) {
            GemmaModels.CHOICES.mapNotNull { c ->
                AiShare.providers(context, c.model).firstOrNull()?.let { pkg ->
                    c.model.id to runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
                        .getOrDefault("another 72F app")
                }
            }.toMap()
        }
    }
    val chosen = GemmaModels.chosen(context)

    KelvinNotice(EigenAssistant.statusLabel(), when (status) {
        EigenAssistant.Status.Unavailable -> "This phone has no Gemini Nano. Pick a Gemma model below and the assistant answers anything; quick answers work either way."
        EigenAssistant.Status.Preparing -> "Android is downloading Gemini Nano. It's ready in a few minutes."
        else -> "Everything runs on this phone. Nothing you type is sent anywhere."
    }, Modifier.padding(bottom = 16.dp), if (status == EigenAssistant.Status.Unavailable) Tone.Caution else Tone.Info)

    (download as? GemmaModels.State.Failed)?.let { f ->
        val (title, line) = when (f.code) {
            Problems.AI_SPACE -> "Not enough space" to "Free up some storage, then try again."
            Problems.AI_CHECKSUM -> "The download was damaged" to "Eigen deleted it. Try again on a steady connection."
            Problems.AI_TERMS -> "Accept the model's terms first" to "Tap Download again and accept."
            else -> "The download stopped" to "Check your connection and tap Download to carry on where it stopped."
        }
        KelvinNotice(title, line, Modifier.padding(bottom = 16.dp), Tone.Critical, actionText = "View more", onAction = {
            Problems.show(state, context, f.code, "model:download", title, line,
                "The model comes from 72F's Hugging Face mirror and is checked against its SHA-256 before Eigen uses it.", f.error)
            GemmaModels.clearFailure()
        })
    }

    KelvinSection("Gemma models") {
        KelvinGroup {
            for (c in GemmaModels.CHOICES) {
                val here = GemmaModels.isDownloaded(context, c.model)
                val inApp = elsewhere[c.model.id]
                val isDownloading = downloading?.id == c.model.id
                val sizeMb = (c.model.sizeBytes shr 20).toInt()
                Column {
                    KelvinRow(
                        c.model.name,
                        summary = when {
                            here -> c.summary + " On this phone, " + sizeMb + " MB."
                            inApp != null -> c.summary + " Already on this phone in $inApp: Eigen uses that copy, no download."
                            else -> c.summary + " " + (if (sizeMb >= 1024) "%.1f GB".format(sizeMb / 1024f) else "$sizeMb MB") + " download."
                        },
                        icon = KelvinIcons.Sparkles, tile = if (here || inApp != null) KelvinTiles.Orange else KelvinTiles.Grey,
                        value = when {
                            chosen?.id == c.model.id && (here || inApp != null) -> "In use"
                            isDownloading -> null
                            here -> "Use"
                            inApp != null -> "Use"
                            else -> "Download"
                        },
                        onClick = when {
                            isDownloading -> null
                            here || inApp != null -> ({ GemmaModels.choose(context, c.model) })
                            else -> ({ askToDownload(host, c) })
                        },
                    )
                    if (isDownloading) {
                        val d = download as? GemmaModels.State.Downloading
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            KelvinProgressBar(d?.fraction)
                            KText(if (download is GemmaModels.State.Verifying) "Checking the file…" else "${d?.doneMb ?: 0} of ${d?.totalMb ?: sizeMb} MB",
                                style = Kelvin.type.caption, color = Kelvin.colors.ink2)
                            KelvinButton("Pause", { GemmaModels.pause() }, kind = ButtonKind.Secondary)
                        }
                    }
                    if (here && !isDownloading) {
                        KelvinRow("Delete ${c.model.name}", summary = "Frees $sizeMb MB. Other 72F apps using this copy look for another, or download their own.",
                            danger = true, onClick = {
                                host.choose("Delete ${c.model.name}?", listOf("Delete", "Keep it")) { i -> if (i == 0) GemmaModels.delete(context, c.model) }
                            })
                    }
                }
            }
        }
    }
    KText(
        "Eigen goes online for one thing: downloading the model you pick, from 72F's mirror on Hugging Face. Whichever 72F app " +
            "downloads a model first runs it for the others, so it is on the phone once. Prompts and answers never leave the phone.",
        Modifier.padding(horizontal = 32.dp, vertical = 12.dp), Kelvin.type.summary, Kelvin.colors.ink2,
    )
    KelvinGroup(Modifier.padding(top = 8.dp)) {
        KelvinRow("Start a new conversation", icon = KelvinIcons.Refresh, tile = KelvinTiles.Grey, onClick = {
            EigenAssistant.clearHistory(); state.chat.clear()
        })
    }
}

private fun askToDownload(host: EigenHost, c: GemmaModels.Choice) {
    val context = host.context()
    val terms = if (c.model.vision) "the Apache License 2.0" else "the Gemma Terms of Use and its Prohibited Use Policy"
    host.choose(
        "Download ${c.model.name}? By downloading you accept $terms.",
        listOf("Accept and download", "Read the terms"),
    ) { i ->
        if (i == 0) { GemmaModels.acceptTerms(context); GemmaModels.download(context, c) }
        else runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(if (c.model.vision) "https://www.apache.org/licenses/LICENSE-2.0" else "https://ai.google.dev/gemma/terms"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

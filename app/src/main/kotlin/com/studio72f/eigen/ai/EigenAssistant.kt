package com.studio72f.eigen.ai

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Eigen's assistant, for the Assistant tab and the search bar. Everything stays on the phone:
 * 1. Quick answers (open an app, battery, time, sums) need no model and reply at once.
 * 2. Everything else goes to Gemini Nano through AICore ([MlKitEngine]), streamed as it is written.
 * 3. On a phone without Gemini Nano, to Gemma through 72F AI Share: a model another 72F app already holds, or one
 *    Eigen downloaded ([GemmaModels]). Either way it runs on this phone.
 */
object EigenAssistant {
    enum class Status { Checking, Ready, Preparing, Unavailable }

    data class Answer(val text: String, val source: String?, val failed: Boolean = false)

    const val SOURCE_QUICK = "Quick answer · on this phone"
    const val SOURCE_NANO = "Gemini Nano · on this phone"

    @Volatile private var appContext: android.content.Context? = null
    /** Gemma through 72F AI Share when Gemini Nano isn't on this phone. */
    @Volatile private var gemma: studio72f.aishare.SharedLlm? = null
    @Volatile private var nanoMissing = false

    /** From EigenApp, so status checks can look for Gemma without a screen's context. */
    fun init(context: android.content.Context) { appContext = context.applicationContext }

    private fun gemmaSource(llm: studio72f.aishare.SharedLlm): String {
        val from = if (llm.local) "" else " · from " + appName(llm.providerPackage)
        return "${llm.model.name}$from · on this phone"
    }

    private fun appName(pkg: String): String = runCatching {
        val pm = appContext!!.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("another 72F app")

    /** The Gemma models to try, the person's pick first, then smallest first. */
    private fun gemmaCandidates(context: android.content.Context): List<studio72f.aishare.SharedModel> =
        (listOfNotNull(GemmaModels.chosen(context)) + GemmaModels.CHOICES.map { it.model }).distinct()

    private suspend fun findGemma(context: android.content.Context): studio72f.aishare.SharedLlm? {
        gemma?.let { return it }
        for (m in gemmaCandidates(context)) studio72f.aishare.AiShare.find(context, m)?.let { gemma = it; return it }
        return null
    }

    /** Drops the Gemma connection so the next question looks again (a download finished, a model was deleted). */
    fun forgetGemma() { gemma?.close(); gemma = null; _status.value = Status.Checking }

    private const val SYSTEM = "You are Eigen, the assistant built into a phone's home screen. You run entirely on the phone. " +
        "Answer in plain sentences, briefly and warmly. No markdown, no emoji, no lists unless asked."
    private const val HISTORY_TURNS = 6

    private val engine = MlKitEngine()
    private val initLock = Mutex()
    private val history = ArrayDeque<String>()
    private val _status = MutableStateFlow(Status.Checking)
    val status: StateFlow<Status> = _status.asStateFlow()

    @JvmStatic fun statusLabel(): String = when (_status.value) {
        Status.Ready -> gemma?.let { gemmaSource(it) } ?: "Gemini Nano · on this phone"
        Status.Preparing -> "Gemini Nano · getting ready"
        Status.Checking -> "Checking the on-device model"
        Status.Unavailable -> "Quick answers only · add a model in Settings"
    }

    @JvmStatic fun clearHistory() = synchronized(history) { history.clear() }

    /** Reads AICore's status without starting a session. Cheap; call when a screen that shows it appears. */
    suspend fun refreshStatus() {
        if (engine.isInitialized() || gemma != null) { _status.value = Status.Ready; return }
        _status.value = when (withContext(Dispatchers.IO) { MlKitEngine.probeStatus() }) {
            FeatureStatus.AVAILABLE, FeatureStatus.DOWNLOADABLE -> Status.Ready
            FeatureStatus.DOWNLOADING -> Status.Preparing
            else -> {
                nanoMissing = true
                val c = appContext
                if (c != null && withContext(Dispatchers.IO) { findGemma(c) } != null) Status.Ready else Status.Unavailable
            }
        }
    }

    /**
     * Answers [query]. [conversational] keeps recent turns as context (the Assistant tab); the search bar asks
     * one-off questions capped at two sentences. [onPartial] receives the answer so far while Nano writes it.
     */
    suspend fun ask(context: Context, query: String, conversational: Boolean, act: Boolean = true, onPartial: (String) -> Unit = {}): Answer =
        withContext(Dispatchers.Default) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext Answer("Type a question or a command.", null, failed = true)
            quickAnswer(context.applicationContext, clean, act)?.let { remember(conversational, clean, it); return@withContext Answer(it, SOURCE_QUICK) }
            val digest = if (Actions.isCatchUp(clean)) Actions.messages(clean) else null
            if (digest != null && digest.isEmpty()) return@withContext Answer(Actions.nothingToSummarise(clean), SOURCE_QUICK)
            if (!ready()) return@withContext if (digest != null) Answer(Actions.plainDigest(digest), SOURCE_QUICK) else Answer(unavailable(), null, failed = true)
            gemma?.let { llm -> return@withContext askGemma(llm, if (digest != null) Actions.summaryPrompt(SYSTEM, clean, digest) else prompt(clean, conversational), conversational, clean, onPartial) }
            val sb = StringBuilder()
            try {
                engine.generateResponseStream(if (digest != null) Actions.summaryPrompt(SYSTEM, clean, digest) else prompt(clean, conversational)).collect { chunk ->
                    if (chunk.isEmpty()) return@collect
                    sb.append(chunk)
                    val sofar = sb.toString()
                    withContext(Dispatchers.Main) { onPartial(sofar) }
                }
            } catch (e: Throwable) {
                android.util.Log.w("EigenAssistant", "Gemini Nano generation failed", e)
                if (sb.isEmpty()) {
                    engine.reset()
                    return@withContext Answer("Gemini Nano couldn't answer just now. Try again in a moment.", null, failed = true)
                }
            }
            val text = sb.toString().trim()
            if (text.isEmpty()) return@withContext Answer("Gemini Nano didn't return an answer. Try rephrasing.", null, failed = true)
            remember(conversational, clean, text)
            Answer(text, SOURCE_NANO)
        }

    private suspend fun askGemma(llm: studio72f.aishare.SharedLlm, text: String, conversational: Boolean, query: String, onPartial: (String) -> Unit): Answer {
        val sb = StringBuilder()
        try {
            llm.stream(studio72f.aishare.AiRequest(text)).collect { chunk ->
                sb.append(chunk)
                val sofar = sb.toString()
                withContext(Dispatchers.Main) { onPartial(sofar) }
            }
        } catch (e: studio72f.aishare.AiShareException) {
            android.util.Log.w("EigenAssistant", "Gemma failed: ${e.error.code}")
            // The model went away (its app removed, deleted, or the app holding it closed): look again next time.
            forgetGemma()
            if (sb.isEmpty()) return Answer("${llm.model.name} couldn't answer just now (${e.error.code}). Try again in a moment.", null, failed = true)
        }
        val answer = sb.toString().trim()
        if (answer.isEmpty()) return Answer("${llm.model.name} didn't return an answer. Try rephrasing.", null, failed = true)
        remember(conversational, query, answer)
        return Answer(answer, gemmaSource(llm))
    }

    private suspend fun ready(): Boolean = initLock.withLock {
        if (engine.isInitialized() || gemma != null) return true
        val result = if (nanoMissing) Result.failure(IllegalStateException("no AICore"))
            else engine.initialize { downloading -> _status.value = if (downloading) Status.Preparing else Status.Checking }
        result.exceptionOrNull()?.let { android.util.Log.w("EigenAssistant", "Gemini Nano setup failed", it) }
        // No Gemini Nano: Gemma from a 72F app on this phone, or Eigen's own download.
        val ok = result.isSuccess || appContext?.let { findGemma(it) } != null
        _status.value = if (ok) Status.Ready else Status.Unavailable
        ok
    }

    private fun prompt(query: String, conversational: Boolean): String = buildString {
        append(SYSTEM).append('\n')
        if (conversational) synchronized(history) { history.forEach { append(it).append('\n') } }
        else append("Answer in at most two sentences.\n")
        append("User: ").append(query).append("\nEigen:")
    }

    private fun remember(conversational: Boolean, query: String, answer: String) {
        if (!conversational) return
        synchronized(history) {
            history.addLast("User: $query")
            history.addLast("Eigen: $answer")
            while (history.size > HISTORY_TURNS * 2) history.removeFirst()
        }
    }

    private fun unavailable() =
        "This phone has no Gemini Nano. Add a Gemma model in Settings > Assistant (or install a 72F app that has one) and Eigen can answer anything. Quick answers work now: open an app, ask the time or battery, or do a sum."

    /** Commands that need no model. Returns null when the query needs Nano. */
    fun quickAnswer(context: Context, query: String, act: Boolean = true): String? {
        val lower = query.lowercase(Locale.ROOT).trim()
        // Search asks as you type: anything that does something waits for a tap.
        if (lower.startsWith("open ") || lower.startsWith("launch ")) return if (act) launchApp(context, lower.replaceFirst(Regex("^(open|launch)\\s+"), "").trim()) else "Tap to open it."
        Actions.handle(context, query, dryRun = !act)?.let { return it }
        com.studio72f.eigen.QuickMath.answer(query)?.let { return it }
        if (lower == "battery" || "battery level" in lower || "battery status" in lower || "my battery" in lower) {
            val bm = context.getSystemService(BatteryManager::class.java) ?: return null
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return "Battery is at $level%" + (if (bm.isCharging) " and charging." else ".")
        }
        if (lower == "time" || lower == "date" || "what time" in lower || "what day" in lower) {
            return "It's " + SimpleDateFormat("EEEE d MMMM, h:mm a", Locale.getDefault()).format(Date()) + "."
        }
        val expr = lower.replaceFirst(Regex("^(calc|calculate|what is|what's)\\s+"), "").removeSuffix("?").trim()
        if (expr != lower || Regex("[0-9.\\s]+[+\\-*/x%][0-9.\\s]+").matches(lower)) evaluate(expr)?.let { v ->
            val shown = if (v == Math.floor(v) && !v.isInfinite()) String.format(Locale.getDefault(), "%d", v.toLong()) else String.format(Locale.getDefault(), "%.4f", v).trimEnd('0')
            return "$expr = $shown"
        }
        return null
    }

    private fun launchApp(context: Context, name: String): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (info in pm.queryIntentActivities(intent, 0)) {
            val label = info.loadLabel(pm).toString()
            val l = label.lowercase(Locale.ROOT)
            if (l.contains(name) || name.contains(l)) {
                val launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName) ?: continue
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opening $label."
            }
        }
        return "There's no app called “$name” on this phone."
    }

    /** "18% of 2400", "12 x 4", "100/3". */
    private fun evaluate(raw: String): Double? = runCatching {
        val e = raw.replace(" ", "")
        Regex("^([0-9.]+)%of([0-9.]+)$").find(e)?.let { return it.groupValues[1].toDouble() / 100 * it.groupValues[2].toDouble() }
        for (op in charArrayOf('+', '-', '*', 'x', '/')) {
            val at = e.indexOf(op, 1)
            if (at <= 0) continue
            val a = e.substring(0, at).toDouble(); val b = e.substring(at + 1).toDouble()
            return when (op) { '+' -> a + b; '-' -> a - b; '/' -> a / b; else -> a * b }
        }
        null
    }.getOrNull()
}

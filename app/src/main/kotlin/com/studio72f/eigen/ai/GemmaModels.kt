package com.studio72f.eigen.ai

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio72f.aishare.AiShare
import studio72f.aishare.HostedModel
import studio72f.aishare.SharedModel
import studio72f.aishare.SharedModels
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Gemma for phones without Gemini Nano. Through 72F AI Share, Eigen first uses a model another 72F app (Tofu, ReMerit,
 * HyperMind) already holds; only when none does is a model downloaded, once, from 72F's Hugging Face mirror. Eigen
 * then serves it to the other 72F apps in turn. The downloader is Tofu's (ModelDownloader), cut down: resumable,
 * checked against its SHA-256, and gated on the person accepting the Gemma terms.
 */
object GemmaModels {
    data class Choice(val model: SharedModel, val url: String, val summary: String)

    /** Smallest first: an assistant on the home screen wants a quick answer more than a long one. */
    val CHOICES = listOf(
        Choice(SharedModels.GEMMA_3_270M, "https://huggingface.co/72fstudio/gemma-3/resolve/main/gemma3-270m-it-q8.litertlm",
            "Quick and light. Best for short answers on any phone."),
        Choice(SharedModels.GEMMA_3_1B, "https://huggingface.co/72fstudio/gemma-3/resolve/main/gemma3-1b-it-int4.litertlm",
            "Better answers, a little slower. For most phones."),
        Choice(SharedModels.GEMMA_4_E2B, "https://huggingface.co/72fstudio/gemma-4/resolve/main/gemma-4-E2B-it.litertlm",
            "The best answers. Needs a recent phone with 8 GB of memory."),
    )

    sealed class State {
        object Idle : State()
        data class Downloading(val fraction: Float, val doneMb: Int, val totalMb: Int) : State()
        object Verifying : State()
        /** [code] is one of Problems' AI codes. */
        data class Failed(val code: String, val error: Throwable?) : State()
    }

    const val TERMS_KEY = "gemma_terms_2026_04_01"
    private const val PREFS = "gemma_models"
    private const val CHOSEN = "chosen"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _downloading = MutableStateFlow<SharedModel?>(null)
    val downloading: StateFlow<SharedModel?> = _downloading.asStateFlow()
    /** Bumped when a model arrives or is deleted, so pages re-read what is here. */
    val changes = MutableStateFlow(0)

    fun fileFor(context: Context, model: SharedModel) = File(File(context.filesDir, "models"), model.fileName)
    private fun partFor(context: Context, model: SharedModel) = File(File(context.filesDir, "models"), model.fileName + ".part")
    private fun markerFor(context: Context, model: SharedModel) = File(File(context.filesDir, "models"), model.fileName + ".sha256")

    /** Downloaded, whole and checked. */
    fun isDownloaded(context: Context, model: SharedModel): Boolean {
        val f = fileFor(context, model)
        return f.isFile && f.length() == model.sizeBytes &&
            runCatching { markerFor(context, model).readText().trim() == model.id }.getOrDefault(false)
    }

    /** Called once from EigenApp: Eigen serves what it has downloaded to the other 72F apps. */
    fun register() = AiShare.hostModels { c -> CHOICES.map { it.model }.filter { isDownloaded(c, it) }.map { HostedModel(it, fileFor(c, it)) } }

    fun acceptedTerms(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(TERMS_KEY, false)
    fun acceptTerms(context: Context) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(TERMS_KEY, true).apply()

    /** The model the person picked, if any; the assistant uses it before looking at the rest. */
    fun chosen(context: Context): SharedModel? = SharedModels.byId(context.getSharedPreferences(PREFS, 0).getString(CHOSEN, null))
    fun choose(context: Context, model: SharedModel) {
        context.getSharedPreferences(PREFS, 0).edit().putString(CHOSEN, model.id).apply()
        changes.value++
    }

    fun download(context: Context, choice: Choice) {
        if (_downloading.value != null) return
        val app = context.applicationContext
        _downloading.value = choice.model
        job = scope.launch {
            _state.value = State.Downloading(0f, 0, (choice.model.sizeBytes shr 20).toInt())
            _state.value = try {
                fetch(app, choice)
                choose(app, choice.model)
                State.Idle
            } catch (e: kotlinx.coroutines.CancellationException) {
                State.Idle
            } catch (e: DownloadFailure) {
                State.Failed(e.code, e.cause)
            } catch (t: Throwable) {
                State.Failed(com.studio72f.eigen.ui.Problems.AI_DOWNLOAD, t)
            } finally {
                _downloading.value = null
                changes.value++
            }
        }
    }

    /** Stops the download; what arrived so far is kept and the next download resumes from it. */
    fun pause() { job?.cancel(); job = null }

    fun delete(context: Context, model: SharedModel) {
        listOf(fileFor(context, model), partFor(context, model), markerFor(context, model)).forEach { it.delete() }
        File(File(context.filesDir, "models"), model.fileName + ".licenses").deleteRecursively()
        changes.value++
    }

    fun clearFailure() { if (_state.value is State.Failed) _state.value = State.Idle }

    private class DownloadFailure(val code: String, cause: Throwable? = null) : Exception(code, cause)

    private suspend fun fetch(context: Context, choice: Choice) = withContext(Dispatchers.IO) {
        val model = choice.model
        if (!acceptedTerms(context)) throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_TERMS)
        if (isDownloaded(context, model)) return@withContext
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        // Room for the file plus what LiteRT-LM caches beside it.
        if (dir.usableSpace < model.sizeBytes - partFor(context, model).length() + 300L * 1024 * 1024) {
            throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_SPACE)
        }
        val part = partFor(context, model)
        val from = part.length().takeIf { it in 1 until model.sizeBytes } ?: 0L.also { part.delete() }
        val conn = (URL(choice.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 120_000
            if (from > 0) setRequestProperty("Range", "bytes=$from-")
        }
        try {
            val code = try { conn.responseCode } catch (t: Throwable) { throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_DOWNLOAD, t) }
            val resumed = from > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resumed) throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_DOWNLOAD, Exception("HTTP $code"))
            if (!resumed) part.delete()
            var done = if (resumed) from else 0L
            val total = model.sizeBytes
            val buf = ByteArray(1 shl 20)
            var lastShown = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, resumed).buffered(1 shl 20).use { out ->
                    while (true) {
                        ensureActive()
                        val n = try { input.read(buf) } catch (t: Throwable) { throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_DOWNLOAD, t) }
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done - lastShown > 4L * 1024 * 1024) {
                            lastShown = done
                            _state.value = State.Downloading((done.toFloat() / total).coerceIn(0f, 1f), (done shr 20).toInt(), (total shr 20).toInt())
                        }
                    }
                }
            }
            if (part.length() != total) throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_DOWNLOAD, Exception("incomplete"))
        } finally {
            conn.disconnect()
        }
        _state.value = State.Verifying
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input -> val b = ByteArray(1 shl 20); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != model.id) { part.delete(); throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_CHECKSUM) }
        val file = fileFor(context, model)
        if (!part.renameTo(file)) throw DownloadFailure(com.studio72f.eigen.ui.Problems.AI_DOWNLOAD, Exception("rename"))
        markerFor(context, model).writeText(model.id)
        // The applicable terms travel with each separately downloaded model (as Tofu does).
        val notices = File(dir, model.fileName + ".licenses").apply { mkdirs() }
        val terms = if (model.vision) listOf("apache-2.0.txt") else listOf("gemma-NOTICE.txt", "gemma-3-terms.txt", "gemma-prohibited-use.txt")
        terms.forEach { name ->
            runCatching { context.assets.open("licenses/models/$name").use { i -> File(notices, name).outputStream().use { i.copyTo(it) } } }
        }
    }
}

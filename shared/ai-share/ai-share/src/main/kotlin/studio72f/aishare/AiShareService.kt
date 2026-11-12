package studio72f.aishare

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs this app's models for the other 72F apps. Bound only, never started, so it lives exactly as long as a 72F app
 * is connected. Every call checks the caller first ([Trust]); prompts and answers are never logged or stored.
 */
class AiShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val binder = object : IAiShare.Stub() {
        override fun apiVersion() = Protocol.API_VERSION

        override fun describe(): Bundle {
            requireTrusted()
            return Bundle().apply {
                putStringArrayList(Protocol.KEY_MODELS, ArrayList(AiShare.hosted(this@AiShareService).map { it.model.id }))
            }
        }

        override fun start(request: Bundle?, image: ParcelFileDescriptor?, callback: IAiShareCallback?): String {
            requireTrusted()
            val id = UUID.randomUUID().toString()
            val cb = callback ?: return id
            val req = request ?: return id.also { cb.safeError(AiShareError.BAD_REQUEST) }
            jobs[id] = scope.launch {
                try {
                    val hosted = AiShare.hosted(this@AiShareService).firstOrNull { it.model.id == req.getString(Protocol.KEY_MODEL) }
                        ?: throw AiShareException(AiShareError.NO_MODEL)
                    val prompt = req.getString(Protocol.KEY_PROMPT).orEmpty()
                    if (prompt.isBlank() || prompt.length > Protocol.MAX_PROMPT_CHARS) throw AiShareException(AiShareError.BAD_REQUEST)
                    val stream = req.getBoolean(Protocol.KEY_STREAM, true)
                    val result = SharedEngine.run(this@AiShareService, hosted.model, hosted.file, AiRequest(
                        prompt = prompt,
                        system = req.getString(Protocol.KEY_SYSTEM).orEmpty(),
                        deterministic = req.getBoolean(Protocol.KEY_DETERMINISTIC, false),
                        image = image?.let(::readImage),
                        toolJson = req.getString(Protocol.KEY_TOOL),
                    )) { if (stream) cb.onText(it) }
                    cb.onDone(Bundle().apply {
                        putString(Protocol.KEY_TEXT, result.text)
                        result.toolArgsJson?.let { putString(Protocol.KEY_TOOL_ARGS, it) }
                    })
                } catch (e: AiShareException) {
                    Log.w(TAG, "Shared request failed: ${e.error.code}", e.cause)
                    cb.safeError(e.error)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cb.safeError(AiShareError.CANCELLED)
                } catch (t: Throwable) {
                    Log.w(TAG, "Shared request failed", t)
                    cb.safeError(AiShareError.GENERATE)
                } finally {
                    jobs.remove(id)
                    runCatching { image?.close() }
                }
            }
            return id
        }

        override fun cancel(requestId: String?) {
            requireTrusted()
            requestId?.let { jobs.remove(it)?.cancel() }
        }

        private fun requireTrusted() {
            if (!Trust.isTrustedUid(this@AiShareService, Binder.getCallingUid())) throw SecurityException(AiShareError.NOT_TRUSTED.code)
        }
    }

    private fun readImage(pfd: ParcelFileDescriptor): ByteArray = try {
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > Protocol.MAX_IMAGE_BYTES) throw AiShareException(AiShareError.IMAGE)
            }
            out.toByteArray().also { if (it.isEmpty()) throw AiShareException(AiShareError.IMAGE) }
        }
    } catch (e: AiShareException) {
        throw e
    } catch (t: Throwable) {
        throw AiShareException(AiShareError.IMAGE, t)
    }

    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == Protocol.ACTION) binder else null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun IAiShareCallback.safeError(error: AiShareError) { runCatching { onError(error.code) } }

    private companion object { const val TAG = "AiShareService" }
}

package studio72f.aishare

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Finds the 72F apps that hold a model and talks to them. */
internal object AiShareClient {
    private const val TAG = "AiShareClient"
    private const val BIND_TIMEOUT_MS = 5_000L

    private class Connection(val context: Context, val packageName: String) : ServiceConnection {
        val ready = CompletableDeferred<IAiShare?>()
        @Volatile var service: IAiShare? = null
        @Volatile var lost = false

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IAiShare.Stub.asInterface(binder); ready.complete(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) { lost = true; service = null }
        override fun onBindingDied(name: ComponentName?) { lost = true; service = null; ready.complete(null) }
        override fun onNullBinding(name: ComponentName?) { ready.complete(null) }
        fun close() = runCatching { context.unbindService(this) }
    }

    /** Peers other than this app, installed and verified as 72F's own. */
    private fun peers(context: Context): List<String> =
        Trust.PACKAGES.keys.filter { it != context.packageName && Trust.isInstalled(context, it) && Trust.isTrusted(context, it) }

    private suspend fun bind(context: Context, packageName: String): Connection? {
        val c = Connection(context, packageName)
        val intent = Intent(Protocol.ACTION).setClassName(packageName, AiShareService::class.java.name)
        // BIND_IMPORTANT lifts the host to foreground priority while this app is on screen, so the phone's memory
        // manager (ColorOS's Athena, MIUI's PowerKeeper) doesn't kill it mid-answer as an idle background app.
        val flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        val bound = runCatching { context.bindService(intent, c, flags) }.getOrDefault(false)
        if (!bound) { c.close(); return null }
        val service = withTimeoutOrNull(BIND_TIMEOUT_MS) { c.ready.await() }
        if (service == null) { c.close(); return null }
        return c
    }

    private fun holds(c: Connection, model: SharedModel): Boolean = runCatching {
        val s = c.service ?: return false
        s.apiVersion() >= 1 && s.describe().getStringArrayList(Protocol.KEY_MODELS)?.contains(model.id) == true
    }.onFailure { Log.w(TAG, "${c.packageName} couldn't say what it holds", it) }.getOrDefault(false)

    suspend fun providers(context: Context, model: SharedModel): List<String> = withContext(Dispatchers.IO) {
        peers(context).filter { pkg -> bind(context, pkg)?.let { c -> holds(c, model).also { c.close() } } == true }
    }

    suspend fun connect(context: Context, model: SharedModel): SharedLlm? = withContext(Dispatchers.IO) {
        for (pkg in peers(context)) {
            val c = bind(context, pkg) ?: continue
            if (holds(c, model)) return@withContext RemoteLlm(c, model) { bind(context, pkg) }
            c.close()
        }
        null
    }

    private class RemoteLlm(
        @Volatile private var c: Connection,
        override val model: SharedModel,
        private val rebind: suspend () -> Connection?,
    ) : SharedLlm {
        override val providerPackage get() = c.packageName
        override val local = false

        /** A host killed before it wrote a word is started again once; one killed mid-answer is reported (AIS-05). */
        override fun stream(req: AiRequest): Flow<String> = kotlinx.coroutines.flow.flow {
            var wrote = false
            try {
                run(req, stream = true).collect { wrote = true; emit(it) }
            } catch (e: AiShareException) {
                if (e.error != AiShareError.HOST_GONE || wrote || !reconnect()) throw e
                emitAll(run(req, stream = true))
            }
        }

        override suspend fun generate(req: AiRequest): Result<AiResult> {
            val first = once(req)
            val gone = (first.exceptionOrNull() as? AiShareException)?.error == AiShareError.HOST_GONE
            return if (gone && reconnect()) once(req) else first
        }

        private suspend fun reconnect(): Boolean {
            c.close()
            c = rebind() ?: return false
            return true
        }

        private suspend fun once(req: AiRequest): Result<AiResult> = runCatching {
            val done = CompletableDeferred<AiResult>()
            val id = start(req, stream = false, object : IAiShareCallback.Stub() {
                override fun onText(chunk: String?) = Unit
                override fun onDone(result: Bundle?) {
                    done.complete(AiResult(result?.getString(Protocol.KEY_TEXT).orEmpty(), result?.getString(Protocol.KEY_TOOL_ARGS)))
                }
                override fun onError(code: String?) { done.completeExceptionally(AiShareException(AiShareError.of(code))) }
            })
            try { awaitOrHostGone(done) } catch (e: kotlinx.coroutines.CancellationException) { cancel(id); throw e }
        }

        private fun run(req: AiRequest, stream: Boolean): Flow<String> = callbackFlow {
            val id = start(req, stream, object : IAiShareCallback.Stub() {
                override fun onText(chunk: String?) { if (!chunk.isNullOrEmpty()) trySend(chunk) }
                override fun onDone(result: Bundle?) { close() }
                override fun onError(code: String?) { close(AiShareException(AiShareError.of(code))) }
            })
            // A host that dies mid-answer never calls back; notice it rather than wait forever.
            val watch = launch { while (true) { kotlinx.coroutines.delay(500); if (c.lost) { close(AiShareException(AiShareError.HOST_GONE)); break } } }
            awaitClose { watch.cancel(); cancel(id) }
        }

        private fun start(req: AiRequest, stream: Boolean, callback: IAiShareCallback): String {
            val s = c.service ?: throw AiShareException(AiShareError.HOST_GONE)
            if (req.image != null && !model.vision) throw AiShareException(AiShareError.IMAGE)
            val pipe = req.image?.let { bytes ->
                val (read, write) = ParcelFileDescriptor.createPipe()
                Thread {
                    runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) } }
                }.start()
                read
            }
            return try {
                s.start(Protocol.request(model, req, stream), pipe, callback)
            } catch (e: SecurityException) {
                throw AiShareException(AiShareError.NOT_TRUSTED, e)
            } catch (e: android.os.RemoteException) {
                throw AiShareException(AiShareError.HOST_GONE, e)
            } finally {
                runCatching { pipe?.close() }
            }
        }

        private suspend fun awaitOrHostGone(done: CompletableDeferred<AiResult>): AiResult {
            while (true) {
                withTimeoutOrNull(500) { return@withTimeoutOrNull done.await() }?.let { return it }
                if (c.lost) throw AiShareException(AiShareError.HOST_GONE)
            }
        }

        private fun cancel(id: String) { runCatching { c.service?.cancel(id) } }

        override fun close() { c.close() }
    }
}

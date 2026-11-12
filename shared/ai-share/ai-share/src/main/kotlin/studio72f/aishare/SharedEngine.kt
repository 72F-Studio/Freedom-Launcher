package studio72f.aishare

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The one LiteRT-LM engine in this app's process, shared by the app itself and by every 72F app it serves, so a
 * model is in memory once. Requests run one at a time. The engine loads on GPU and falls back to CPU (from Tofu's
 * GemmaEngine), keeps the vision encoder when the model has one, and unloads after [IDLE_MS] without a request.
 */
object SharedEngine {
    private const val TAG = "AiShareEngine"
    const val IDLE_MS = 90_000L

    private val SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
    // Greedy decoding for structured output (ReMerit's extraction): creative sampling breaks JSON.
    private val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var loadedFile: String? = null
    private var visionReady = false
    private var idleJob: Job? = null

    init {
        runCatching { System.loadLibrary("litertlm_jni") }.onFailure { Log.e(TAG, "LiteRT-LM native library missing", it) }
    }

    /**
     * Answers [req] with the model in [file]. [onText] receives each chunk as it is written. Throws
     * [AiShareException] with LOAD or GENERATE; never a raw exception.
     */
    suspend fun run(context: Context, model: SharedModel, file: File, req: AiRequest, onText: suspend (String) -> Unit): AiResult =
        lock.withLock {
            idleJob?.cancel()
            try {
                val eng = ensure(context, model, file)
                withContext(Dispatchers.IO) { answer(eng, req, onText) }
            } finally {
                idleJob = scope.launch { delay(IDLE_MS); unload() }
            }
        }

    /** Frees the model now (memory pressure, or the model file is about to be deleted). */
    suspend fun unload() = lock.withLock {
        runCatching { engine?.close() }
        engine = null; loadedFile = null; visionReady = false
    }

    /** Whether [file] is the model loaded right now (the app can show "ready"). */
    fun isLoaded(file: File) = loadedFile == file.absolutePath

    @OptIn(ExperimentalApi::class)
    private suspend fun ensure(context: Context, model: SharedModel, file: File): Engine = withContext(Dispatchers.IO) {
        engine?.takeIf { loadedFile == file.absolutePath }?.let { return@withContext it }
        runCatching { engine?.close() }
        engine = null; loadedFile = null
        if (!file.exists()) throw AiShareException(AiShareError.NO_MODEL)
        val cache = File(context.cacheDir, "aishare").apply { mkdirs() }.absolutePath
        val spec = runCatching { ModelInfo.from(file.absolutePath).use { (it as? ModelInfo.Llm)?.hasSpeculativeDecodingSupport() == true } }
            .getOrDefault(false)
        var last: Throwable? = null
        // Vision first when the model has it; never lose text because the vision encoder wouldn't load.
        for (vision in if (model.vision) listOf(true, false) else listOf(false)) {
            // Gemma 3 stays on CPU: on the OnePlus 12R its GPU path loads but decodes nothing but invalid tokens. Gemma 4
            // has GPU builds and tries GPU first; an app only gets the GPU when it declares libOpenCL.so itself.
            for (backend in if (model.vision) listOf("GPU", "CPU") else listOf("CPU")) {
                try {
                    if (spec) ExperimentalFlags.enableSpeculativeDecoding = true
                    val e = Engine(EngineConfig(
                        modelPath = file.absolutePath,
                        backend = if (backend == "GPU") Backend.GPU() else Backend.CPU(numOfThreads = 4),
                        visionBackend = if (vision) (if (backend == "GPU") Backend.GPU() else Backend.CPU(numOfThreads = 4)) else null,
                        maxNumImages = if (vision) 1 else null,
                        cacheDir = cache,
                    ))
                    e.initialize()
                    engine = e; loadedFile = file.absolutePath; visionReady = vision
                    Log.i(TAG, "Loaded ${model.name} on $backend (vision=$vision)")
                    return@withContext e
                } catch (t: Throwable) {
                    last = t
                    Log.w(TAG, "${model.name} wouldn't load on $backend (vision=$vision)", t)
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = false
                }
            }
        }
        throw AiShareException(AiShareError.LOAD, last)
    }

    private suspend fun answer(eng: Engine, req: AiRequest, onText: suspend (String) -> Unit): AiResult {
        if (req.image != null && !visionReady) throw AiShareException(AiShareError.IMAGE)
        val system = req.system.takeIf { it.isNotBlank() }?.let { Contents.of(it) }
        val sampler = if (req.deterministic) GREEDY else SAMPLER
        try {
            if (req.toolJson != null) {
                val described = tool(object : OpenApiTool {
                    override fun getToolDescriptionJsonString(): String = req.toolJson
                    // Never run by the engine: the asking app confirms with the person first.
                    override fun execute(paramsJsonString: String): String = "{}"
                })
                val reply = eng.createConversation(ConversationConfig(systemInstruction = system, tools = listOf(described),
                    samplerConfig = sampler, automaticToolCalling = false)).use { it.sendMessage(req.prompt) }
                val args = reply.toolCalls.firstOrNull()?.arguments?.let { JSONObject(it).toString() }
                return AiResult(reply.toString(), args)
            }
            val message = if (req.image != null) Contents.of(listOf(Content.ImageBytes(req.image), Content.Text(req.prompt)))
                else Contents.of(req.prompt)
            val text = StringBuilder()
            eng.createConversation(ConversationConfig(systemInstruction = system, samplerConfig = sampler)).use { conversation ->
                conversation.sendMessageAsync(message).collect { chunk ->
                    val piece = chunk.toString()
                    if (piece.isNotEmpty()) { text.append(piece); onText(piece) }
                }
            }
            // A broken backend decodes invalid tokens to nothing; report it rather than hand back an empty answer.
            if (text.isBlank()) throw AiShareException(AiShareError.GENERATE)
            return AiResult(text.toString())
        } catch (e: AiShareException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw AiShareException(AiShareError.GENERATE, t)
        }
    }
}

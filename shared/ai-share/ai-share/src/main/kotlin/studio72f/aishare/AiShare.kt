package studio72f.aishare

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

/** A model file this app holds, fully downloaded and checked. */
data class HostedModel(val model: SharedModel, val file: File)

/**
 * A model ready to answer, run either in this app ([local]) or by another 72F app ([providerPackage]). Close it when
 * done; a remote one holds a connection to the other app.
 */
interface SharedLlm : AutoCloseable {
    val model: SharedModel
    val providerPackage: String
    val local: Boolean

    /** The answer as it is written. Fails with [AiShareException]. */
    fun stream(req: AiRequest): Flow<String>

    /** The whole answer, and a tool's arguments when [AiRequest.toolJson] was given. */
    suspend fun generate(req: AiRequest): Result<AiResult>
}

/**
 * The entry point. In Application.onCreate an app says which models it holds with [hostModels]; from then on it
 * serves them to the other 72F apps, and [find] gives it a model wherever it lives on the phone:
 *
 * ```
 * AiShare.hostModels { listOfNotNull(downloader.hostedOrNull(Models.GEMMA_3_1B)) }
 * val llm = AiShare.find(context, SharedModels.GEMMA_3_1B) ?: return showDownload()
 * ```
 */
object AiShare {
    @Volatile private var provider: (Context) -> List<HostedModel> = { emptyList() }

    /** Registers what this app can serve. Called again whenever a download finishes or a model is deleted is fine. */
    fun hostModels(provider: (Context) -> List<HostedModel>) { this.provider = provider }

    /** The models this app holds now; the provider is the app's own code, so a failure there means "none". */
    fun hosted(context: Context): List<HostedModel> =
        runCatching { provider(context.applicationContext) }.getOrDefault(emptyList())
            // Only a whole file is served: an app whose downloader keeps no checksum still never shares a cut-off one.
            .filter { it.file.isFile && it.file.length() == it.model.sizeBytes }

    /** [model] from this app if it holds it, otherwise from the first 72F app that does; null when none does. */
    suspend fun find(context: Context, model: SharedModel): SharedLlm? {
        hosted(context).firstOrNull { it.model.id == model.id }?.let { return LocalLlm(context.applicationContext, it) }
        return AiShareClient.connect(context.applicationContext, model)
    }

    /** The 72F apps on this phone that hold [model], for "Gemma 3 1B is already in Tofu" in a model picker. */
    suspend fun providers(context: Context, model: SharedModel): List<String> =
        AiShareClient.providers(context.applicationContext, model)

    /** Every shared model available to this app right now, here or in another 72F app. */
    suspend fun available(context: Context): Map<SharedModel, List<String>> =
        SharedModels.ALL.associateWith { m ->
            (if (hosted(context).any { it.model.id == m.id }) listOf(context.packageName) else emptyList()) + providers(context, m)
        }.filterValues { it.isNotEmpty() }
}

private class LocalLlm(private val context: Context, private val hosted: HostedModel) : SharedLlm {
    override val model get() = hosted.model
    override val providerPackage: String get() = context.packageName
    override val local = true

    override fun stream(req: AiRequest): Flow<String> = kotlinx.coroutines.flow.channelFlow {
        SharedEngine.run(context, hosted.model, hosted.file, req) { send(it) }
    }

    override suspend fun generate(req: AiRequest): Result<AiResult> =
        runCatching { SharedEngine.run(context, hosted.model, hosted.file, req) {} }

    override fun close() = Unit
}

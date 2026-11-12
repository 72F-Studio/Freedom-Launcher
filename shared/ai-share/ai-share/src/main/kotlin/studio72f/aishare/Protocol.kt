package studio72f.aishare

import android.os.Bundle

/** Keys and codes shared by every 72F app. Add keys, never rename or reuse one; raise [API_VERSION] when meaning changes. */
object Protocol {
    const val API_VERSION = 1
    const val ACTION = "studio72f.aishare.SHARE"

    const val KEY_MODELS = "models"            // ArrayList<String> of model ids
    const val KEY_MODEL = "model"              // model id (sha256)
    const val KEY_PROMPT = "prompt"
    const val KEY_SYSTEM = "system"
    const val KEY_DETERMINISTIC = "deterministic"
    const val KEY_TOOL = "tool"                // OpenAPI JSON of the one tool the model may choose
    const val KEY_STREAM = "stream"            // send onText chunks (otherwise only onDone)
    const val KEY_TEXT = "text"
    const val KEY_TOOL_ARGS = "toolArgs"

    const val MAX_PROMPT_CHARS = 64_000
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

    fun request(model: SharedModel, req: AiRequest, stream: Boolean) = Bundle().apply {
        putString(KEY_MODEL, model.id)
        putString(KEY_PROMPT, req.prompt)
        putString(KEY_SYSTEM, req.system)
        putBoolean(KEY_DETERMINISTIC, req.deterministic)
        req.toolJson?.let { putString(KEY_TOOL, it) }
        putBoolean(KEY_STREAM, stream)
    }
}

/**
 * Every way a shared request can fail, one stable code each. Apps map these to their own problem screens; the code
 * goes in View more, never a raw exception.
 */
enum class AiShareError(val code: String) {
    NOT_TRUSTED("AIS-01"),   // the other app isn't a verified 72F app
    NO_MODEL("AIS-02"),      // the host no longer holds that model
    LOAD("AIS-03"),          // the model wouldn't load on the host (memory, a damaged file)
    GENERATE("AIS-04"),      // the model failed while answering
    HOST_GONE("AIS-05"),     // the host app was closed, updated or removed mid-answer
    CANCELLED("AIS-06"),
    BAD_REQUEST("AIS-07"),   // the request was empty or too large
    IMAGE("AIS-08"),         // the image couldn't be passed across
    VERSION("AIS-09"),       // the host is an older 72F app that doesn't know this request
    BIND("AIS-10");          // Android wouldn't connect to the host app

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: GENERATE
    }
}

class AiShareException(val error: AiShareError, cause: Throwable? = null) : Exception(error.code, cause)

/** What an app asks a model. [image] needs a vision model (Gemma 4); [toolJson] asks it to pick that tool, nothing more. */
class AiRequest(
    val prompt: String,
    val system: String = "",
    val deterministic: Boolean = false,
    val image: ByteArray? = null,
    val toolJson: String? = null,
)

/** The answer: the whole text, and the chosen tool's arguments as JSON when a tool was offered and picked. */
data class AiResult(val text: String, val toolArgsJson: String? = null)

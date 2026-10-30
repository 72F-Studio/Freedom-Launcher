package com.studio72f.eigen.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Gemini Nano through Android AICore, via ML Kit's GenAI Prompt API. Ported from Tofu's `MlKitEngine`
 * (ai-core), which has been hardened in production: the download flow's DownloadCompleted is authoritative
 * (checkStatus can still report a cached DOWNLOADABLE), the model is warmed up before first use, and any failure
 * closes the client so the next call starts clean.
 */
class MlKitEngine(
    private val clientFactory: () -> GenerativeModel = { Generation.getClient() },
) {
    private var client: GenerativeModel? = null

    suspend fun initialize(onDownload: (Boolean) -> Unit = {}): Result<Unit> {
        val result = try {
            val c = clientFactory()
            client = c
            var status = c.checkStatus()

            if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                Log.i(TAG, "AICore model status is $status; starting download")
                onDownload(true)
                var downloadCompleted = false
                c.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> Log.i(TAG, "AICore model download started")
                        is DownloadStatus.DownloadProgress -> Log.d(TAG, "AICore model download progress: $downloadStatus")
                        is DownloadStatus.DownloadCompleted -> {
                            downloadCompleted = true
                            Log.i(TAG, "AICore model download completed")
                        }
                        is DownloadStatus.DownloadFailed -> throw downloadStatus.e
                    }
                }
                onDownload(false)
                status = if (downloadCompleted) FeatureStatus.AVAILABLE else c.checkStatus()
            }

            if (status == FeatureStatus.AVAILABLE) {
                c.warmup()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("AICore model status is not AVAILABLE: $status"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
        if (result.isFailure) reset()
        return result
    }

    suspend fun generateResponse(prompt: String, systemInstruction: String = ""): Result<String> {
        val model = client ?: return Result.failure(IllegalStateException("ML Kit not initialized"))
        return try {
            val response = model.generateContent(request(prompt, systemInstruction))
            Result.success(response.candidates.firstOrNull()?.text ?: "")
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun generateResponseStream(prompt: String, systemInstruction: String = ""): Flow<String> {
        val model = client ?: throw IllegalStateException("ML Kit not initialized")
        return model.generateContentStream(request(prompt, systemInstruction)).map { chunk ->
            chunk.candidates.firstOrNull()?.text ?: ""
        }
    }

    private fun request(prompt: String, systemInstruction: String): GenerateContentRequest {
        val text = if (systemInstruction.isNotBlank()) "$systemInstruction\n\n$prompt" else prompt
        return GenerateContentRequest.Builder(TextPart(text)).apply {
            temperature = 0.6f
            maxOutputTokens = 384
        }.build()
    }

    suspend fun reset() {
        runCatching { client?.close() }
        client = null
    }

    fun isInitialized(): Boolean = client != null

    companion object {
        private const val TAG = "MlKitEngine"

        /** A status probe that owns no session (Tofu: availability probes never hold a running client). */
        suspend fun probeStatus(): Int? = runCatching {
            val probe = Generation.getClient()
            try { probe.checkStatus() } finally { probe.close() }
        }.onFailure { Log.w(TAG, "AICore availability query failed", it) }.getOrNull()
    }
}

package com.monday8am.koogagent.local

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.monday8am.agent.LocalLLModel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

object LocalInferenceUtils {

    private val TAG = LocalInferenceUtils::class.java.simpleName

    fun initialize(
        context: Context,
        model: LocalLLModel,
    ): Result<LlmModelInstance> {
        Log.d(TAG, "Initializing...")

        val preferredBackend = if (model.isGPUAccelerated) LlmInference.Backend.GPU else LlmInference.Backend.CPU
        val options =
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.path)
                .setMaxTokens(model.maxToken)
                .setPreferredBackend(preferredBackend)
                .build()

        // Create an instance of the LLM Inference task and session.
        return try {
            val llmInference = LlmInference.createFromOptions(context, options)
            val session =
                LlmInferenceSession.createFromOptions(
                    llmInference,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(model.topK)
                        .setTopP(model.topP)
                        .setTemperature(model.temperature)
                        .build(),
                )
            Result.success(LlmModelInstance(engine = llmInference, session = session))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun prompt(instance: LlmModelInstance, prompt: String): Result<String> {
        val session = instance.session
        return try {
            if (prompt.trim().isNotEmpty()) {
                session.addQueryChunk(prompt)
            }
            Result.success(session.generateResponseAsync().await())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close(instance: LlmModelInstance): Result<Unit> {
        try {
            instance.session.close()
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to close the LLM Inference session: ${e.message}"))
        }

        try {
            instance.engine.close()
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to close the LLM Inference engine: ${e.message}"))
        }

        return Result.success(Unit)
    }

    private fun String.cleanUpMediapipeTaskErrorMessage(): String {
        val index = this.indexOf("=== Source Location Trace")
        if (index >= 0) {
            return this.substring(0, index)
        }
        return this
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    // Propagate coroutine cancellation to the future
    cont.invokeOnCancellation {
        this.cancel(true)
    }
    this.addListener(
        {
            try {
                cont.resume(this.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        // Use direct executor to avoid thread pool overhead
        com.google.common.util.concurrent.MoreExecutors.directExecutor()
    )
}

package com.monday8am.koogagent.mediapipe

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.monday8am.agent.LocalLLModel
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class LlmModelInstance(
    val engine: LlmInference,
    var session: LlmInferenceSession,
)

interface LocalInferenceEngine {
    suspend fun initialize(model: LocalLLModel): Result<Unit>
    suspend fun prompt(prompt: String): Result<String>
    fun initializeAsFlow(model: LocalLLModel): Flow<LocalInferenceEngine>
    fun closeSession(): Result<Unit>
}

class LocalInferenceEngineImpl(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalInferenceEngine {

    private var currentInstance: LlmModelInstance? = null

    override suspend fun initialize(model: LocalLLModel): Result<Unit> = withContext(dispatcher) {
        if (currentInstance != null) {
            return@withContext Result.success(Unit)
        }

        runCatching {
            if (!File(model.path).exists()) {
                throw IllegalStateException("Model file not found at path: ${model.path}")
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.path)
                .setMaxTokens(model.contextLength)
                .setPreferredBackend(if (model.isGPUAccelerated) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
                .build()

            val llmInference = LlmInference.createFromOptions(context, options)
            val session = LlmInferenceSession.createFromOptions(
                llmInference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(model.topK)
                    .setTopP(model.topP)
                    .setTemperature(model.temperature)
                    .build(),
            )
            // Assign the new instance upon success
            currentInstance = LlmModelInstance(engine = llmInference, session = session)
        }
    }

    override suspend fun prompt(prompt: String): Result<String> {
        val instance = currentInstance
            ?: return Result.failure(IllegalStateException("Inference engine is not initialized."))

        return withContext(dispatcher) {
            runCatching {
                val session = instance.session
                if (prompt.isNotBlank()) {
                    session.addQueryChunk(prompt)
                }
                session.generateResponseAsync().await()
            }
        }
    }

    override fun initializeAsFlow(model: LocalLLModel): Flow<LocalInferenceEngine> {
        return flow {
            initialize(model = model)
                .onSuccess {
                    emit(this@LocalInferenceEngineImpl)
                }
                .onFailure {
                    throw it
                }
        }
    }

    override fun closeSession(): Result<Unit> {
        val instance = currentInstance ?: return Result.success(Unit) // Nothing to close
        return runCatching {
            currentInstance = null
            instance.session.close()
            instance.engine.close()
        }
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
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
            com.google.common.util.concurrent.MoreExecutors
                .directExecutor(),
        )
    }

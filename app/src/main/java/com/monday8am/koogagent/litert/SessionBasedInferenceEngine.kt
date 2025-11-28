package com.monday8am.koogagent.litert

import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.agent.core.LocalLLModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Session-based implementation of LocalInferenceEngine using LiteRT-LM's low-level Session API.
 *
 * This implementation:
 * - Uses stateless Session instead of stateful Conversation
 * - Manually formats prompts with chat templates (Gemma/Qwen format)
 * - Bypasses automatic tool handling for better control
 * - Provides raw inference without conversation state management
 *
 * Use this when you need:
 * - Fine-grained control over prompt formatting
 * - Custom tool calling protocols (REACT, SIMPLE, etc.)
 * - To avoid Conversation API's automatic tool handling
 *
 * @param chatTemplate The chat template format to use ("gemma", "qwen", or "raw")
 * @param dispatcher Coroutine dispatcher for blocking operations
 */

private data class LlmModelSession(
    val engine: Engine,
    var session: Session,
    val model: LocalLLModel,
)

class SessionBasedInferenceEngine(
    private val chatTemplate: ChatTemplate = ChatTemplate.QWEN,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalInferenceEngine {
    private val logger = Logger.withTag("SessionBasedInferenceEngine")
    private var currentInstance: LlmModelSession? = null

    /**
     * Chat template formats for different models.
     */
    enum class ChatTemplate {
        /**
         * Gemma format:
         * <start_of_turn>user
         * {prompt}<end_of_turn>
         * <start_of_turn>model
         */
        GEMMA,

        /**
         * Qwen format:
         * <|im_start|>user
         * {prompt}<|im_end|>
         * <|im_start|>assistant
         */
        QWEN,

        /**
         * Raw format (no template, prompt as-is)
         */
        RAW,
    }

    override suspend fun initialize(model: LocalLLModel): Result<Unit> =
        withContext(dispatcher) {
            if (currentInstance != null) {
                return@withContext Result.success(Unit)
            }

            runCatching {
                if (!File(model.path).exists()) {
                    throw IllegalStateException("Model file not found at path: ${model.path}")
                }

                val engineConfig =
                    EngineConfig(
                        modelPath = model.path,
                        backend = Backend.CPU,
                        visionBackend = null,
                        audioBackend = null,
                        maxNumTokens = model.contextLength,
                    )

                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                // Create new session for this inference
                val sessionConfig =
                    SessionConfig(
                        samplerConfig =
                            SamplerConfig(
                                topK = model.topK,
                                topP = model.topP.toDouble(),
                                temperature = model.temperature.toDouble(),
                            ),
                    )
                val session = newEngine.createSession(sessionConfig)

                currentInstance = LlmModelSession(engine = newEngine, session = session, model = model)
                logger.i { "✅ Session-based engine initialized" }
            }
        }

    override suspend fun prompt(prompt: String): Result<String> {
        val instance =
            currentInstance
                ?: return Result.failure(IllegalStateException("Inference engine is not initialized."))

        return withContext(dispatcher) {
            runCatching {
                if (prompt.isBlank()) {
                    throw IllegalArgumentException("Prompt cannot be blank")
                }

                // Format prompt with chat template
                val formattedPrompt = formatPrompt(prompt)

                logger.i { "▶️ Starting stateless inference (prompt: ${prompt.length} chars, formatted: ${formattedPrompt.length} chars)" }
                val startTime = System.currentTimeMillis()

                val inputData = listOf(InputData.Text(text = formattedPrompt))
                val response = instance.session.generateContent(inputData = inputData)

                val duration = System.currentTimeMillis() - startTime
                val tokensApprox = response.length / 4
                val tokensPerSec = if (duration > 0) (tokensApprox * 1000.0 / duration) else 0.0

                logger.i {
                    "✅ Inference complete: ${duration}ms | " +
                        "Response: ${response.length} chars (~$tokensApprox tokens) | " +
                        "Speed: %.2f tokens/sec".format(tokensPerSec)
                }

                response
            }
        }
    }

    override fun promptStreaming(prompt: String): Flow<String> {
        val instance =
            currentInstance ?: run {
                Logger.e("LocalInferenceEngine") { "Inference instance is not available." }
                return emptyFlow() // Return an empty flow if there's no instance.
            }
        var startTime = 0L
        val formattedPrompt = formatPrompt(prompt)

        return instance.session
            .generateContentStreamAsFlow(formattedPrompt)
            .onEach { output ->
                Logger.i("LocalInferenceEngine") { "Message content size: $output" }
            }.onStart {
                startTime = System.currentTimeMillis()
                Logger.i("LocalInferenceEngine") { "Streaming inference started." }
            }.onCompletion {
                val duration = System.currentTimeMillis() - startTime
                Logger.i("LocalInferenceEngine") { "✅ Streaming inference complete: ${duration}ms" }
            }.flowOn(dispatcher)
    }

    override fun initializeAsFlow(model: LocalLLModel): Flow<LocalInferenceEngine> =
        flow {
            initialize(model)
                .onSuccess { emit(this@SessionBasedInferenceEngine) }
                .onFailure { throw it }
        }

    override fun resetConversation(): Result<Unit> {
        val instance = currentInstance ?: return Result.failure(IllegalStateException("Engine not initialized"))
        return runCatching {
            instance.session.close()
            val sessionConfig =
                SessionConfig(
                    samplerConfig =
                        SamplerConfig(
                            topK = instance.model.topK,
                            topP = instance.model.topP.toDouble(),
                            temperature = instance.model.temperature.toDouble(),
                        ),
                )
            instance.session = instance.engine.createSession(sessionConfig)
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

    /**
     * Formats the prompt according to the selected chat template.
     */
    private fun formatPrompt(prompt: String): String =
        when (chatTemplate) {
            ChatTemplate.GEMMA -> {
                // Gemma 3 chat template format
                "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
            }

            ChatTemplate.QWEN -> {
                // Qwen chat template format
                // "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
                prompt
            }

            ChatTemplate.RAW -> {
                // No formatting, pass prompt as-is
                prompt
            }
        }
}

/**
 * Extension function to convert Session.generateContentStream into a Flow.
 * This wraps the callback-based API into a more idiomatic Kotlin Flow.
 */
private fun Session.generateContentStreamAsFlow(prompt: String): Flow<String> =
    callbackFlow {
        val inputData = listOf(InputData.Text(text = prompt))

        val callback =
            object : ResponseCallback {
                override fun onNext(response: String) {
                    trySend(response)
                }

                override fun onError(throwable: Throwable): Unit = throw throwable

                override fun onDone() {
                    close()
                }
            }

        generateContentStream(inputData, callback)
        awaitClose { /* No specific action needed on cancellation/close */ }
    }

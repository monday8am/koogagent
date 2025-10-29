package com.monday8am.koogagent.litert

import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.agent.core.LocalLLModel
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM based implementation of LocalInferenceEngine.
 * Uses Engine/Conversation API with MessageCallback for async inference.
 */
private data class LlmModelInstance(
    val engine: Engine,
    var conversation: Conversation,
    val model: LocalLLModel,
)

class LocalInferenceEngineImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalInferenceEngine {
    private var currentInstance: LlmModelInstance? = null

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
                        backend = if (model.isGPUAccelerated) Backend.GPU else Backend.CPU,
                        visionBackend = null, // Text-only inference
                        audioBackend = null, // Text-only inference
                        maxNumTokens = model.contextLength,
                    )

                // Create and initialize engine
                val engine = Engine(engineConfig)
                engine.initialize()

                val conversationConfig =
                    ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = model.topK,
                            topP = model.topP.toDouble(),
                            temperature = model.temperature.toDouble(),
                        ),
                    )
                val conversation = engine.createConversation(conversationConfig)

                currentInstance = LlmModelInstance(engine = engine, conversation = conversation, model = model)
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

                val userMessage = Message.of(prompt)
                instance.conversation.sendMessageAsync(userMessage)
            }
        }
    }

    override fun initializeAsFlow(model: LocalLLModel): Flow<LocalInferenceEngine> =
        flow {
            initialize(model = model)
                .onSuccess {
                    emit(this@LocalInferenceEngineImpl)
                }.onFailure {
                    throw it
                }
        }

    override fun resetConversation(): Result<Unit> {
        val instance = currentInstance ?: return Result.failure(IllegalStateException("Engine not initialized"))
        return runCatching {
            instance.conversation.close()
            val conversationConfig =
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = instance.model.topK,
                        topP = instance.model.topP.toDouble(),
                        temperature = instance.model.temperature.toDouble(),
                    ),
                )
            instance.conversation = instance.engine.createConversation(conversationConfig)
        }
    }

    override fun closeSession(): Result<Unit> {
        val instance = currentInstance ?: return Result.success(Unit) // Nothing to close
        return runCatching {
            currentInstance = null
            instance.conversation.close()
            instance.engine.close()
        }
    }
}

/**
 * Extension function to convert LiteRT-LM's MessageCallback to coroutine-based suspend function.
 * Collects streaming message responses and returns the complete generated text.
 */
@OptIn(InternalCoroutinesApi::class)
private suspend fun Conversation.sendMessageAsync(message: Message): String =
    suspendCancellableCoroutine { continuation ->
        val resultBuilder = StringBuilder()

        val callback =
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    // Extract text content from message and append it
                    message.contents.filterIsInstance<Content.Text>().forEach { content ->
                        resultBuilder.append(content.text)
                    }
                }

                override fun onDone() {
                    // The 'try' block is important for thread safety.
                    // It ensures that we don't resume a continuation that has already been cancelled.
                    continuation.tryResume(resultBuilder.toString())
                }

                override fun onError(throwable: Throwable) {
                    // Same as onDone, we use 'try' to safely resume with an exception.
                    continuation.tryResumeWithException(throwable)
                }
            }

        this.sendMessageAsync(message, callback)

        // Handle coroutine cancellation.
        // LiteRT-LM's Conversation API does not currently offer a direct way to interrupt an ongoing
        // inference task. The coroutine will suspend until the task completes or errors out.
        // Cleanup, like closing the conversation, is handled by the closeSession() method.
        continuation.invokeOnCancellation {
            Logger.i("sendMessageAsync:Coroutine was cancelled, but underlying inference may continue.")
        }
    }

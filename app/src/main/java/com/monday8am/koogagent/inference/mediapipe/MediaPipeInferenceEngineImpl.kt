package com.monday8am.koogagent.inference.mediapipe

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.fc.ChatSession
import com.google.ai.edge.localagents.fc.GenerativeModel
import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.ai.edge.localagents.fc.HammerFormatter
import com.google.ai.edge.localagents.fc.LlamaFormatter
import com.google.ai.edge.localagents.fc.LlmInferenceBackend
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.ModelConfiguration
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val SYSTEM_PROMPT = "You are Hammer, a helpful AI assistant."
private const val SYSTEM_ROLE = "system"
private const val USER_ROLE = "user"

/**
 * MediaPipe-based implementation of LocalInferenceEngine using AI Edge On-Device APIs.
 * Uses GenerativeModel/ChatSession API with function calling support for Hammer2.1 model.
 */

class MediaPipeInferenceEngineImpl(
    private val context: Context,
    private val tools: List<Tool> = emptyList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalInferenceEngine {
    private var generativeModel: GenerativeModel? = null
    private var chatSession: ChatSession? = null
    private var modelConfig: ModelConfiguration? = null
    private var currentModelPath: String? = null
    private var llmInference: LlmInference? = null

    override suspend fun initialize(modelConfig: ModelConfiguration, modelPath: String): Result<Unit> =
        withContext(dispatcher) {
            if (generativeModel != null) {
                return@withContext Result.success(Unit)
            }

            runCatching {
                llmInference = createLlmInference(context, modelPath, modelConfig)
                val formatter = createFormatter(modelConfig.modelFamily)
                val backend = LlmInferenceBackend(llmInference!!, formatter)
                val systemInstruction = createSystemInstruction()

                generativeModel = GenerativeModel(backend, systemInstruction, tools)
                chatSession = generativeModel!!.startChat()
                this@MediaPipeInferenceEngineImpl.modelConfig = modelConfig
                this@MediaPipeInferenceEngineImpl.currentModelPath = modelPath
            }
        }

    override suspend fun prompt(prompt: String): Result<String> {
        val session =
            chatSession
                ?: return Result.failure(IllegalStateException("Inference engine is not initialized."))

        return withContext(dispatcher) {
            runCatching {
                if (prompt.isBlank()) {
                    throw IllegalArgumentException("Prompt cannot be blank")
                }

                Logger.i("MediaPipeInferenceEngine") { "▶️ Starting inference (prompt: ${prompt.length} chars)" }
                val startTime = System.currentTimeMillis()
                val response = session.sendMessageBlocking(prompt)
                val duration = System.currentTimeMillis() - startTime
                val tokensApprox = response.length / 4 // Rough estimate: 1 token ≈ 4 chars
                val tokensPerSec = if (duration > 0) (tokensApprox * 1000.0 / duration) else 0.0

                Logger.i("MediaPipeInferenceEngine") {
                    "✅ Inference complete: ${duration}ms | " +
                            "Response: ${response.length} chars (~$tokensApprox tokens) | " +
                            "Speed: %.2f tokens/sec".format(tokensPerSec)
                }
                response
            }
        }
    }

    override fun promptStreaming(prompt: String): Flow<String> {
        val session =
            chatSession ?: run {
                Logger.e("MediaPipeInferenceEngine") { "Inference session is not available." }
                return emptyFlow()
            }

        return flow {
            val startTime = System.currentTimeMillis()
            try {
                val response = session.sendMessageBlocking(prompt)
                emit(response)
                val duration = System.currentTimeMillis() - startTime
                Logger.i("MediaPipeInferenceEngine") { "✅ Streaming inference complete: ${duration}ms" }
            } catch (e: Exception) {
                Logger.e("MediaPipeInferenceEngine", e) { "Streaming inference failed" }
                throw e
            }
        }.flowOn(dispatcher)
    }

    override fun initializeAsFlow(modelConfig: ModelConfiguration, modelPath: String): Flow<LocalInferenceEngine> =
        flow {
            initialize(modelConfig = modelConfig, modelPath = modelPath)
                .onSuccess {
                    emit(this@MediaPipeInferenceEngineImpl)
                }.onFailure {
                    throw it
                }
        }

    override fun resetConversation(): Result<Unit> {
        val config = modelConfig ?: return Result.failure(IllegalStateException("Engine not initialized"))
        val path = currentModelPath ?: return Result.failure(IllegalStateException("Model path not available"))

        return runCatching {
            // Close existing resources to prevent native memory corruption
            llmInference?.close()
            chatSession = null
            generativeModel = null

            // Recreate everything from scratch
            llmInference = createLlmInference(context, path, config)
            val formatter = createFormatter(config.modelFamily)
            val backend = LlmInferenceBackend(llmInference!!, formatter)
            val systemInstruction = createSystemInstruction()

            generativeModel = GenerativeModel(backend, systemInstruction, tools)
            chatSession = generativeModel!!.startChat()

            Logger.i("MediaPipeInferenceEngine") { "💬 Reset conversation with full session recreation!" }
        }
    }

    override fun closeSession(): Result<Unit> {
        if (generativeModel == null) return Result.success(Unit)
        return runCatching {
            llmInference?.close()
            chatSession = null
            generativeModel = null
            modelConfig = null
            currentModelPath = null
            llmInference = null
            Logger.i("MediaPipeInferenceEngine") { "Closed MediaPipe session" }
        }
    }
}

private fun createFormatter(modelFamily: String) = when (modelFamily.lowercase()) {
    "gemma3", "gemma" -> GemmaFormatter()
    "hammer2", "hammer" -> HammerFormatter()
    "llama" -> LlamaFormatter()
    else -> GemmaFormatter() // Default fallback
}

private suspend fun ChatSession.sendMessageBlocking(message: String): String = suspendCancellableCoroutine { cont ->
    try {
        val userContent =
            Content
                .newBuilder()
                .setRole(USER_ROLE)
                .addParts(Part.newBuilder().setText(message))
                .build()

        val response = this.sendMessage(userContent)

        if (response == null) {
            Logger.e("MediaPipeInferenceEngine") { "Received null response from sendMessage" }
            cont.resumeWithException(IllegalStateException("Received null response from inference engine"))
            return@suspendCancellableCoroutine
        }

        val part =
            response.candidatesList
                ?.firstOrNull()
                ?.content
                ?.partsList
                ?.firstOrNull()

        val resultText =
            when {
                part?.hasText() == true -> {
                    part.text
                }

                part?.hasFunctionCall() == true -> {
                    // In a full implementation, this would execute the tool and send back results
                    val functionCall = part.functionCall
                    "Function call detected: ${functionCall.name} with args: ${functionCall.args}"
                }

                else -> {
                    Logger.w("MediaPipeInferenceEngine") { "Response has no text or function call" }
                    ""
                }
            }
        cont.resume(resultText)
    } catch (e: Exception) {
        Logger.e("MediaPipeInferenceEngine", e) { "Error in sendMessageBlocking: ${e.message}" }
        cont.resumeWithException(e)
    }
}

private fun createLlmInference(context: Context, modelPath: String, modelConfig: ModelConfiguration): LlmInference {
    if (!File(modelPath).exists()) {
        throw IllegalStateException("Model file not found at path: $modelPath")
    }
    val backend =
        if (modelConfig.hardwareAcceleration == HardwareBackend.GPU_SUPPORTED) {
            Backend.GPU
        } else {
            Backend.CPU
        }
    val llmInferenceOptions =
        LlmInference.LlmInferenceOptions
            .builder()
            .setModelPath(modelPath)
            .setMaxTokens(modelConfig.defaultMaxOutputTokens)
            .setPreferredBackend(backend)
            .build()
    return LlmInference.createFromOptions(context, llmInferenceOptions)
}

private fun createSystemInstruction(): Content = Content
    .newBuilder()
    .setRole(SYSTEM_ROLE)
    .addParts(
        Part
            .newBuilder()
            .setText(SYSTEM_PROMPT),
    ).build()

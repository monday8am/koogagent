package com.monday8am.agent.core

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import co.touchlab.kermit.Logger
import com.monday8am.agent.local.LocalInferenceLLMClient

/**
 * Agent backend type determines which executor and configuration to use.
 */
enum class AgentBackend {
    /** Local inference (LiteRT-LM, etc.) - requires promptExecutor and toolFormat */
    LOCAL,

    /** Koog framework agent (Ollama, etc.) - uses built-in executors */
    KOOG,
}

/**
 * Unified agent implementation that supports multiple LLM backends.
 *
 * Use factory methods for clearer construction:
 * - `NotificationAgent.local()` for local inference-based agents (LiteRT-LM, MediaPipe)
 * - `NotificationAgent.koog()` for Koog framework agents (Ollama, etc.)
 *
 * Tool calling is handled natively by the platform implementations:
 * - LiteRT-LM: Uses @Tool annotations and model-specific processors
 * - MediaPipe: Uses HammerFormatter with protobuf Tool objects
 */
class NotificationAgent private constructor(
    private val backend: AgentBackend,
    private val promptExecutor: (suspend (String) -> String?)?,
    private val model: LLModel,
) {
    companion object {
        /**
         * Creates an agent for local inference (e.g., LiteRT-LM or MediaPipe).
         *
         * Tool calling is handled natively by the platform implementation:
         * - LiteRT-LM: Uses @Tool annotations, tools passed via ConversationConfig
         * - MediaPipe: Uses HammerFormatter with protobuf Tool objects
         *
         * @param promptExecutor Function that executes prompts against the local LLM
         * @param modelId Model identifier (e.g., "gemma3-1b-it-int4", "qwen3-0.6b", "hammer2-0.5b")
         * @param modelProvider LLM provider (default: Google)
         */
        fun local(
            promptExecutor: suspend (String) -> String?,
            modelId: String,
            modelProvider: LLMProvider = LLMProvider.Google,
        ) = NotificationAgent(
            backend = AgentBackend.LOCAL,
            promptExecutor = promptExecutor,
            model =
            LLModel(
                provider = modelProvider,
                id = modelId,
                capabilities =
                listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.Schema.JSON.Standard,
                ),
                maxOutputTokens = 1024L, // Metadata only, actual value from ModelConfiguration
                contextLength = 4096L, // Metadata only, actual value from ModelConfiguration
            ),
        )

        /**
         * Creates an agent using Koog framework's built-in executors (e.g., Ollama).
         *
         * This mirrors AIAgent's API by accepting an LLModel directly, avoiding
         * the need to rebuild model information that's already available.
         *
         * @param model LLModel instance (typically from OllamaClient.getModels().toLLModel())
         */
        fun koog(model: LLModel) = NotificationAgent(
            backend = AgentBackend.KOOG,
            promptExecutor = null,
            model = model,
        )
    }

    private var registry: ToolRegistry? = null
    private val logger = Logger.withTag("NotificationAgent")

    /**
     * Initializes the agent with a tool registry.
     *
     * @param toolRegistry Registry containing available tools
     */
    fun initializeWithTools(toolRegistry: ToolRegistry) {
        registry = toolRegistry
    }

    /**
     * Generates a message using the configured LLM and tool calling protocol.
     *
     * @param systemPrompt System-level instructions for the agent
     * @param userPrompt User's input message
     * @return Generated response text
     */
    suspend fun generateMessage(systemPrompt: String, userPrompt: String): String = try {
        getAIAgent(systemPrompt = systemPrompt).run(
            agentInput = userPrompt,
        )
    } catch (e: Exception) {
        logger.e("Error generating message", e)
        "Error generating message"
    }

    private fun getAIAgent(systemPrompt: String): AIAgent<String, String> {
        logger.d { "Using backend: $backend, model: ${model.id}" }

        val executor =
            when (backend) {
                AgentBackend.LOCAL -> {
                    requireNotNull(promptExecutor) { "promptExecutor required for LOCAL backend" }
                    LocalInferenceAIExecutor(llmClient = createLLMClient())
                }

                AgentBackend.KOOG -> {
                    simpleOllamaAIExecutor()
                }
            }

        return AIAgent(
            promptExecutor = executor,
            systemPrompt = systemPrompt,
            temperature = 0.2,
            llmModel = model,
            toolRegistry = registry ?: ToolRegistry.EMPTY,
            installFeatures = installCommonEventHandling,
        )
    }

    private fun createLLMClient(): LLMClient {
        val executor = requireNotNull(promptExecutor) { "promptExecutor required for LOCAL backend" }
        return LocalInferenceLLMClient(promptExecutor = executor)
    }
}

private class LocalInferenceAIExecutor(
    llmClient: LLMClient,
) : SingleLLMPromptExecutor(llmClient)

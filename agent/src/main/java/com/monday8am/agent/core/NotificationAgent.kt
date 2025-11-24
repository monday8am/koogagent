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
import com.monday8am.agent.local.HermesLLMClient
import com.monday8am.agent.local.LiteRTLLMClient
import com.monday8am.agent.local.OpenApiLLMClient
import com.monday8am.agent.local.ReActLLMClient
import com.monday8am.agent.local.SimpleLLMClient
import com.monday8am.agent.local.SlimLLMClient
import kotlinx.coroutines.flow.Flow

/**
 * Tool calling format options for the notification agent.
 *
 * @property SIMPLE Simplified JSON format: `{"tool":"ToolName"}` (no parameters) - Custom protocol for Gemma
 * @property OPENAPI OpenAPI specification format: `{"name":"FunctionName", "parameters":{...}}` - Custom protocol for Gemma
 * @property SLIM XML-like tag format: `<function>FunctionName</function>` with optional `<parameters>{...}</parameters>` - Custom protocol for Gemma
 * @property REACT ReAct (Reasoning and Acting) pattern: Natural language with `Thought:` and `Action:` (recommended by Google) - Custom protocol for Gemma
 * @property HERMES Hermes-style Qwen format: XML tags with `<tools></tools>` for definitions and `<tool_call></tool_call>` for invocations - Custom protocol
 * @property NATIVE Native LiteRT-LM tool calling: Tools passed via ConversationConfig, handled by model-specific processors (Qwen3DataProcessor, etc.)
 */
enum class ToolFormat {
    SIMPLE,
    OPENAPI,
    SLIM,
    REACT,
    HERMES,
    NATIVE,
}

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
 * Unified agent implementation that supports multiple LLM backends and tool calling protocols.
 *
 * Use factory methods for clearer construction:
 * - `NotificationAgent.local()` for local inference-based agents (Gemma, etc.)
 * - `NotificationAgent.koog()` for Koog framework agents (Ollama, etc.)
 */
class NotificationAgent private constructor(
    private val backend: AgentBackend,
    private val promptExecutor: (suspend (String) -> String?)?,
    private val streamPromptExecutor: ((String) -> Flow<String>)?,
    private val model: LLModel,
    private val toolFormat: ToolFormat?,
) {
    companion object {
        /**
         * Creates an agent for local inference (e.g., LiteRT-LM with Gemma or Qwen3).
         *
         * @param promptExecutor Function that executes prompts against the local LLM
         * @param streamPromptExecutor Optional function that streams responses from the local LLM
         * @param modelId Model identifier (e.g., "gemma3-1b-it-int4", "qwen3-0.6b")
         * @param toolFormat Tool calling protocol:
         *                   - NATIVE: Use LiteRT-LM's native tool calling (for Qwen3, etc.)
         *                   - REACT/HERMES/etc: Use custom text-based protocols (for Gemma, etc.)
         * @param modelProvider LLM provider (default: Google)
         */
        fun local(
            promptExecutor: suspend (String) -> String?,
            streamPromptExecutor: ((String) -> Flow<String>)? = null,
            modelId: String,
            toolFormat: ToolFormat = ToolFormat.NATIVE,
            modelProvider: LLMProvider = LLMProvider.Google,
        ) = NotificationAgent(
            backend = AgentBackend.LOCAL,
            promptExecutor = promptExecutor,
            streamPromptExecutor = streamPromptExecutor,
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
                    maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS.toLong(),
                    contextLength = DEFAULT_CONTEXT_LENGTH.toLong(),
                ),
            toolFormat = toolFormat,
        )

        /**
         * Creates an agent using Koog framework's built-in executors (e.g., Ollama).
         *
         * This mirrors AIAgent's API by accepting an LLModel directly, avoiding
         * the need to rebuild model information that's already available.
         *
         * @param model LLModel instance (typically from OllamaClient.getModels().toLLModel())
         */
        fun koog(model: LLModel) =
            NotificationAgent(
                backend = AgentBackend.KOOG,
                promptExecutor = null,
                streamPromptExecutor = null,
                model = model,
                toolFormat = null,
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
    suspend fun generateMessage(
        systemPrompt: String,
        userPrompt: String,
    ): String =
        try {
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
                    requireNotNull(toolFormat) { "toolFormat required for LOCAL backend" }
                    LocalInferenceAIExecutor(llmClient = createLLMClient())
                }

                AgentBackend.KOOG -> {
                    simpleOllamaAIExecutor()
                }
            }

        return AIAgent(
            promptExecutor = executor,
            systemPrompt = systemPrompt,
            temperature = 0.7,
            llmModel = model,
            toolRegistry = registry ?: ToolRegistry.EMPTY,
            installFeatures = installCommonEventHandling,
        )
    }

    private fun createLLMClient(): LLMClient {
        val executor = requireNotNull(promptExecutor) { "promptExecutor required for LOCAL backend" }
        val format = requireNotNull(toolFormat) { "toolFormat required for LOCAL backend" }
        return when (format) {
            ToolFormat.SIMPLE -> {
                SimpleLLMClient(promptExecutor = executor)
            }

            ToolFormat.OPENAPI -> {
                OpenApiLLMClient(promptExecutor = executor)
            }

            ToolFormat.SLIM -> {
                SlimLLMClient(promptExecutor = executor)
            }

            ToolFormat.REACT -> {
                ReActLLMClient(promptExecutor = executor)
            }

            ToolFormat.HERMES -> {
                HermesLLMClient(promptExecutor = executor)
            }

            ToolFormat.NATIVE -> {
                LiteRTLLMClient(
                    promptExecutor = executor,
                    streamPromptExecutor = streamPromptExecutor,
                )
            }
        }
    }
}

private class LocalInferenceAIExecutor(
    llmClient: LLMClient,
) : SingleLLMPromptExecutor(llmClient)

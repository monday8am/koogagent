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
import com.monday8am.agent.gemma.GemmaLLMClient
import com.monday8am.agent.gemma.OpenApiLLMClient
import com.monday8am.agent.gemma.ReActLLMClient
import com.monday8am.agent.gemma.SlimLLMClient
import com.monday8am.agent.ollama.OllamaLLMClient

/**
 * Tool calling format options for the notification agent.
 *
 * @property SIMPLE Simplified JSON format: `{"tool":"ToolName"}` (no parameters)
 * @property OPENAPI OpenAPI specification format: `{"name":"FunctionName", "parameters":{...}}`
 * @property SLIM XML-like tag format: `<function>FunctionName</function>` with optional `<parameters>{...}</parameters>`
 * @property REACT ReAct (Reasoning and Acting) pattern: Natural language with `Thought:` and `Action:` (recommended by Google)
 * @property NATIVE Native tool calling via Ollama API (OpenAI-compatible format)
 */
enum class ToolFormat {
    SIMPLE,
    OPENAPI,
    SLIM,
    REACT,
    NATIVE,
}

/**
 * Unified agent implementation that supports multiple LLM backends and tool calling protocols.
 *
 * This class replaces the previous GemmaAgent and OllamaAgent implementations, providing
 * a single configurable agent that works with different models and tool formats.
 *
 * @param promptExecutor Function that executes prompts against the LLM (for text-based protocols)
 * @param modelId Model identifier (e.g., "gemma3-1b-it-int4", "llama3")
 * @param toolFormat Tool calling protocol to use (default: REACT)
 * @param modelProvider LLM provider (default: Google)
 */
class NotificationAgentImpl(
    private val promptExecutor: suspend (String) -> String?,
    private val modelId: String,
    private val toolFormat: ToolFormat = ToolFormat.REACT,
    private val modelProvider: LLMProvider = LLMProvider.Google,
) {
    private var registry: ToolRegistry? = null
    private val logger = Logger.withTag("NotificationAgentImpl")

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
        logger.d { "Using tool format: $toolFormat, model: $modelId" }

        val llmModel = buildLLModel()

        return when (toolFormat) {
            ToolFormat.NATIVE -> {
                // Use Ollama's native tool calling
                AIAgent(
                    promptExecutor = simpleOllamaAIExecutor(),
                    systemPrompt = systemPrompt,
                    temperature = 0.7,
                    llmModel = llmModel,
                    toolRegistry = registry ?: ToolRegistry.EMPTY,
                    installFeatures = installCommonEventHandling,
                )
            }
            else -> {
                // Use text-based tool calling protocol with custom LLM client
                val client = createLLMClient()
                AIAgent(
                    promptExecutor =
                        SimpleGemmaAIExecutor(
                            llmClient = client,
                        ),
                    systemPrompt = systemPrompt,
                    temperature = 0.7,
                    llmModel = llmModel,
                    toolRegistry = registry ?: ToolRegistry.EMPTY,
                    installFeatures = installCommonEventHandling,
                )
            }
        }
    }

    private fun createLLMClient(): LLMClient =
        when (toolFormat) {
            ToolFormat.SIMPLE -> GemmaLLMClient(promptExecutor = promptExecutor)
            ToolFormat.OPENAPI -> OpenApiLLMClient(promptExecutor = promptExecutor)
            ToolFormat.SLIM -> SlimLLMClient(promptExecutor = promptExecutor)
            ToolFormat.REACT -> ReActLLMClient(promptExecutor = promptExecutor)
            ToolFormat.NATIVE -> OllamaLLMClient(promptExecutor = promptExecutor)
        }

    private fun buildLLModel(): LLModel =
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
        )
}

private class SimpleGemmaAIExecutor(
    llmClient: LLMClient,
) : SingleLLMPromptExecutor(llmClient)

package com.monday8am.agent.gemma

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import co.touchlab.kermit.Logger
import com.monday8am.agent.core.DEFAULT_CONTEXT_LENGTH
import com.monday8am.agent.core.DEFAULT_MAX_OUTPUT_TOKENS
import com.monday8am.agent.core.NotificationAgent
import com.monday8am.agent.core.installCommonEventHandling

private val gemmaModel =
    LLModel(
        provider = LLMProvider.Google,
        id = "gemma3-1b-it-int4",
        capabilities =
            listOf(
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.Schema.JSON.Standard,
            ),
        maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS.toLong(),
        contextLength = DEFAULT_CONTEXT_LENGTH.toLong(), // Actual Gemma 3n-1b-it context: 4096 tokens
    )

class GemmaAgent(
    private val promptExecutor: suspend (String) -> String?,
    private val useOpenApiForTools: Boolean = false,
) : NotificationAgent {
    private var registry: ToolRegistry? = null
    private val logger = Logger.withTag("GemmaAgent")

    override fun initializeWithTools(toolRegistry: ToolRegistry) {
        registry = toolRegistry
    }

    override suspend fun generateMessage(
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
        val client = if (useOpenApiForTools) OpenApiLLMClient(promptExecutor) else GemmaLLMClient(promptExecutor = promptExecutor)
        return AIAgent(
            promptExecutor =
                SimpleGemmaAIExecutor(
                    llmClient = client,
                ),
            systemPrompt = systemPrompt,
            temperature = 0.7,
            llmModel = gemmaModel,
            toolRegistry = registry ?: ToolRegistry.EMPTY,
            installFeatures = installCommonEventHandling,
        )
    }
}

private class SimpleGemmaAIExecutor(
    llmClient: LLMClient,
) : SingleLLMPromptExecutor(llmClient)

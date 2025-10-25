package com.monday8am.agent.ollama

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.asAssistantMessage
import ai.koog.agents.core.dsl.extension.containsToolCalls
import ai.koog.agents.core.dsl.extension.executeMultipleTools
import ai.koog.agents.core.dsl.extension.extractToolCalls
import ai.koog.agents.core.dsl.extension.requestLLM
import ai.koog.agents.core.dsl.extension.requestLLMMultiple
import ai.koog.agents.core.dsl.extension.sendMultipleToolResults
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import co.touchlab.kermit.Logger
import com.monday8am.agent.core.NotificationAgent

private val logger = Logger.withTag("OllamaAgent")

class OllamaAgent : NotificationAgent {
    private val client = OllamaClient()
    private var registry: ToolRegistry? = null

    override fun initializeWithTools(toolRegistry: ToolRegistry) {
        registry = toolRegistry
    }

    override suspend fun generateMessage(
        systemPrompt: String,
        userPrompt: String,
    ): String =
        try {
            logger.d { "Generating message with userPrompt: $userPrompt" }
            getAIAgent(systemPrompt = systemPrompt).run(
                agentInput = userPrompt,
            )
        } catch (e: Exception) {
            logger.e(e) { "Error generating message: ${e.message}" }
            "Error generating message"
        }

    private suspend fun getAIAgent(systemPrompt: String): AIAgent<String, String> {
        if (registry == null) {
            logger.e { "ToolRegistry is null!" }
            throw Exception("OllamaAgent: ToolRegistry is null!")
        }

        val llModel = client.getModels().firstOrNull()?.toLLModel()
        if (llModel == null) {
            logger.e { "No Ollama models found" }
            throw Exception("No models found")
        }
        logger.i { "Using Ollama model: ${llModel.id}" }

        return AIAgent(
            promptExecutor = simpleOllamaAIExecutor(),
            systemPrompt = systemPrompt,
            temperature = 0.7,
            toolRegistry = registry ?: ToolRegistry.EMPTY,
            llmModel = llModel,
            strategy =
                functionalStrategy { input ->
                    logger.d { "Calling LLM with input: $input" }
                    var responses = requestLLMMultiple(input)

                    val hasToolCalls = responses.containsToolCalls()
                    logger.d { "Response contains tool calls: $hasToolCalls" }

                    while (responses.containsToolCalls()) {
                        val pendingCalls = extractToolCalls(responses)
                        logger.i { "Executing ${pendingCalls.size} tool calls:" }
                        pendingCalls.forEach { call ->
                            logger.i { "  - ${call.tool}: ${call.content}" }
                        }
                        val results = executeMultipleTools(pendingCalls)
                        responses = sendMultipleToolResults(results)
                    }

                    val draft = responses.single().asAssistantMessage().content
                    logger.d { "Draft response: $draft" }
                    logger.d { "Requesting LLM to improve and clarify draft" }
                    requestLLM("Improve and clarify: $draft").asAssistantMessage().content
                },
        )
    }
}

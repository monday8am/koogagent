package com.monday8am.local

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
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import ai.koog.prompt.llm.LLModel
import com.monday8am.agent.NotificationAgent
import com.monday8am.agent.installCommonEventHandling

class OllamaAgent : NotificationAgent {
    private val client = OllamaClient()
    private var agent: AIAgent<String, String>? = null
    private var registry: ToolRegistry? = null

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
            println(e)
            "Error generating message"
        }

    private suspend fun getAIAgent(systemPrompt: String): AIAgent<String, String> {
        if (registry == null) {
            throw Exception("OllamaAgent: ToolRegistry is null!")
        }

        val llModel = client.getModels().firstOrNull()?.toLLModel()
        if (llModel == null) {
            throw Exception("No models found")
        }

        return AIAgent(
            promptExecutor = simpleOllamaAIExecutor(),
            systemPrompt = systemPrompt,
            temperature = 0.7,
            toolRegistry = registry ?: ToolRegistry.EMPTY,
            llmModel = llModel,
            strategy = functionalStrategy { input ->
                println("Calling LLM with Input = $input")
                var responses = requestLLMMultiple(input)

                while (responses.containsToolCalls()) {
                    val pendingCalls = extractToolCalls(responses)
                    println("Pending Calls")
                    println(pendingCalls.map { "${it.tool} ${it.content}" })
                    val results = executeMultipleTools(pendingCalls)
                    responses = sendMultipleToolResults(results)
                }

                val draft = responses.single().asAssistantMessage().content
                requestLLM("Improve and clarify: $draft").asAssistantMessage().content
            },
        )
    }
}

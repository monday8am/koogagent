package com.monday8am.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel

class MealAgent(
    private val country: String,
    private val season: String,
    private val meal: String,
    private val language: String
) {

    private val client = OllamaClient(baseUrl = "http://10.0.2.2:11434")
    private var agent: AIAgent<String, String>? = null

    private val systemPrompt = PromptTemplate.Builder(
        template = """
                You're a motivational nutrition assistant.
                The user is in {{country}}, it's {{season}}.
                They haven't logged their {{meal}} yet.
                """.trimIndent()
    ).build()

    private val userPrompt = PromptTemplate.Builder(
        template = "Suggest a local dish or drink and encourage them in {{language}}."
    ).build()

    suspend fun generateMessage(): String {
        val systemPrompt = systemPrompt.fill(
            mapOf(
                "country" to country,
                "season" to season,
                "meal" to meal,
            )
        )
        return try {
            getAIAgent(systemPrompt = systemPrompt).run(
                agentInput = userPrompt.fill(
                    values = mapOf("language" to language)
                )
            )
        } catch (e: Exception) {
            println(e)
            "Error generating message"
        }
    }

    private suspend fun getAIAgent(systemPrompt: String): AIAgent<String, String> {
        if (agent == null) {
            agent = client.getModels().firstOrNull()?.toLLModel()?.let { llModel ->
                AIAgent(
                    executor = simpleOllamaAIExecutor(),
                    systemPrompt = systemPrompt,
                    temperature = 0.7,
                    toolRegistry = ToolRegistry {
                        tool(SayToUser)
                    },
                    llmModel = llModel,
                ) {
                    handleEvents {
                        onToolCall { eventContext ->
                            println("Tool called: ${eventContext.tool} with args ${eventContext.toolArgs}")
                        }
                        onAgentFinished { eventContext ->
                            println("Agent finished with result: ${eventContext.result}")
                        }
                        onAgentRunError { errorContext ->
                            println("Agent error with result: ${errorContext.throwable}")
                        }
                    }
                }
            }
        }
        return agent!!
    }
}

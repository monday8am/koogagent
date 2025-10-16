package com.monday8am.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel

interface NotificationAgent {
    suspend fun generateMessage(
        systemPrompt: String,
        userPrompt: String,
    ): String

    fun initializeWithTools(toolSet: WeatherToolSet)
}

class OllamaAgent : NotificationAgent {
    private val client = OllamaClient(baseUrl = "http://10.0.2.2:11434")
    private var agent: AIAgent<String, String>? = null
    private var weatherToolSet: WeatherToolSet? = null

    override fun initializeWithTools(toolSet: WeatherToolSet) {
        weatherToolSet = toolSet
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
        if (weatherToolSet == null) {
            throw Exception("Tools aren't initialized")
        }

        if (agent == null) {
            agent =
                client.getModels().firstOrNull()?.toLLModel()?.let { llModel ->
                    AIAgent(
                        executor = simpleOllamaAIExecutor(),
                        systemPrompt = systemPrompt,
                        temperature = 0.7,
                        toolRegistry =
                            ToolRegistry {
                                tools(weatherToolSet!!.asTools() + SayToUser)
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

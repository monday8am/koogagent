package com.monday8am.local

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import com.monday8am.agent.NotificationAgent
import com.monday8am.agent.WeatherTool
import com.monday8am.agent.installCommonEventHandling

class OllamaAgent : NotificationAgent {
    private val client = OllamaClient()
    private var agent: AIAgent<String, String>? = null
    private var weatherTool: WeatherTool? = null

    override fun initializeWithTool(tool: WeatherTool) {
        weatherTool = tool
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
        if (weatherTool == null) {
            throw Exception("Tools aren't initialized")
        }

        if (agent == null) {
            agent =
                client.getModels().firstOrNull()?.toLLModel()?.let { llModel ->
                    AIAgent(
                        promptExecutor = simpleOllamaAIExecutor(),
                        systemPrompt = systemPrompt,
                        temperature = 0.7,
                        toolRegistry =
                            ToolRegistry {
                                weatherTool
                                SayToUser
                            },
                        llmModel = llModel,
                        installFeatures = installCommonEventHandling,
                    )
                }
        }
        return agent!!
    }
}

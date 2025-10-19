package com.monday8am.koogagent.mediapipe

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.monday8am.agent.DEFAULT_MAX_TOKEN
import com.monday8am.agent.NotificationAgent
import com.monday8am.agent.installCommonEventHandling

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
        maxOutputTokens = DEFAULT_MAX_TOKEN.toLong(),
        contextLength = 128_000,
    )

class GemmaAgent(
    private val instance: LlmModelInstance,
) : NotificationAgent {
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

    private fun getAIAgent(systemPrompt: String): AIAgent<String, String> {
        if (agent == null) {
            agent =
                AIAgent(
                    promptExecutor = SimpleGemmaAIExecutor(llmClient = GemmaLLMClient(instance = instance)),
                    systemPrompt = systemPrompt,
                    temperature = 0.7,
                    llmModel = gemmaModel,
                    toolRegistry = registry ?: ToolRegistry.EMPTY,
                    installFeatures = installCommonEventHandling,
                )
        }
        return agent!!
    }
}

private class SimpleGemmaAIExecutor(
    llmClient: LLMClient,
) : SingleLLMPromptExecutor(llmClient)

private class GemmaLLMClient(
    private val instance: LlmModelInstance,
) : LLMClient {
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val prompt = prompt.messages.joinToString("\n") { it.content }
        return LocalInferenceUtils.prompt(instance, prompt).getOrNull()?.let {
            listOf(
                Message.Assistant(metaInfo = ResponseMetaInfo.Empty, content = it),
            )
        } ?: listOf()
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = throw Exception("Not supported")

    override fun llmProvider(): LLMProvider = object : LLMProvider("gemma", "Gemma") { }
}

package com.monday8am.agent.gemma

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import co.touchlab.kermit.Logger

internal class SimpleLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("SimpleLLMClient")

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.d { "Executing prompt: $prompt with tools: $tools and model: $model" }

        val fullPrompt = buildFullPrompt(prompt)
        val response = promptExecutor(fullPrompt)

        if (response == null) {
            logger.w { "MediaPipe returned null response" }
            return emptyList()
        }

        return listOf(
            Message.Assistant(
                content = response,
                metaInfo = ResponseMetaInfo.Empty,
            ),
        )
    }

    private fun buildFullPrompt(prompt: Prompt): String {
        val systemMessage =
            prompt.messages
                .filterIsInstance<Message.System>()
                .firstOrNull()
                ?.content ?: ""

        val conversation =
            prompt.messages
                .filterNot { it is Message.System }
                .mapNotNull { message ->
                    when (message) {
                        is Message.User -> {
                            "User: ${message.content}"
                        }

                        is Message.Assistant -> {
                            "Assistant: ${message.content}"
                        }

                        // CRITICAL: Handle tool call requests ?
                        is Message.Tool.Call -> {
                            // The assistant previously requested a tool call
                            "Assistant: {\"tool\":\"${message.tool}\"}"
                        }

                        // CRITICAL: Handle tool results ?
                        is Message.Tool.Result -> {
                            "Tool '${message.tool}' returned: ${message.content}"
                        }

                        else -> {
                            null
                        }
                    }
                }.joinToString("\n\n")

        return buildString {
            if (systemMessage.isNotEmpty()) {
                appendLine(systemMessage)
                appendLine()
            }
            append(conversation)
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = throw Exception("Not supported")

    override fun llmProvider(): LLMProvider = object : LLMProvider("gemma", "Gemma") {}
}

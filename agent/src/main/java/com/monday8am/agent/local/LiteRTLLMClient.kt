package com.monday8am.agent.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import co.touchlab.kermit.Logger

/**
 * Bridge LLMClient between Koog's agent framework and LiteRT-LM native inference.
 *
 * This client is a thin wrapper that:
 * - Converts Koog's Prompt (list of messages) to a single string
 * - Passes it to LiteRT-LM via the promptExecutor
 * - Returns the response as Message.Assistant
 *
 * **Tool Calling:**
 * Tools are managed entirely by LiteRT-LM's native tool calling system:
 * - Tools are passed via ConversationConfig (not this client)
 * - Qwen3DataProcessor handles tool detection, parsing, and execution
 * - This client just receives the final response after all tool calls complete
 *
 * **No Custom Protocols:**
 * Unlike HermesLLMClient, ReActLLMClient, etc., this client doesn't need to:
 * - Format tool schemas in prompts
 * - Parse tool calls from responses
 * - Execute tools manually
 * - Handle multi-turn tool calling loops
 *
 * LiteRT-LM's Conversation API handles all of that automatically!
 *
 * @param promptExecutor Function that sends a prompt string to LiteRT-LM and returns the response.
 *                       This should wrap LocalInferenceEngine.prompt()
 */
internal class LiteRTLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("LiteRTLLMClient")

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        // Convert Koog's message list to a single concatenated string
        // LiteRT-LM maintains conversation state internally via Conversation API
        val promptText = buildPromptString(prompt.messages)

        logger.d { "Executing prompt with LiteRT-LM (${promptText.length} chars)" }
        logger.d { promptText }

        // Execute via LiteRT-LM
        val response = promptExecutor(promptText)
            ?: throw IllegalStateException("LiteRT-LM returned null response")

        logger.d { "LiteRT-LM response: ${response.take(100)}..." }

        // Return as Koog message
        return listOf(
            Message.Assistant(
                content = response,
                metaInfo = ResponseMetaInfo.Empty
            )
        )
    }

    /**
     * Converts Koog's message list to a simple string.
     * Format: "Role:Content\nRole:Content\n..."
     *
     * Since LiteRT-LM's Conversation maintains history internally,
     * we typically only send the latest user message here.
     */
    private fun buildPromptString(messages: List<Message>): String {
        return messages.joinToString("\n") { message ->
            when (message) {
                is Message.System -> "System:${message.content}"
                is Message.User -> "User:${message.content}"
                is Message.Assistant -> "Assistant:${message.content}"
                is Message.Tool.Call -> "ToolCall:${message.tool}(${message.content})"
                is Message.Tool.Result -> "ToolResult:${message.tool}=${message.content}"
                is Message.Reasoning -> "Reasoning:${message.content}"
            }
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ) = throw UnsupportedOperationException(
        "LiteRT-LM client does not support streaming. " +
            "Use execute() instead - LiteRT-LM's Conversation API handles async internally."
    )

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult {
        throw UnsupportedOperationException(
            "LiteRT-LM client does not support moderation. " +
                "Implement content filtering in your application layer if needed."
        )
    }

    override fun llmProvider(): LLMProvider = object : LLMProvider("litert-lm", "LiteRT-LM") {}

    override fun close() {
        // No resources to clean up - client is stateless
        // Actual LiteRT-LM resources (Engine/Conversation) are managed by LocalInferenceEngine
    }
}

package com.monday8am.agent.ollama

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.Message

/**
 * LLM client for Ollama's native tool calling protocol.
 *
 * This client is a lightweight wrapper that delegates to Ollama's native API.
 * Unlike the text-based protocols (SIMPLE, OPENAPI, SLIM, REACT), this client
 * relies on Ollama's built-in OpenAI-compatible tool calling support.
 *
 * Note: This is primarily used as a placeholder for the NATIVE tool format.
 * In practice, when using NATIVE format, the NotificationAgentImpl directly
 * uses simpleOllamaAIExecutor() which bypasses this client entirely.
 */
class OllamaLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    override val provider: LLMProvider = LLMProvider.Ollama

    /**
     * Generates text from a prompt using the Ollama executor.
     *
     * @param prompt The input prompt (messages will be concatenated)
     * @return Assistant message with generated text
     */
    override suspend fun generateText(prompt: Prompt): Message.Assistant {
        val concatenatedPrompt =
            prompt.messages.joinToString("\n") { message ->
                when (message) {
                    is Message.System -> "System: ${message.content}"
                    is Message.User -> "User: ${message.content}"
                    is Message.Assistant -> "Assistant: ${message.content}"
                }
            }

        val response = promptExecutor(concatenatedPrompt) ?: "No response"
        return Message.Assistant(content = response)
    }

    /**
     * Not supported by Ollama client.
     * @throws UnsupportedOperationException always
     */
    override suspend fun streamText(prompt: Prompt): Message.Assistant {
        throw UnsupportedOperationException("Ollama client does not support streaming")
    }

    /**
     * Not supported by Ollama client.
     * @throws UnsupportedOperationException always
     */
    override suspend fun moderate(text: String): Boolean {
        throw UnsupportedOperationException("Ollama client does not support moderation")
    }
}

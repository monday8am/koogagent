package com.monday8am.koogagent.mediapipe

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo

internal class GemmaLLMClient(
    private val promptMediaPipe: suspend (String) -> String?,
) : LLMClient {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        // 1. Build the full prompt with tool instructions
        val fullPrompt = buildFullPrompt(prompt, tools)

        println("=== GEMMA PROMPT ===")
        println(fullPrompt)
        println("===================")

        // 2. Execute inference
        val response = promptMediaPipe(fullPrompt) ?: return emptyList()

        println("=== GEMMA RESPONSE ===")
        println(response)
        println("======================")

        // 3. Parse response
        return parseResponse(response, tools)
    }

    internal fun buildToolInstructions(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) return ""

        // For Gemma 3n: Keep it SIMPLE - no parameters
        val toolsList = tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }

        return """
            Available tools:
            $toolsList
            
            To call a tool, respond ONLY with: {"tool":"toolName"}
            To skip tools and answer directly, respond with: {"tool":"none"} followed by your answer.
            
            Examples:
            User: What's the weather?
            Assistant: {"tool":"getWeather"}
            
            User: Tell me a joke
            Assistant: {"tool":"none"}
            Why did the chicken cross the road? To get to the other side!
            """.trimIndent()
    }

    internal fun buildFullPrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>
    ): String {
        // Extract system message
        val systemMessage = prompt.messages
            .filterIsInstance<Message.System>()
            .firstOrNull()?.content ?: ""

        // Build conversation history
        val conversation = buildConversationHistory(prompt.messages)

        // Build tool instructions
        val toolInstructions = buildToolInstructions(tools)

        return buildString {
            // System prompt first
            if (systemMessage.isNotEmpty()) {
                appendLine(systemMessage)
                appendLine()
            }

            // Tool instructions (if any)
            if (toolInstructions.isNotEmpty()) {
                appendLine(toolInstructions)
                appendLine()
            }

            // Conversation history
            append(conversation)
        }
    }

    internal fun buildConversationHistory(messages: List<Message>): String {
        return messages
            .filterNot { it is Message.System }
            .joinToString("\n\n") { message ->
                when (message) {
                    is Message.User -> {
                        "User: ${message.content}"
                    }

                    is Message.Assistant -> {
                        "Assistant: ${message.content}"
                    }

                    // CRITICAL: Handle tool call requests
                    is Message.Tool.Call -> {
                        // The assistant previously requested a tool call
                        "Assistant: {\"tool\":\"${message.tool}\"}"
                    }

                    // CRITICAL: Handle tool results
                    is Message.Tool.Result -> {
                        // Tool results come back as "user" messages showing what the tool returned
                        "Tool '${message.tool}' returned: ${message.content}"
                    }

                    else -> ""
                }
            }
    }

    internal fun parseResponse(
        response: String,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {

        // Look for {"tool":"X"} pattern
        val toolCallRegex = """\{\s*"tool"\s*:\s*"([^"]+)"\s*\}""".toRegex()
        val match = toolCallRegex.find(response)

        return if (match != null) {
            val toolName = match.groupValues[1]

            if (toolName == "none") {
                // Model chose not to use tools - return text response
                val cleanResponse = response
                    .replace(toolCallRegex, "")
                    .trim()

                listOf(
                    Message.Assistant(
                        content = cleanResponse,
                        metaInfo = ResponseMetaInfo.Empty
                    )
                )
            } else {
                // Model wants to call a tool
                // Validate tool exists
                val toolExists = tools.any { it.name == toolName }

                if (!toolExists) {
                    // Model hallucinated a tool name
                    println("WARNING: Model requested non-existent tool: $toolName")
                    println("Available tools: ${tools.map { it.name }}")

                    listOf(
                        Message.Assistant(
                            content = "I tried to use a tool called '$toolName' but it doesn't exist. Let me try to help you another way.",
                            metaInfo = ResponseMetaInfo.Empty
                        )
                    )
                } else {
                    // Valid tool call
                    listOf(
                        Message.Tool.Call(
                            id = java.util.UUID.randomUUID().toString(),
                            tool = toolName,
                            content = "{}", // Empty JSON - Gemma 3n can't provide args
                            metaInfo = ResponseMetaInfo.Empty
                        )
                    )
                }
            }
        } else {
            // No tool pattern found - treat as regular response
            listOf(
                Message.Assistant(
                    content = response,
                    metaInfo = ResponseMetaInfo.Empty
                )
            )
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = throw Exception("Not supported")

    override fun llmProvider(): LLMProvider = object : LLMProvider("gemma", "Gemma") {}
}
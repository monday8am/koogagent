package com.monday8am.koogagent.mediapipe

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import co.touchlab.kermit.Logger

/**
 * LLM Client implementation for Gemma models using MediaPipe for local inference.
 *
 * This implementation uses a simplified JSON protocol for tool calling since Gemma 3n
 * (1B parameters) has limited capacity for complex structured output.
 *
 * ## Protocol Limitations:
 * - **Single tool per turn**: Model can only call one tool at a time
 * - **No tool parameters**: Tool calls don't include arguments (always empty JSON "{}")
 * - **Sequential execution**: Multi-turn tool calling requires agent orchestration
 * - **Text-based protocol**: Uses regex parsing instead of structured JSON schemas
 *
 * ## When This Approach May Fail:
 * 1. **Parallel tool calls**: Model cannot request multiple tools simultaneously
 * 2. **Complex parameters**: Cannot pass structured arguments to tools
 * 3. **Ambiguous responses**: Model might mix tool syntax with conversational text
 * 4. **Protocol confusion**: Small models may struggle to follow JSON format consistently
 *
 * See [buildToolInstructions] for the protocol specification.
 */
internal class GemmaLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("GemmaLLMClient")

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.d { "Executing prompt: $prompt with tools: $tools and model: $model" }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        // 1. Build the full prompt with tool instructions
        val fullPrompt = buildFullPrompt(prompt, tools)

        logger.d { "=== GEMMA PROMPT ===" }
        logger.d { fullPrompt }
        logger.d { "===================" }

        // 2. Execute inference
        val response = promptExecutor(fullPrompt)

        if (response == null) {
            logger.w { "MediaPipe returned null response" }
            return emptyList()
        }

        logger.d { "=== GEMMA RESPONSE ===" }
        logger.d { response }
        logger.d { "=======================" }

        // 3. Parse response (with loop detection)
        val parsedMessages = parseResponse(response, tools, prompt.messages)
        logger.i { "Parsed ${parsedMessages.size} messages from response" }

        return parsedMessages
    }

    internal fun buildFullPrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
    ): String {
        // Extract system message
        val systemMessage =
            prompt.messages
                .filterIsInstance<Message.System>()
                .firstOrNull()
                ?.content ?: ""

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

    /**
     * Builds tool instructions for Gemma using a simplified JSON protocol.
     *
     * ## Protocol Design:
     * - Tool calls: `{"tool":"toolName"}` (no parameters)
     * - Skip tools: `{"tool":"none"}` followed by text response
     *
     * ## Limitations:
     * - **Cannot call multiple tools**: One tool per response
     * - **No parameters**: All tool calls have empty arguments
     * - **Model dependent**: Success rate depends on model size/quality
     *
     * @param tools List of available tools to include in instructions
     * @return Formatted tool instructions or empty string if no tools
     */
    internal fun buildToolInstructions(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) {
            logger.d { "No tools provided, skipping tool instructions" }
            return ""
        }

        logger.d { "Building tool instructions for ${tools.size} tools: ${tools.map { it.name }}" }

        // For Gemma 3n: Keep it SIMPLE - no parameters
        val toolsList =
            tools.joinToString("\n") { tool ->
                "- ${tool.name}: ${tool.description}"
            }

        return """
            Available tools:
            $toolsList

            INSTRUCTIONS:
            - To call a tool, respond ONLY with: {"tool":"ToolName"}
            - After receiving a tool result, answer the user's question using that information
            - DO NOT call the same tool repeatedly

            Examples:

            Example 1 - Using a tool:
            User: Where am I?
            Assistant: {"tool":"GetLocationTool"}
            [Tool returns: location: latitude 48.8534, longitude: 2.3488]
            Assistant: You are currently at latitude 48.8534, longitude 2.3488.

            Example 2 - Direct answer:
            User: Tell me a joke
            Assistant: Why did the chicken cross the road? To get to the other side!
            """.trimIndent()
    }

    /**
     * Builds conversation history from Koog messages into text format for Gemma.
     *
     * ## Message Type Mapping:
     * - `Message.User` → "User: content"
     * - `Message.Assistant` → "Assistant: content"
     * - `Message.Tool.Call` → "Assistant: {"tool":"X"}" (shows model requested tool)
     * - `Message.Tool.Result` → "Tool 'X' returned: result" (provides context for next turn)
     *
     * ## Why This Works:
     * Gemma sees tool calls as part of conversation history, allowing it to:
     * 1. Understand that it previously called a tool
     * 2. See the tool's result as contextual information
     * 3. Generate a final response incorporating the result
     *
     * @param messages List of Koog messages to convert
     * @return Formatted conversation history
     */
    internal fun buildConversationHistory(messages: List<Message>): String {
        val formattedMessages =
            messages
                .filterNot { it is Message.System }
                .mapNotNull { message ->
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
                            logger.v { "Including tool call in history: ${message.tool}" }
                            "Assistant: {\"tool\":\"${message.tool}\"}"
                        }

                        // CRITICAL: Handle tool results
                        is Message.Tool.Result -> {
                            // Tool results come back as "user" messages showing what the tool returned
                            logger.v { "Including tool result in history: ${message.tool} -> ${message.content}" }
                            "Tool '${message.tool}' returned: ${message.content}"
                        }

                        else -> {
                            logger.w { "Encountered unexpected message type: ${message::class.simpleName}" }
                            null
                        }
                    }
                }

        return formattedMessages.joinToString("\n\n")
    }

    /**
     * Parses Gemma's response to extract tool calls or text responses.
     *
     * ## Parsing Logic:
     * 1. Search for `{"tool":"X"}` pattern using regex
     * 2. Detect infinite loops (calling same tool repeatedly)
     * 3. If found "none" → Strip marker and return text as Assistant message
     * 4. If found valid tool → Return Tool.Call message
     * 5. If found invalid tool → Return error message as Assistant
     * 6. If no pattern → Return raw text as Assistant message
     *
     * ## Potential Failure Scenarios:
     * - **Malformed JSON**: Model writes `{tool: "X"}` or `{"tool":"X"` (missing quote/brace)
     * - **Multiple tools**: Model tries `{"tool":"X"},{"tool":"Y"}` (not supported)
     * - **Mixed output**: Model writes `Let me check {"tool":"X"} for you` (ambiguous)
     * - **Hallucinated tools**: Model invents tool names not in the available list
     * - **Case sensitivity**: Model writes `{"Tool":"X"}` or `{"TOOL":"X"}`
     * - **Infinite loops**: Model calls same tool repeatedly after receiving result
     *
     * ## Current Handling:
     * - Regex is flexible with whitespace around JSON structure
     * - Trims whitespace from extracted tool name (handles `{"tool":" toolName "}`)
     * - Finds first match only (ignores multiple tool attempts)
     * - Validates tool name against available tools
     * - Detects infinite loops and breaks them with error message
     * - Returns helpful error for hallucinated tools
     * - Falls back to treating response as regular text if no pattern found
     *
     * @param response Raw text response from Gemma
     * @param tools List of available tools for validation
     * @param conversationHistory List of messages in the conversation for loop detection
     * @return List of parsed messages (typically one message)
     */
    internal fun parseResponse(
        response: String,
        tools: List<ToolDescriptor>,
        conversationHistory: List<Message> = emptyList(),
    ): List<Message.Response> {
        // Look for {"tool":"X"} pattern
        val toolCallRegex = """\{\s*"tool"\s*:\s*"([^"]+)"\s*\}""".toRegex()
        val match = toolCallRegex.find(response)

        return if (match != null) {
            val toolName = match.groupValues[1].trim()
            logger.d { "Found tool pattern in response: tool='$toolName'" }

            // SAFETY: Detect infinite loop - model calling same tool after getting result
            val lastToolResult =
                conversationHistory
                    .filterIsInstance<Message.Tool.Result>()
                    .lastOrNull()

            if (lastToolResult != null && lastToolResult.tool == toolName) {
                logger.w { "INFINITE LOOP DETECTED: Model trying to call '$toolName' again after receiving result" }
                logger.w { "Last tool result: ${lastToolResult.content}" }
                return listOf(
                    Message.Assistant(
                        content =
                            "I have the information from $toolName: ${lastToolResult.content}. " +
                                "Let me provide you with the answer based on that.",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            }

            if (toolName == "none") {
                // Model chose not to use tools - return text response
                val cleanResponse =
                    response
                        .replace(toolCallRegex, "")
                        .trim()

                logger.i { "Model chose not to use tools, returning text response" }
                listOf(
                    Message.Assistant(
                        content = cleanResponse,
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            } else {
                // Model wants to call a tool
                // Validate tool exists
                val toolExists = tools.any { it.name == toolName }

                if (!toolExists) {
                    // Model hallucinated a tool name
                    logger.w { "Model requested non-existent tool: '$toolName'" }
                    logger.w { "Available tools: ${tools.map { it.name }}" }

                    listOf(
                        Message.Assistant(
                            content = "I tried to use a tool called '$toolName' but it doesn't exist. Let me try to help you another way.",
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                } else {
                    // Valid tool call
                    logger.i { "Model requested valid tool: '$toolName'" }
                    listOf(
                        Message.Tool.Call(
                            id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            tool = toolName,
                            content = "{}", // CRITICAL: Empty JSON - Gemma 3n can't provide args
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                }
            }
        } else {
            // No tool pattern found - treat as regular response
            logger.d { "No tool pattern found, treating as regular text response" }
            listOf(
                Message.Assistant(
                    content = response,
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = throw Exception("Not supported")

    override fun llmProvider(): LLMProvider = object : LLMProvider("gemma", "Gemma") {}
}

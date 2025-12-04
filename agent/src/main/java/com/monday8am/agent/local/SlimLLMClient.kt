package com.monday8am.agent.local

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
import java.util.UUID

/**
 * SLIM Format LLM Client implementation for Gemma models.
 *
 * This implementation is inspired by LLMWare's SLIM (Small Language Instructional Models) approach,
 * which emphasizes minimal syntax overhead and simple, structured outputs optimized for small models (1B-3B parameters).
 *
 * ## SLIM Format Philosophy:
 * - **Minimal syntax**: XML-like tags that are easy for small models to learn
 * - **Single function focus**: Each call targets one specific function
 * - **Structured outputs**: Returns simple key-value pairs instead of conversational text
 * - **Low cognitive load**: Reduces the complexity burden on small models
 *
 * ## Protocol Design:
 *
 * **Tool Call Format:**
 * ```
 * <function>ToolName</function>
 * ```
 *
 * **Tool Call with Parameters:**
 * ```
 * <function>ToolName</function>
 * <parameters>{"key": "value"}</parameters>
 * ```
 *
 * **Skip Tools (Direct Answer):**
 * ```
 * <function>none</function>
 * Regular conversational response here...
 * ```
 *
 * ## Advantages Over JSON Protocol:
 * 1. **Clearer markers**: XML-like tags are more visually distinct than JSON braces
 * 2. **Easier parsing**: Simple regex patterns for tag extraction
 * 3. **More forgiving**: Model can make small syntax errors without breaking parser
 * 4. **Lower perplexity**: Common HTML/XML patterns are well-represented in training data
 *
 * ## Limitations:
 * - **Single tool per turn**: Like other small model protocols, no parallel execution
 * - **Simple parameters**: JSON within <parameters> tags, but kept minimal
 * - **No nested structures**: Avoid complex parameter hierarchies
 *
 * ## Example Interactions:
 *
 * ```
 * User: Where am I?
 * Assistant: <function>GetLocation</function>
 *
 * [Tool returns: latitude: 48.8534, longitude: 2.3488]
 *
 * User: [Tool result provided]
 * Assistant: You are currently at latitude 48.8534, longitude 2.3488.
 * ```
 *
 * ```
 * User: What's the weather like?
 * Assistant: <function>GetWeatherFromLocation</function>
 * <parameters>{"latitude": "48.8534", "longitude": "2.3488"}</parameters>
 * ```
 *
 * @param promptExecutor Function that executes inference with the model
 */
internal class SlimLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("SlimLLMClient")
    private var isFirstTurn = true

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.d { "Executing prompt with SLIM format: tools=${tools.map { it.name }}" }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        // Build prompt based on whether it's the first turn or subsequent turn
        val promptText =
            if (isFirstTurn) {
                logger.d { "First turn: sending system + tools + initial message" }
                buildFirstTurnPrompt(prompt, tools)
            } else {
                logger.d { "Subsequent turn: sending only latest message" }
                buildSubsequentTurnPrompt(prompt.messages)
            }

        logger.d { "=== SLIM FORMAT PROMPT ===" }
        logger.d { promptText }
        logger.d { "==========================" }

        // Execute inference
        val response = promptExecutor(promptText)

        if (response == null) {
            logger.w { "LiteRT-LM returned null response" }
            return emptyList()
        }

        logger.d { "=== SLIM FORMAT RESPONSE ===" }
        logger.d { response }
        logger.d { "============================" }

        // Parse response (with loop detection)
        val parsedMessages = parseResponse(response, tools, prompt.messages)
        logger.i { "Parsed ${parsedMessages.size} messages from response" }

        // After first turn completes, subsequent calls are incremental
        isFirstTurn = false

        return parsedMessages
    }

    /**
     * Builds the first turn prompt with system message, tool instructions, and initial user message.
     * This is only sent once at the start of the conversation.
     */
    internal fun buildFirstTurnPrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
    ): String {
        // Extract system message
        val systemMessage =
            prompt.messages
                .filterIsInstance<Message.System>()
                .firstOrNull()
                ?.content ?: ""

        // Build tool instructions in SLIM format
        val toolInstructions = buildSlimToolInstructions(tools)

        // Get only the first user message (no history yet)
        val firstUserMessage =
            prompt.messages
                .filterIsInstance<Message.User>()
                .firstOrNull()
                ?.content ?: ""

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

            // First user message
            append(firstUserMessage)
        }
    }

    /**
     * Builds subsequent turn prompts with only the latest message.
     * LiteRT-LM's Conversation API maintains history, so we only send new content.
     */
    internal fun buildSubsequentTurnPrompt(messages: List<Message>): String {
        // Get the last non-system message
        val latestMessage =
            messages
                .filterNot { it is Message.System }
                .lastOrNull()

        return when (latestMessage) {
            is Message.User -> {
                latestMessage.content
            }

            is Message.Tool.Result -> {
                "Tool result: ${latestMessage.content}"
            }

            is Message.Assistant -> {
                latestMessage.content
            }

            is Message.Tool.Call -> {
                // Reconstruct SLIM format for conversation history
                if (latestMessage.content.isEmpty() || latestMessage.content == "{}") {
                    "<function>${latestMessage.tool}</function>"
                } else {
                    "<function>${latestMessage.tool}</function>\n<parameters>${latestMessage.content}</parameters>"
                }
            }

            else -> {
                logger.w { "No valid latest message found, returning empty string" }
                ""
            }
        }
    }

    /**
     * Builds tool instructions for Gemma using SLIM format (XML-like tags).
     *
     * ## SLIM Format Design Rationale:
     * - **XML-like syntax**: Familiar pattern from training data (HTML, XML documentation)
     * - **Clear boundaries**: Opening/closing tags are visually distinct
     * - **Simple to parse**: Regex-friendly structure
     * - **Low cognitive load**: Minimal syntax for small models
     *
     * ## Format Specification:
     * ```
     * <function>ToolName</function>                    # No parameters
     * <function>ToolName</function>                    # With parameters
     * <parameters>{"key": "value"}</parameters>
     * ```
     *
     * @param tools List of available tools to include in instructions
     * @return Formatted tool instructions or empty string if no tools
     */
    internal fun buildSlimToolInstructions(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) {
            logger.d { "No tools provided, skipping tool instructions" }
            return ""
        }

        logger.d { "Building SLIM tool instructions for ${tools.size} tools: ${tools.map { it.name }}" }

        // Build tools list with descriptions
        val toolsList =
            tools.joinToString("\n") { tool ->
                val paramDesc =
                    if (tool.requiredParameters.isNotEmpty()) {
                        val params = tool.requiredParameters.joinToString(", ") { p -> p.name }
                        " (parameters: $params)"
                    } else {
                        " No parameters needed!"
                    }
                "- ${tool.name}: ${tool.description}$paramDesc"
            }

        return """
            |INSTRUCTIONS FOR FUNCTION CALLING:
            |
            |1. To call a function, use this exact format:
            |   <function>FunctionName</function>
            |
            |2. If the function requires parameters, add them immediately after:
            |   <function>FunctionName</function>
            |   <parameters>{"param1": "value1", "param2": "value2"}</parameters>
            |
            |3. After receiving a tool result, answer the user's question using that information.
            |
            |4. If you don't need a function, respond normally (no tags needed).
            |
            |5. DO NOT call the same function repeatedly with the same parameters.
            |
            |6. You SHOULD NOT include any other text in the response if you call a function.
            |
            |Available functions:
            |$toolsList
            |
            |EXAMPLES:
            |
            |Example 1 - Function without parameters:
            |Where am I?
            |<function>GetLocation</function>
            |
            |Example 2 - Function with parameters:
            |What's the weather at these coordinates?
            |<function>GetWeatherFromLocation</function>
            |<parameters>{"latitude": "xxxx", "longitude": "xxxx"}</parameters>
            """.trimMargin()
    }

    /**
     * Parses Gemma's response to extract SLIM format tool calls or text responses.
     *
     * ## Parsing Logic:
     * 1. Search for `<function>X</function>` pattern using regex
     * 2. Optionally extract `<parameters>{...}</parameters>` if present
     * 3. Detect infinite loops (calling same tool repeatedly)
     * 4. Validate tool name against available tools
     * 5. Return Tool.Call message or Assistant message
     *
     * ## Supported Formats:
     * - `<function>ToolName</function>` → Tool call with no parameters
     * - `<function>ToolName</function><parameters>{"key":"value"}</parameters>` → Tool call with params
     * - `<function>none</function>` → Explicit skip (treat as text response)
     * - Plain text → Regular assistant response
     *
     * ## Error Handling:
     * - **Malformed tags**: Missing closing tag → treated as text
     * - **Invalid tool names**: Not in tools list → error message
     * - **Infinite loops**: Same tool called twice → breaks loop with answer
     * - **Empty parameters**: Treats as `{}`
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
        // Look for <function>X</function> pattern
        val functionRegex = """<function>\s*([^<]+?)\s*</function>""".toRegex()
        val functionMatch = functionRegex.find(response)

        return if (functionMatch != null) {
            val functionName = functionMatch.groupValues[1].trim()
            logger.d { "Found SLIM function tag: function='$functionName'" }

            // Check for <parameters>...</parameters> pattern
            val parametersRegex = """<parameters>\s*(\{[^}]*\})\s*</parameters>""".toRegex()
            val parametersMatch = parametersRegex.find(response)
            val parameters = parametersMatch?.groupValues?.get(1)?.trim() ?: "{}"

            logger.d { "Extracted parameters: $parameters" }

            // SAFETY: Detect infinite loop - model calling same tool after getting result
            val lastToolResult =
                conversationHistory
                    .filterIsInstance<Message.Tool.Result>()
                    .lastOrNull()

            if (lastToolResult != null && lastToolResult.tool == functionName) {
                logger.w { "INFINITE LOOP DETECTED: Model trying to call '$functionName' again after receiving result" }
                logger.w { "Last tool result: ${lastToolResult.content}" }
                return listOf(
                    Message.Assistant(
                        content =
                            "I have the information from $functionName: ${lastToolResult.content}. " +
                                "Let me provide you with the answer based on that.",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            }

            if (functionName.equals("none", ignoreCase = true)) {
                // Model chose not to use tools - return text response
                val cleanResponse =
                    response
                        .replace(functionRegex, "")
                        .replace(parametersRegex, "")
                        .trim()

                logger.i { "Model chose not to use tools, returning text response" }
                listOf(
                    Message.Assistant(
                        content = cleanResponse.ifEmpty { "I can help with that without using any tools." },
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            } else {
                // Model wants to call a tool
                // Validate tool exists
                val toolExists = tools.any { it.name == functionName }

                if (!toolExists) {
                    // Model hallucinated a tool name
                    logger.w { "Model requested non-existent function: '$functionName'" }
                    logger.w { "Available tools: ${tools.map { it.name }}" }

                    listOf(
                        Message.Assistant(
                            content = "I tried to use a function called '$functionName' but it doesn't exist. Let me try to help you another way.",
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                } else {
                    // Valid tool call
                    logger.i { "Model requested valid function: '$functionName' with parameters: $parameters" }
                    listOf(
                        Message.Tool.Call(
                            id = UUID.randomUUID().toString(),
                            tool = functionName,
                            content = parameters,
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                }
            }
        } else {
            // No function pattern found - treat as regular response
            logger.d { "No SLIM function tags found, treating as regular text response" }
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

    override fun llmProvider(): LLMProvider = object : LLMProvider("gemma-slim", "Gemma with SLIM Format") {}

    override fun close() {
        // No resources to clean up - client is stateless
    }
}

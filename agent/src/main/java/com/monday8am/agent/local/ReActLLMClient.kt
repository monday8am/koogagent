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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * ReAct (Reasoning and Acting) LLM Client implementation for Gemma models.
 *
 * This implementation is based on Google's official Gemma 2 Agentic AI cookbook:
 * https://github.com/google-gemini/gemma-cookbook/blob/main/Gemma/%5BGemma_2%5DAgentic_AI.ipynb
 *
 * ## ReAct Pattern Philosophy:
 * - **Natural language tool calling**: No rigid JSON/XML syntax - uses conversational patterns
 * - **Explicit reasoning**: Model shows its "thought process" before taking actions
 * - **Few-shot learning**: Includes complete multi-turn examples in the prompt
 * - **Forgiving parsing**: Extracts tool calls from natural language patterns
 *
 * ## Expected Model Output Format:
 *
 * **When using a tool:**
 * ```
 * Thought: I need to get the user's current location.
 * Action: I should use the tool `GetLocationTool` with input `no parameters`
 * ```
 *
 * **After receiving tool result:**
 * ```
 * Final Answer: You are currently at latitude 48.8534, longitude 2.3488 (Paris, France).
 * ```
 *
 * **When answering directly (no tool needed):**
 * ```
 * Final Answer: The sky is blue because of Rayleigh scattering of sunlight.
 * ```
 *
 * ## Advantages Over JSON/XML Protocols:
 * 1. **Lower cognitive load**: Natural language is easier for small models (1B-3B params)
 * 2. **Better few-shot learning**: Complete examples are more effective than syntax rules
 * 3. **More forgiving**: Can handle variations in phrasing and formatting
 * 4. **Training data alignment**: Conversational patterns are well-represented in training
 * 5. **Explicit reasoning**: `Thought:` step improves reliability of tool selection
 *
 * ## Limitations:
 * - **Single tool per turn**: Model can only call one tool at a time
 * - **Parameter parsing complexity**: Need to extract params from natural language
 * - **Longer prompts**: Few-shot examples increase token usage
 *
 * @param promptExecutor Function that executes inference with the model (via LiteRT-LM)
 */
internal class ReActLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("ReActLLMClient")
    private var isFirstTurn = true
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.d { "Executing ReAct prompt with ${tools.size} tools: ${tools.map { it.name }}" }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        // Build prompt based on whether it's the first turn or subsequent turn
        val promptText =
            if (isFirstTurn) {
                logger.d { "First turn: sending system + few-shot examples + tools + initial message" }
                buildFirstTurnPrompt(prompt, tools)
            } else {
                logger.d { "Subsequent turn: sending only latest message" }
                buildSubsequentTurnPrompt(prompt.messages, tools)
            }

        logger.d { "=== REACT PROMPT ===" }
        logger.d { promptText }
        logger.d { "====================" }

        // Execute inference
        val response = promptExecutor(promptText)

        if (response == null) {
            logger.w { "LiteRT-LM returned null response" }
            return emptyList()
        }

        logger.d { "=== REACT RESPONSE ===" }
        logger.d { response }
        logger.d { "======================" }

        // Parse response using ReAct pattern
        val parsedMessages = parseReActResponse(response, tools, prompt.messages)
        logger.i { "Parsed ${parsedMessages.size} messages from ReAct response" }

        // After first turn completes, subsequent calls are incremental
        isFirstTurn = false

        return parsedMessages
    }

    /**
     * Builds the first turn prompt with system message, few-shot examples, tool instructions,
     * and initial user message.
     *
     * This follows Google's cookbook approach where few-shot examples are critical for
     * teaching the model the ReAct pattern.
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

        // Build tool instructions
        val toolInstructions = buildReActToolInstructions(tools)

        // Build few-shot examples (critical for ReAct!)
        val fewShotExamples = buildFewShotExamples(tools)

        // Get only the first user message (no history yet)
        val firstUserMessage =
            prompt.messages
                .filterIsInstance<Message.User>()
                .firstOrNull()
                ?.content ?: ""

        return buildString {
            // System prompt first (if provided)
            if (systemMessage.isNotEmpty()) {
                appendLine(systemMessage)
                appendLine()
            }

            // Tool instructions with ReAct pattern explanation
            if (toolInstructions.isNotEmpty()) {
                appendLine(toolInstructions)
                appendLine()
            }

            // Few-shot examples (this is the key to ReAct!)
            if (fewShotExamples.isNotEmpty()) {
                appendLine("EXAMPLES:")
                appendLine(fewShotExamples)
                appendLine()
            }

            // First user message
            appendLine("Now, please respond to the following:")
            append("User: $firstUserMessage")
        }
    }

    /**
     * Builds subsequent turn prompts with only the latest message.
     * LiteRT-LM's Conversation API maintains history, so we only send new content.
     */
    internal fun buildSubsequentTurnPrompt(
        messages: List<Message>,
        tools: List<ToolDescriptor>,
    ): String {
        // Get the last non-system message
        val latestMessage =
            messages
                .filterNot { it is Message.System }
                .lastOrNull()

        return when (latestMessage) {
            is Message.User -> "User: ${latestMessage.content}"
            is Message.Tool.Result -> {
                // Format tool result as per ReAct pattern
                "result of ${latestMessage.tool}:\n${latestMessage.content}"
            }
            is Message.Assistant -> latestMessage.content
            is Message.Tool.Call -> {
                // This shouldn't normally appear in subsequent turns, but handle it
                "Action: I should use the tool `${latestMessage.tool}` with input `${latestMessage.content}`"
            }
            else -> {
                logger.w { "No valid latest message found, returning empty string" }
                ""
            }
        }
    }

    /**
     * Builds ReAct tool instructions for Gemma using natural language format.
     *
     * This follows Google's cookbook approach:
     * - List tools with natural language descriptions
     * - Explain the Thought/Action/Final Answer pattern
     * - No JSON schemas - keep it conversational
     */
    internal fun buildReActToolInstructions(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) {
            logger.d { "No tools provided, skipping ReAct tool instructions" }
            return ""
        }

        logger.d { "Building ReAct tool instructions for ${tools.size} tools: ${tools.map { it.name }}" }

        // Build tools list with natural language descriptions
        val toolsList =
            tools.joinToString("\n") { tool ->
                val params =
                    if (tool.requiredParameters.isNotEmpty()) {
                        tool.requiredParameters.joinToString(", ") { p -> p.name }
                    } else {
                        "no parameters"
                    }
                "* `${tool.name}($params)`: ${tool.description}"
            }

        return """
You have access to the following tools:

$toolsList

When you need to use a tool, follow this multi-step conversation pattern:

Thought: [Your reasoning about what needs to be done]
Action: I should use the tool `ToolName` with input `parameter_values`

[Wait for the tool result]

Final Answer: [Your response to the user using the tool result]

IMPORTANT RULES:
- Always show your Thought before taking an Action
- Only call ONE tool at a time
- After receiving a tool result, provide a Final Answer
- DO NOT call the same tool repeatedly with the same parameters
- If you don't need a tool, skip directly to Final Answer
        """.trimIndent()
    }

    /**
     * Builds few-shot examples showing complete ReAct interactions.
     *
     * This is critical for ReAct - the model learns the pattern from complete examples
     * rather than just syntax rules.
     *
     * Google's cookbook shows that including full multi-turn conversations is more
     * effective than prescriptive instructions.
     */
    internal fun buildFewShotExamples(tools: List<ToolDescriptor>): String {
        // Build examples based on available tools
        val examples = mutableListOf<String>()

        // Find location tool if available
        val locationTool = tools.firstOrNull { it.name.contains("Location", ignoreCase = true) }
        if (locationTool != null) {
            examples.add(
                """
Example 1 - Using a tool without parameters:
User: Where am I?
Thought: I need to get the user's current location.
Action: I should use the tool `${locationTool.name}` with input `no parameters`
[result of ${locationTool.name}: latitude: 48.8534, longitude: 2.3488]
Final Answer: You are currently at coordinates latitude 48.8534, longitude 2.3488 (Paris, France).
                """.trimIndent(),
            )
        }

        // Find weather tool if available
        val weatherTool = tools.firstOrNull { it.name.contains("Weather", ignoreCase = true) }
        if (weatherTool != null && weatherTool.requiredParameters.isNotEmpty()) {
            val paramNames = weatherTool.requiredParameters.joinToString(", ") { it.name }
            examples.add(
                """
Example 2 - Using a tool with parameters:
User: What's the weather like?
Thought: I need to get weather information for the user's location.
Action: I should use the tool `${weatherTool.name}` with input `$paramNames: "48.8534", "2.3488"`
[result of ${weatherTool.name}: temperature: 18°C, condition: Partly cloudy]
Final Answer: The current weather is 18°C and partly cloudy.
                """.trimIndent(),
            )
        }

        // Add a "no tool needed" example
        examples.add(
            """
Example 3 - Answering without tools:
User: What is 2 + 2?
Final Answer: 2 + 2 equals 4.
            """.trimIndent(),
        )

        return examples.joinToString("\n\n")
    }

    /**
     * Parses Gemma's response to extract ReAct pattern elements.
     *
     * ## Parsing Logic:
     * 1. Check for "Action:" pattern → Extract tool name and parameters
     * 2. Check for "Final Answer:" pattern → Extract final response
     * 3. If neither found → Treat as conversational response
     *
     * ## Supported Formats:
     * - `Action: I should use the tool 'ToolName' with input 'params'` → Tool call
     * - `Final Answer: text...` → Assistant response
     * - Plain text → Assistant response
     *
     * ## Parameter Parsing:
     * Extracts parameters from natural language using patterns like:
     * - `with input "param1", "param2"`
     * - `with input param1: "value1", param2: "value2"`
     * - `with input no parameters` → empty params
     */
    internal fun parseReActResponse(
        response: String,
        tools: List<ToolDescriptor>,
        conversationHistory: List<Message> = emptyList(),
    ): List<Message.Response> {
        // Look for "Action: I should use the tool `ToolName` with input ..."
        // Allow variations in phrasing
        val actionRegex = """Action:\s*I should use the tool [`']([^`']+)[`']\s*with input\s*[`']?([^`'\n]*)[`']?""".toRegex(
            RegexOption.IGNORE_CASE,
        )
        val actionMatch = actionRegex.find(response)

        if (actionMatch != null) {
            val toolName = actionMatch.groupValues[1].trim()
            val inputText = actionMatch.groupValues[2].trim()

            logger.d { "Found ReAct Action pattern: tool='$toolName', input='$inputText'" }

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
                            "Based on the information from $toolName: ${lastToolResult.content}",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            }

            // Validate tool exists
            val tool = tools.firstOrNull { it.name == toolName }

            if (tool == null) {
                // Model hallucinated a tool name
                logger.w { "Model requested non-existent tool: '$toolName'" }
                logger.w { "Available tools: ${tools.map { it.name }}" }

                return listOf(
                    Message.Assistant(
                        content = "I tried to use a tool called '$toolName' but it doesn't exist. Let me try to help you another way.",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            }

            // Parse parameters from natural language
            val parameters = parseNaturalLanguageParameters(inputText, tool)

            logger.i { "Model requested valid tool: '$toolName' with parsed parameters: $parameters" }

            return listOf(
                Message.Tool.Call(
                    id = UUID.randomUUID().toString(),
                    tool = toolName,
                    content = parameters,
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        }

        // Check for "Final Answer:" pattern
        val finalAnswerRegex = """Final Answer:\s*(.*)""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val finalAnswerMatch = finalAnswerRegex.find(response)

        if (finalAnswerMatch != null) {
            val answer = finalAnswerMatch.groupValues[1].trim()
            logger.d { "Found Final Answer pattern: '$answer'" }

            return listOf(
                Message.Assistant(
                    content = answer,
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        }

        // No ReAct pattern found - treat as regular response
        logger.d { "No ReAct pattern found, treating as regular text response" }
        return listOf(
            Message.Assistant(
                content = response,
                metaInfo = ResponseMetaInfo.Empty,
            ),
        )
    }

    /**
     * Parses natural language parameter input into JSON format.
     *
     * ## Supported Formats:
     * - `no parameters` → "{}"
     * - `"value1", "value2"` → maps to tool's required parameters in order
     * - `param1: "value1", param2: "value2"` → explicit key-value pairs
     *
     * ## Examples:
     * - Input: "no parameters" → "{}"
     * - Input: "48.8534", "2.3488" → {"latitude": "48.8534", "longitude": "2.3488"}
     * - Input: "latitude: 48.8534, longitude: 2.3488" → {"latitude": "48.8534", "longitude": "2.3488"}
     */
    internal fun parseNaturalLanguageParameters(
        inputText: String,
        tool: ToolDescriptor,
    ): String {
        // Handle "no parameters" case
        if (inputText.contains("no parameters", ignoreCase = true) ||
            inputText.isBlank()
        ) {
            return "{}"
        }

        // Try to parse explicit key-value pairs: "param1: value1, param2: value2"
        val keyValueRegex = """(\w+):\s*[`"']?([^,`"']+)[`"']?""".toRegex()
        val keyValueMatches = keyValueRegex.findAll(inputText).toList()

        if (keyValueMatches.isNotEmpty()) {
            val paramsMap =
                keyValueMatches.associate { match ->
                    val key = match.groupValues[1].trim()
                    val value = match.groupValues[2].trim()
                    key to value
                }

            return json.encodeToString(
                JsonObject.serializer(),
                JsonObject(paramsMap.mapValues { JsonPrimitive(it.value) }),
            )
        }

        // Try to parse comma-separated values: "value1", "value2"
        val valuesRegex = """[`"']([^`"']+)[`"']""".toRegex()
        val valueMatches = valuesRegex.findAll(inputText).toList()

        if (valueMatches.isNotEmpty()) {
            val values = valueMatches.map { it.groupValues[1].trim() }

            // Map values to required parameters in order
            if (values.size == tool.requiredParameters.size) {
                val paramsMap =
                    tool.requiredParameters.zip(values).associate { (param, value) ->
                        param.name to value
                    }

                return json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(paramsMap.mapValues { JsonPrimitive(it.value) }),
                )
            }
        }

        // Fallback: return empty object if we can't parse
        logger.w { "Could not parse parameters from: '$inputText', returning empty JSON" }
        return "{}"
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = throw Exception("Not supported")

    override fun llmProvider(): LLMProvider = object : LLMProvider("gemma-react", "Gemma with ReAct Pattern") {}
}

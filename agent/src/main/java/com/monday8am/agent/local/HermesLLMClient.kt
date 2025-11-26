package com.monday8am.agent.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Hermes-style LLM Client for Qwen models using XML tag delimiters.
 *
 * This implementation follows Qwen's official Hermes-style tool calling format as documented
 * in the Qwen Function Calling guide. The format uses XML tags for both tool definitions
 * and tool invocations (without ChatML markers for broader compatibility).
 *
 * ## Protocol Format:
 *
 * ### System Message Structure:
 * ```
 * You are Qwen, created by Alibaba Cloud. You are a helpful assistant.
 *
 * # Tools
 * You may call one or more functions to assist with the user query.
 *
 * You are provided with function signatures within <tools></tools> XML tags:
 * <tools>
 * [{"type": "function", "function": {"name": "...", "description": "...", "parameters": {...}}}]
 * </tools>
 *
 * For each function call, return a json object with function name and arguments
 * within <tool_call></tool_call> XML tags:
 * <tool_call>
 * {"name": <function-name>, "arguments": <args-json-object>}
 * </tool_call>
 * ```
 *
 * ### Tool Call Response Format:
 * ```
 * <tool_call>
 * {"name": "get_weather", "arguments": {"latitude": 40.7128, "longitude": -74.0060}}
 * </tool_call>
 * ```
 *
 * ### Tool Result Format (from framework to model):
 * ```
 * <tool_response>
 * {"temperature": 65, "condition": "cloudy"}
 * </tool_response>
 * ```
 *
 * ## Key Features:
 * - XML tag delimiters for clear structure (`<tools>`, `<tool_call>`, `<tool_response>`)
 * - OpenAPI-compatible function schemas (follows OpenAI spec)
 * - Support for parallel function calling (multiple `<tool_call>` tags)
 * - No ChatML markers (`<|im_start|>`, `<|im_end|>`) for broader model compatibility
 *
 * ## Important Notes from Qwen Documentation:
 * - **Model Size Matters**: 0.6B models struggle with multi-step tool calling (use 7B+ for reliable results)
 * - **No Guarantees**: "It is not guaranteed that the model generation will always follow the protocol"
 * - **Prompt Engineering**: Function calling relies on prompt structure, not specialized training
 * - **Context Window**: Qwen3-0.6B has 32K context, 8B+ has 128K context
 *
 * @see <a href="https://qwen.readthedocs.io/en/latest/">Qwen Documentation</a>
 */
class HermesLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("HermesLLMClient")
    private var isFirstTurn = true

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
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

        logger.d { "=== HERMES/QWEN PROMPT ===" }
        logger.d { promptText }
        logger.d { "===========================" }

        val response = promptExecutor(promptText)

        if (response == null) {
            logger.w { "LiteRT-LM returned null response" }
            return emptyList()
        }

        logger.d { "=== HERMES/QWEN RESPONSE ===" }
        logger.d { response }
        logger.d { "=============================" }

        val parsedMessages = parseResponse(response, tools, prompt.messages)

        // After first turn completes, subsequent calls are incremental
        isFirstTurn = false

        return parsedMessages
    }

    /**
     * Builds the first turn prompt with system message, tool schemas, and initial user message.
     * This follows Qwen's Hermes-style format with XML tags (without ChatML markers).
     */
    internal fun buildFirstTurnPrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
    ): String {
        val systemMessage =
            prompt.messages
                .filterIsInstance<Message.System>()
                .firstOrNull()
                ?.content ?: ""

        val toolSchemas = buildHermesToolSchemas(tools)

        // Get only the first user message
        val firstUserMessage =
            prompt.messages
                .filterIsInstance<Message.User>()
                .firstOrNull()
                ?.content ?: ""

        return buildString {
            if (systemMessage.isNotEmpty()) {
                appendLine(systemMessage)
                appendLine()
            }
            if (toolSchemas.isNotEmpty()) {
                appendLine(toolSchemas)
                appendLine()
            }
            append(firstUserMessage)
        }
    }

    /**
     * Builds subsequent turn prompts with only the latest message.
     * LiteRT-LM's Conversation API maintains history, so we only send new content.
     */
    internal fun buildSubsequentTurnPrompt(messages: List<Message>): String {
        val latestMessage =
            messages
                .filterNot { it is Message.System }
                .lastOrNull()

        return when (latestMessage) {
            is Message.User -> {
                latestMessage.content
            }

            is Message.Tool.Result -> {
                buildString {
                    appendLine("<tool_response>")
                    appendLine(latestMessage.content)
                    appendLine("</tool_response>")
                }
            }

            is Message.Assistant -> {
                latestMessage.content
            }

            is Message.Tool.Call -> {
                buildString {
                    appendLine("<tool_call>")
                    appendLine("{\"name\": \"${latestMessage.tool}\", \"arguments\": ${latestMessage.content}}")
                    appendLine("</tool_call>")
                }
            }

            else -> {
                logger.w { "No valid latest message found, returning empty string" }
                ""
            }
        }
    }

    /**
     * Builds Hermes-style tool schemas using XML tags and OpenAPI-compatible JSON.
     *
     * Format:
     * ```
     * # Tools
     * You may call one or more functions to assist with the user query.
     *
     * You are provided with function signatures within <tools></tools> XML tags:
     * <tools>
     * [
     *   {
     *     "type": "function",
     *     "function": {
     *       "name": "get_weather",
     *       "description": "Get the current weather...",
     *       "parameters": {
     *         "type": "object",
     *         "properties": {
     *           "latitude": {"type": "number", "description": "..."},
     *           "longitude": {"type": "number", "description": "..."}
     *         },
     *         "required": ["latitude", "longitude"]
     *       }
     *     }
     *   }
     * ]
     * </tools>
     *
     * For each function call, return a json object with function name and arguments
     * within <tool_call></tool_call> XML tags:
     * <tool_call>
     * {"name": <function-name>, "arguments": <args-json-object>}
     * </tool_call>
     * ```
     */
    internal fun buildHermesToolSchemas(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) {
            logger.d { "No tools provided, skipping Hermes schemas" }
            return ""
        }

        val toolsArray =
            tools.map { tool ->
                buildHermesToolObject(tool)
            }

        val toolsJson = prettyJson.encodeToString(JsonArray.serializer(), JsonArray(toolsArray))

        return """
            # Tools
You may call one or more functions to assist with the user query.
You are provided with function signatures within <tools></tools> XML tags:
<tools>
$toolsJson
</tools>

For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:
<tool_call>
{"name": <function-name>, "arguments": <args-json-object>}
</tool_call>
            """.trimIndent()
    }

    /**
     * Builds a Hermes-style tool object following OpenAPI schema format.
     *
     * Format:
     * ```json
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "get_weather",
     *     "description": "Get the current weather for a specific location.",
     *     "parameters": {
     *       "type": "object",
     *       "properties": {
     *         "latitude": {
     *           "type": "number",
     *           "description": "Latitude coordinate"
     *         },
     *         "longitude": {
     *           "type": "number",
     *           "description": "Longitude coordinate"
     *         }
     *       },
     *       "required": ["latitude", "longitude"]
     *     }
     *   }
     * }
     * ```
     */
    private fun buildHermesToolObject(tool: ToolDescriptor): JsonObject {
        val functionObject =
            if (tool.requiredParameters.isEmpty()) {
                // Zero-parameter function
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to
                        mapOf(
                            "type" to "object",
                            "properties" to emptyMap<String, Any>(),
                            "required" to emptyList<String>(),
                        ),
                )
            } else {
                // Function with parameters
                val properties =
                    tool.requiredParameters.associate { param ->
                        param.name to
                            mapOf(
                                "type" to mapParameterType(param.type),
                                "description" to (param.description ?: ""),
                            )
                    }

                val requiredParams = tool.requiredParameters.map { it.name }

                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to
                        mapOf(
                            "type" to "object",
                            "properties" to properties,
                            "required" to requiredParams,
                        ),
                )
            }

        // Wrap in "type": "function" structure
        val wrappedObject =
            mapOf(
                "type" to "function",
                "function" to functionObject,
            )

        return JsonObject(
            wrappedObject.mapValues { (_, value) -> toJsonElement(value) },
        )
    }

    private fun mapParameterType(type: ToolParameterType): String {
        val typeName = type::class.simpleName ?: "string"
        return when {
            typeName.contains("String", ignoreCase = true) -> "string"
            typeName.contains("Number", ignoreCase = true) -> "number"
            typeName.contains("Integer", ignoreCase = true) -> "integer"
            typeName.contains("Float", ignoreCase = true) -> "number"
            typeName.contains("Boolean", ignoreCase = true) -> "boolean"
            typeName.contains("Array", ignoreCase = true) -> "array"
            typeName.contains("List", ignoreCase = true) -> "array"
            typeName.contains("Object", ignoreCase = true) -> "object"
            typeName.contains("Enum", ignoreCase = true) -> "string"
            else -> "string"
        }
    }

    private fun toJsonElement(value: Any?): JsonElement =
        when (value) {
            is String -> {
                JsonPrimitive(value)
            }

            is Number -> {
                JsonPrimitive(value)
            }

            is Boolean -> {
                JsonPrimitive(value)
            }

            is Map<*, *> -> {
                JsonObject(
                    value
                        .mapKeys { it.key.toString() }
                        .mapValues { toJsonElement(it.value) },
                )
            }

            is List<*> -> {
                JsonArray(value.map { toJsonElement(it) })
            }

            null -> {
                JsonNull
            }

            else -> {
                JsonPrimitive(value.toString())
            }
        }

    /**
     * Parses Hermes/Qwen response to extract tool calls or text responses.
     *
     * Looks for patterns:
     * - `<tool_call>{"name": "X", "arguments": {...}}</tool_call>` - Tool invocation
     * - Plain text - Assistant response
     *
     * Supports:
     * - Multiple tool calls (parallel calling)
     * - Zero-parameter tool calls (empty arguments object)
     * - Infinite loop detection (calling same tool repeatedly)
     */
    internal fun parseResponse(
        response: String,
        tools: List<ToolDescriptor>,
        conversationHistory: List<Message> = emptyList(),
    ): List<Message.Response> {
        // Look for <tool_call>...</tool_call> patterns
        val toolCallRegex =
            """<tool_call>\s*\{[^}]*"name"\s*:\s*"([^"]+)"[^}]*"arguments"\s*:\s*(\{[^}]*\})\s*\}\s*</tool_call>"""
                .toRegex()
        val matches = toolCallRegex.findAll(response).toList()

        return if (matches.isNotEmpty()) {
            // Found one or more tool calls
            val toolCalls = mutableListOf<Message.Response>()

            for (match in matches) {
                val functionName = match.groupValues[1].trim()
                val argumentsJson = match.groupValues[2].trim()

                logger.d { "Found Hermes tool call: name='$functionName', arguments='$argumentsJson'" }

                // SAFETY: Detect infinite loop
                val lastToolResult =
                    conversationHistory
                        .filterIsInstance<Message.Tool.Result>()
                        .lastOrNull()

                if (lastToolResult != null && lastToolResult.tool == functionName) {
                    logger.w { "INFINITE LOOP DETECTED: Model trying to call '$functionName' again" }
                    return listOf(
                        Message.Assistant(
                            content =
                                "I have the information from $functionName: ${lastToolResult.content}. " +
                                    "Let me provide you with the answer based on that.",
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                }

                // Validate tool exists
                val functionExists = tools.any { it.name == functionName }
                if (!functionExists) {
                    toolCalls.add(
                        Message.Assistant(
                            content = "I tried to use a function called '$functionName' but it doesn't exist.",
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                } else {
                    // Valid function call
                    logger.i { "Model requested valid function: '$functionName' with arguments: $argumentsJson" }
                    toolCalls.add(
                        Message.Tool.Call(
                            id = UUID.randomUUID().toString(),
                            tool = functionName,
                            content = argumentsJson,
                            metaInfo = ResponseMetaInfo.Empty,
                        ),
                    )
                }
            }

            toolCalls
        } else {
            // No tool call pattern found - treat as regular response
            // Strip any remaining XML tags
            val cleanResponse =
                response
                    .replace("<tool_response>", "")
                    .replace("</tool_response>", "")
                    .trim()

            logger.d { "No Hermes tool call pattern found, treating as regular text response" }
            listOf(
                Message.Assistant(
                    content = cleanResponse,
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = throw Exception("Not supported")

    override fun llmProvider(): LLMProvider = object : LLMProvider("qwen", "Qwen") {}

    override fun close() {
        // No resources to clean up - client is stateless
    }
}

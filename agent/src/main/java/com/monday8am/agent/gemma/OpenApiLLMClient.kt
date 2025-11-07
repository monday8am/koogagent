package com.monday8am.agent.gemma

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
 * This implementation follows the OpenAPI specification format similar to LiteRT-LM's Tool.kt,
 * providing better structured tool definitions with typed parameters.
 */

internal val prettyJson = Json { prettyPrint = true }

internal class OpenApiLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("OpenApiLLMClient")
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

        logger.d { "=== GEMMA PROMPT ===" }
        logger.d { promptText }
        logger.d { "===================" }

        val response = promptExecutor(promptText)

        if (response == null) {
            logger.w { "LiteRT-LM returned null response" }
            return emptyList()
        }

        logger.d { "=== GEMMA RESPONSE ===" }
        logger.d { response }
        logger.d { "=======================" }

        val parsedMessages = parseResponse(response, tools, prompt.messages)

        // After first turn completes, subsequent calls are incremental
        isFirstTurn = false

        return parsedMessages
    }

    /**
     * Builds the first turn prompt with system message, tool schemas, and initial user message.
     * This is only sent once at the start of the conversation.
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

        val toolSchemas = buildOpenApiToolSchemas(tools)

        // Get only the first user message (no history yet)
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
        // Get the last non-system message
        val latestMessage =
            messages
                .filterNot { it is Message.System }
                .lastOrNull()

        return when (latestMessage) {
            is Message.User -> latestMessage.content
            is Message.Tool.Result -> "Result: ${latestMessage.content}"
            is Message.Assistant -> latestMessage.content
            is Message.Tool.Call -> "{\"name\":\"${latestMessage.tool}\", \"parameters\":${latestMessage.content}}"
            else -> {
                logger.w { "No valid latest message found, returning empty string" }
                ""
            }
        }
    }

    internal fun buildOpenApiToolSchemas(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) {
            logger.d { "No tools provided, skipping OpenAPI schemas" }
            return ""
        }
        val schemaObjects =
            tools.map { tool ->
                buildOpenApiSchemaObject(tool)
            }
        val schemasArray = prettyJson.encodeToString(schemaObjects)

        return """
You have access to functions:
$schemasArray

You are a tool-calling assistant. When a tool is appropriate, output ONLY a single JSON object:
{"tool_name": "...", "arguments": {...}} with valid schema. No prose, no markdown.

User: What's the temperature in Madrid?
Assistant:
{"tool_name":"get_weather","arguments":{"city":"Madrid","units":"metric"}}

You SHOULD NOT include any other text in the response if you call a function.
            """.trimIndent()
    }

    private fun buildOpenApiSchemaObject(tool: ToolDescriptor): JsonObject {
        // For zero-parameter functions, use a simplified schema
        // For functions with parameters, use full OpenAPI structure
        val schema =
            if (tool.requiredParameters.isEmpty()) {
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to emptyMap<String, Any>(), // Empty object for zero-parameter functions
                )
            } else {
                val properties =
                    tool.requiredParameters.associate { param ->
                        param.name to
                            mapOf(
                                "type" to mapParameterType(param.type),
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

        return JsonObject(
            schema.mapValues { (_, value) -> toJsonElement(value) },
        )
    }

    private fun mapParameterType(type: ToolParameterType): String {
        // ToolParameterType is a sealed class from Koog - map to OpenAPI types
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
            else -> "string" // Default to string for unknown types
        }
    }

    private fun toJsonElement(value: Any?): JsonElement =
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> ->
                JsonObject(
                    value
                        .mapKeys { it.key.toString() }
                        .mapValues { toJsonElement(it.value) },
                )
            is List<*> -> JsonArray(value.map { toJsonElement(it) })
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }

    internal fun parseResponse(
        response: String,
        tools: List<ToolDescriptor>,
        conversationHistory: List<Message> = emptyList(),
    ): List<Message.Response> {
        // Look for {"name":"X", "parameters":{...}} pattern (Gemma format)
        val functionCallRegex = """\{\s*"name"\s*:\s*"([^"]+)"\s*,\s*"parameters"\s*:\s*(\{[^}]*\})\s*\}""".toRegex()
        val match = functionCallRegex.find(response)

        return if (match != null) {
            val functionName = match.groupValues[1].trim()
            val parametersJson = match.groupValues[2].trim()

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

            val functionExists = tools.any { it.name == functionName }
            if (!functionExists) {
                listOf(
                    Message.Assistant(
                        content = "I tried to use a function called '$functionName' but it doesn't exist.",
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            } else {
                // Valid function call with parameters
                logger.i { "Model requested valid function: '$functionName' with parameters: $parametersJson" }
                listOf(
                    Message.Tool.Call(
                        id = UUID.randomUUID().toString(),
                        tool = functionName,
                        content = parametersJson, // Include typed parameters
                        metaInfo = ResponseMetaInfo.Empty,
                    ),
                )
            }
        } else {
            // No function call pattern found - treat as regular response
            logger.d { "No Gemma function call pattern found, treating as regular text response" }
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

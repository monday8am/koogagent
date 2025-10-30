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
internal class OpenApiLLMClient(
    private val promptExecutor: suspend (String) -> String?,
) : LLMClient {
    private val logger = Logger.withTag("OpenApiLLMClient")

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        logger.d { "Executing prompt with OpenAPI protocol, tools: ${tools.map { it.name }}" }
        require(model.capabilities.contains(LLMCapability.Tools)) {
            "Model ${model.id} does not support tools"
        }

        // 1. Build the full prompt with OpenAPI tool schemas
        val fullPrompt = buildFullPrompt(prompt, tools)
        val response = promptExecutor(fullPrompt)

        if (response == null) {
            logger.w { "LiteRT-LM returned null response" }
            return emptyList()
        }
        val parsedMessages = parseResponse(response, tools, prompt.messages)
        return parsedMessages
    }

    internal fun buildFullPrompt(
        prompt: Prompt,
        tools: List<ToolDescriptor>,
    ): String {
        val systemMessage =
            prompt.messages
                .filterIsInstance<Message.System>()
                .firstOrNull()
                ?.content ?: ""

        val conversation = buildConversationHistory(prompt.messages)
        val toolSchemas = buildOpenApiToolSchemas(tools)

        return buildString {
            if (systemMessage.isNotEmpty()) {
                appendLine(systemMessage)
                appendLine()
            }
            if (toolSchemas.isNotEmpty()) {
                appendLine(toolSchemas)
                appendLine()
            }
            append(conversation)
        }
    }

    internal fun buildOpenApiToolSchemas(tools: List<ToolDescriptor>): String {
        if (tools.isEmpty()) {
            logger.d { "No tools provided, skipping OpenAPI schemas" }
            return ""
        }
        val schemaObjects = tools.map { tool ->
            buildOpenApiSchemaObject(tool)
        }

        val schemasArray = Json.encodeToString(
            JsonArray.serializer(),
            JsonArray(schemaObjects)
        )

        return """
            You have access to the following functions:

            $schemasArray

            If you choose to call a function, respond with JSON in this format:
            {"name": "function_name", "parameters": {"param1": "value1", "param2": value2}}

            After receiving the function result, answer the user's question using that information.
            DO NOT call the same function repeatedly.

            Examples:

            Example 1 - Function with parameters:
            User: What's the weather at latitude 48.8534, longitude 2.3488?
            Assistant: {"name": "GetWeatherToolFromLocation", "parameters": {"latitude": 48.8534, "longitude": 2.3488}}
            [Function returns: Weather: sunny, Temperature: 22.0°C]
            Assistant: The weather is sunny with a temperature of 22°C.

            Example 2 - Function without parameters:
            User: Where am I?
            Assistant: {"name": "GetLocationTool", "parameters": {}}
            [Function returns: location: latitude 48.8534, longitude 2.3488]
            Assistant: You are at latitude 48.8534, longitude 2.3488.

            Example 3 - Direct answer (no function needed):
            User: Tell me a joke
            Assistant: Why did the chicken cross the road? To get to the other side!
            """.trimIndent()
    }

    private fun buildOpenApiSchemaObject(tool: ToolDescriptor): JsonObject {
        val properties = tool.requiredParameters.associate { param ->
            param.name to mapOf(
                "type" to mapParameterType(param.type)
            )
        }

        val requiredParams = tool.requiredParameters.map { it.name }
        val schema = mapOf(
            "name" to tool.name,
            "description" to tool.description,
            "parameters" to mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to requiredParams
            )
        )

        return JsonObject(
            schema.mapValues { (_, value) -> toJsonElement(value) }
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
            is Map<*, *> -> JsonObject(
                value.mapKeys { it.key.toString() }
                    .mapValues { toJsonElement(it.value) }
            )
            is List<*> -> JsonArray(value.map { toJsonElement(it) })
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }

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

                        is Message.Tool.Call -> {
                            "Assistant: {\"name\":\"${message.tool}\", \"parameters\":${message.content}}"
                        }

                        is Message.Tool.Result -> {
                            "Function '${message.tool}' returned: ${message.content}"
                        }

                        else -> {
                            logger.w { "Encountered unexpected message type: ${message::class.simpleName}" }
                            null
                        }
                    }
                }

        return formattedMessages.joinToString("\n\n")
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

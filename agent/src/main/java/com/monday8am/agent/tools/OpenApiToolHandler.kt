package com.monday8am.agent.tools

import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.OpenApiTool
import com.monday8am.koogagent.data.testing.ToolSpecification
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * OpenAPI-based tool handler that extends LiteRT-LM's OpenApiTool. Handles tool invocations,
 * returns mock responses, and tracks calls for validation.
 *
 * @param toolSpec OpenAPI tool specification defining the tool schema
 * @param mockResponse Predefined response to return when the tool is called
 */
class OpenApiToolHandler(
    private val toolSpec: ToolSpecification,
    private val mockResponse: String,
) : OpenApiTool(), ToolHandler {

    private val logger = Logger.withTag("OpenApiToolHandler")
    private val _calls = Collections.synchronizedList(mutableListOf<ToolCall>())

    override val calls: List<ToolCall>
        get() = synchronized(_calls) { _calls.toList() }

    override fun getToolDescriptionJsonString(): String {
        return Json.encodeToString(
            OpenApiToolSchema(
                name = toolSpec.function.name,
                description = toolSpec.function.description,
                parameters = toolSpec.function.parameters,
            )
        )
    }

    override fun execute(paramsJsonString: String): String {
        logger.d { "Tool '${toolSpec.function.name}' called with params: $paramsJsonString" }

        // Parse parameters to extract as map for logging
        val args =
            try {
                val jsonElement = Json.parseToJsonElement(paramsJsonString)
                parseJsonToMap(jsonElement)
            } catch (e: Exception) {
                logger.w(e) { "Failed to parse tool parameters: $paramsJsonString" }
                emptyMap()
            }

        // Record the tool call
        val toolCall =
            ToolCall(
                name = toolSpec.function.name,
                timestamp = System.currentTimeMillis(),
                args = args,
            )
        synchronized(_calls) { _calls.add(toolCall) }

        logger.d { "Returning mock response for '${toolSpec.function.name}': $mockResponse" }
        return mockResponse
    }

    /** Converts a JsonElement to a Map<String, Any?> for tool call logging. */
    private fun parseJsonToMap(element: JsonElement): Map<String, Any?> {
        return when {
            element is JsonObject -> {
                element.entries.associate { (key, value) -> key to parseJsonValue(value) }
            }
            else -> emptyMap()
        }
    }

    /** Converts JsonElement values to Kotlin primitives. */
    private fun parseJsonValue(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "null" -> null
                    element.content == "true" -> true
                    element.content == "false" -> false
                    element.content.toIntOrNull() != null -> element.content.toInt()
                    element.content.toLongOrNull() != null -> element.content.toLong()
                    element.content.toDoubleOrNull() != null -> element.content.toDouble()
                    else -> element.content
                }
            }
            is JsonArray -> {
                element.map { parseJsonValue(it) }
            }
            is JsonObject -> {
                parseJsonToMap(element)
            }
        }
    }
}

/**
 * Internal schema format for OpenAPI tool description. This matches the format expected by
 * LiteRT-LM.
 */
@Serializable
private data class OpenApiToolSchema(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)

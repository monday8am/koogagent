package com.monday8am.koogagent.data.testing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class TestSuiteDefinition(val tests: List<TestCaseDefinition>)

@Serializable
data class TestCaseDefinition(
    val id: String,
    val name: String,
    val description: List<String> = emptyList(),
    val queries: List<TestQueryDefinition>,
    @SerialName("system_prompt") val systemPrompt: String,
    val rules: List<ValidationRule>,
    @SerialName("parse_thinking_tags") val parseThinkingTags: Boolean = true,
)

@Serializable data class TestQueryDefinition(val text: String, val description: String? = null)

@Serializable
sealed class ValidationRule {
    /** PASS if tool calls list is empty FAIL if any tool is called */
    @Serializable @SerialName("no_tool_calls") data object NoToolCalls : ValidationRule()

    /** PASS if at least one tool call matches the name */
    @Serializable
    @SerialName("tool_match")
    data class ToolMatch(@SerialName("tool_name") val toolName: String) : ValidationRule()

    /** PASS if contains both tool calls (in any order) */
    @Serializable
    @SerialName("tool_match_all")
    data class ToolMatchAll(@SerialName("tool_names") val toolNames: List<String>) :
        ValidationRule()

    /** PASS if tool call arguments match specific values (approximate match for doubles) */
    @Serializable
    @SerialName("tool_args_match")
    data class ToolArgsMatch(
        @SerialName("tool_name") val toolName: String,
        val args: Map<String, JsonElement>, // Simple exact match or numeric approximation
    ) : ValidationRule()

    /** PASS if number of tool calls >= min */
    @Serializable
    @SerialName("tool_count_min")
    data class ToolCountMin(val min: Int, @SerialName("tool_name") val toolName: String? = null) :
        ValidationRule()

    /** PASS if response length > min */
    @Serializable
    @SerialName("response_length_min")
    data class ResponseLengthMin(val min: Int) : ValidationRule()

    /** PASS if result is not blank and no tools called */
    @Serializable @SerialName("chat_valid") data object ChatValid : ValidationRule()
}

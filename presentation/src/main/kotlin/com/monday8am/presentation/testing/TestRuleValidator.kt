package com.monday8am.presentation.testing

import com.monday8am.agent.tools.ToolTrace
import com.monday8am.koogagent.data.testing.TestCaseDefinition
import com.monday8am.koogagent.data.testing.ValidationRule
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/**
 * Converts data layer test definitions to presentation layer executable test cases.
 */
object TestRuleValidator {

    fun convert(definitions: List<TestCaseDefinition>): List<TestCase> {
        return definitions.map { def ->
            TestCase(
                name = def.name,
                description = def.description,
                queries = def.queries.map { TestQuery(it.text, it.description) },
                systemPrompt = def.systemPrompt,
                parseThinkingTags = def.parseThinkingTags,
                validator = createValidator(def.rules)
            )
        }
    }

    private fun createValidator(rules: List<ValidationRule>): (String) -> ValidationResult {
        return { result ->
            // If there are no rules, we default to passing? Or maybe fail?
            // Existing logic had specific checks. We should try to check ALL rules.
            // If any rule fails, we fail. Passing means all rules pass.

            var failure: ValidationResult.Fail? = null

            for (rule in rules) {
                val validation = validateRule(rule, result)
                if (validation is ValidationResult.Fail) {
                    failure = validation
                    break
                }
            }

            failure ?: ValidationResult.Pass("All validation rules passed")
        }
    }

    private fun validateRule(rule: ValidationRule, result: String): ValidationResult {
        return when (rule) {
            is ValidationRule.NoToolCalls -> {
                if (ToolTrace.calls.isEmpty()) {
                    ValidationResult.Pass("No tools called")
                } else {
                    ValidationResult.Fail("Unexpected tool calls: ${ToolTrace.calls.map { it.name }}")
                }
            }

            is ValidationRule.ToolMatch -> {
                if (ToolTrace.calls.any { it.name == rule.toolName }) {
                    ValidationResult.Pass("Tool '${rule.toolName}' was called")
                } else {
                    ValidationResult.Fail(
                        "Tool '${rule.toolName}' NOT called. Calls: ${ToolTrace.calls.map { it.name }}"
                    )
                }
            }

            is ValidationRule.ToolMatchAll -> {
                val callNames = ToolTrace.calls.map { it.name }
                val missing = rule.toolNames.filter { it !in callNames }
                if (missing.isEmpty()) {
                    ValidationResult.Pass("All required tools called: ${rule.toolNames}")
                } else {
                    ValidationResult.Fail("Missing tool calls: $missing. Actual: $callNames")
                }
            }

            is ValidationRule.ToolArgsMatch -> {
                val toolCall = ToolTrace.calls.find { it.name == rule.toolName }
                if (toolCall != null) {
                    val allMatch = rule.args.entries.all { entry ->
                        val expectedValue = entry.value
                        val actualValue = toolCall.args[entry.key]

                        if (expectedValue is JsonPrimitive) {
                            if (expectedValue.isString) {
                                actualValue.toString() == expectedValue.content
                            } else {
                                val expectedDouble = expectedValue.doubleOrNull
                                if (expectedDouble != null) {
                                    val actualDouble = (actualValue as? Number)?.toDouble() ?: 0.0
                                    abs(expectedDouble - actualDouble) < 0.1
                                } else {
                                    actualValue.toString() == expectedValue.content
                                }
                            }
                        } else {
                            actualValue.toString() == expectedValue.toString()
                        }
                    }

                    if (allMatch) {
                        ValidationResult.Pass("Tool '${rule.toolName}' called with correct arguments")
                    } else {
                        ValidationResult.Fail(
                            "Tool '${rule.toolName}' arguments mismatch. Expected: ${rule.args}, Actual: ${toolCall.args}"
                        )
                    }
                } else {
                    ValidationResult.Fail(
                        "Tool '${rule.toolName}' NOT called (cannot check args). Calls: ${ToolTrace.calls.map { it.name }}"
                    )
                }
            }

            is ValidationRule.ToolCountMin -> {
                val matchingCalls = if (rule.toolName != null) {
                    ToolTrace.calls.filter { it.name == rule.toolName }
                } else {
                    ToolTrace.calls
                }

                if (matchingCalls.size >= rule.min) {
                    ValidationResult.Pass("Tool call count met (min ${rule.min})")
                } else {
                    ValidationResult.Fail(
                        "Expected ${rule.min}+ tool calls" + (if (rule.toolName != null) " for ${rule.toolName}" else "") +
                                ", got: ${matchingCalls.size}."
                    )
                }
            }

            is ValidationRule.ResponseLengthMin -> {
                if (result.isNotBlank() && result.length >= rule.min) {
                    ValidationResult.Pass("Response length sufficient")
                } else {
                    ValidationResult.Fail("Response too short or empty")
                }
            }

            is ValidationRule.ChatValid -> {
                if (ToolTrace.calls.isEmpty() && result.isNotBlank()) {
                    ValidationResult.Pass("Chat response valid (no tools)")
                } else {
                    ValidationResult.Fail("Chat invalid. Tools: ${ToolTrace.calls.size}, Result len: ${result.length}")
                }
            }
        }
    }
}

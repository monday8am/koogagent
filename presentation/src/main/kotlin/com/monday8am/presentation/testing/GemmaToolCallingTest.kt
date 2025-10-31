package com.monday8am.presentation.testing

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.monday8am.agent.gemma.GemmaAgent
import com.monday8am.agent.tools.GetLocation
import com.monday8am.agent.tools.GetWeather
import com.monday8am.agent.tools.GetWeatherFromLocation
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal data class TestQuery(
    val text: String,
    val description: String? = null,
)

sealed class ValidationResult {
    data class Pass(
        val message: String,
    ) : ValidationResult()

    data class Fail(
        val message: String,
        val details: String? = null,
    ) : ValidationResult()
}

data class TestResult(
    val name: String,
    val result: List<Pair<ValidationResult, Long>> = listOf(),
    val fullLog: String = String(),
    val error: Throwable? = null,
) {
    fun toFormattedString(): String {
        val output = StringBuilder()
        output.appendLine(name)
        result.forEach { (validationResult, duration) ->
            when (validationResult) {
                is ValidationResult.Pass -> {
                    output.appendLine("✓ PASS (duration: ${duration}ms): ${validationResult.message}")
                }
                is ValidationResult.Fail -> {
                    output.appendLine("✗ FAIL (duration: ${duration}ms): ${validationResult.message}")
                    validationResult.details?.let { details ->
                        output.appendLine(details)
                    }
                }
            }
        }
        return output.toString()
    }
}

internal data class TestCase(
    val name: String,
    val description: List<String> = emptyList(),
    val tools: List<Tool<*, *>>? = null,
    val queries: List<TestQuery>,
    val systemPrompt: String,
    val validator: (result: String) -> ValidationResult,
)

internal class GemmaToolCallingTest(
    private val promptExecutor: suspend (String) -> String?,
    private val resetConversation: () -> Result<Unit>,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
) {
    private val logger = Logger.withTag("GemmaToolCallingTest")
    private val testIterations = 5

    /**
     * Common test runner that executes a test case with the standard pattern:
     * - Agent creation and tool initialization
     * - Query execution with timing
     * - Result validation
     * - Pass/fail reporting
     */
    private suspend fun runTest(testCase: TestCase): TestResult {
        val output = StringBuilder()
        output.appendLine(testCase.name)
        output.appendLine("─".repeat(testIterations))

        // Append description lines if provided
        testCase.description.forEach { line ->
            output.appendLine(line)
        }
        if (testCase.description.isNotEmpty()) {
            output.appendLine()
        }

        var passCount = 0
        var testResult = TestResult(name = testCase.name)

        for (query in testCase.queries) {
            val startTime = System.currentTimeMillis()
            val queryDisplay =
                if (query.description != null) {
                    "Query (${query.description}): ${query.text}"
                } else {
                    "Query: ${query.text}"
                }
            output.appendLine(queryDisplay)

            try {
                val agent = GemmaAgent(promptExecutor = promptExecutor, useOpenApiForTools = true)

                // Initialize with tools if provided
                testCase.tools?.let { tools ->
                    val toolRegistry =
                        ToolRegistry {
                            tools.forEach { tool(it) }
                        }
                    agent.initializeWithTools(toolRegistry)
                }

                val result =
                    agent.generateMessage(
                        systemPrompt = testCase.systemPrompt,
                        userPrompt = query.text,
                    )

                val duration = System.currentTimeMillis() - startTime
                output.appendLine("Result: $result")
                output.appendLine("Duration: ${duration}ms")

                val validationResult = testCase.validator(result)
                when (validationResult) {
                    is ValidationResult.Pass -> {
                        output.appendLine("✓ PASS: ${validationResult.message}")
                        passCount++
                    }
                    is ValidationResult.Fail -> {
                        output.appendLine("✗ FAIL: ${validationResult.message}")
                        validationResult.details?.let { details ->
                            output.appendLine(details)
                        }
                    }
                }
                testResult =
                    testResult.copy(
                        result = testResult.result + (validationResult to duration),
                    )
            } catch (e: Exception) {
                output.appendLine("✗ ERROR: ${e.message}")
                logger.e(e) { "Test failed: ${query.text}" }
            }
            output.appendLine()
        }

        // Avoid reaching max context tokens
        resetConversation()

        output.appendLine("Summary: $passCount/${testCase.queries.size} passed")
        return testResult.copy(fullLog = output.toString())
    }

    fun runAllTests(): Flow<TestResult> =
        flow {
            Logger.setMinSeverity(Severity.Debug)
            try {
                val tests =
                    listOf(
                        ::testBasicToolCall,
                        ::testNoToolNeeded,
                        ::testToolHallucination,
                        ::testWeatherTool,
                        ::testMultiTurnSequence,
                    )

                tests.forEach { test ->
                    emit(test())
                }
            } catch (e: Exception) {
                emit(TestResult(name = "Unknown", error = e))
                logger.e(e) { "Test suite failed" }
            }
        }

    private suspend fun testBasicToolCall(): TestResult =
        runTest(
            TestCase(
                name = "TEST 1: Basic Location Tool Call",
                tools = listOf(GetLocation(locationProvider)),
                queries =
                    listOf(
                        TestQuery("Where am I located?", "short query"),
                        TestQuery("What is my current location?", "explicit query"),
                        TestQuery("Can you tell me my coordinates?", "coordinate query"),
                    ),
                systemPrompt = "You are a helpful assistant that can call a function for getting user location.",
                validator = { result ->
                    // Check for Madrid coordinates from MockLocationProvider
                    val hasMadridLat = result.contains("40.4") || result.contains("40°")
                    val hasMadridLon = result.contains("3.7") || result.contains("3°") || result.contains("-3.7")
                    val hasLocationKeyword =
                        result.contains("location", ignoreCase = true) ||
                            result.contains("latitude", ignoreCase = true) ||
                            result.contains("longitude", ignoreCase = true)

                    val passed = (hasMadridLat || hasMadridLon) && hasLocationKeyword

                    if (passed) {
                        ValidationResult.Pass("Response contains Madrid coordinates")
                    } else {
                        ValidationResult.Fail(
                            message = "Missing expected coordinates",
                            details =
                                "  Expected: Madrid (40.4168, -3.7038)\n" +
                                    "  Got Madrid Lat: $hasMadridLat, Lon: $hasMadridLon, Keywords: $hasLocationKeyword",
                        )
                    }
                },
            ),
        )

    private suspend fun testNoToolNeeded(): TestResult =
        runTest(
            TestCase(
                name = "TEST 2: No Tool Needed (Normal Conversation)",
                tools = listOf(GetLocation(locationProvider)),
                queries =
                    listOf(
                        TestQuery("Hello! How are you?"),
                        TestQuery("Tell me a joke"),
                        TestQuery("What is 2 + 2?"),
                    ),
                systemPrompt = "You are a helpful assistant. Only call functions when specifically asked about location.",
                validator = { result ->
                    // Should NOT mention tools or call GetLocation
                    val inappropriateToolUse =
                        result.contains("{\"tool\"", ignoreCase = true) ||
                            result.contains("GetLocation", ignoreCase = true) ||
                            result.contains("latitude", ignoreCase = true)

                    val hasValidResponse = result.isNotBlank() && result.length > 5

                    if (!inappropriateToolUse && hasValidResponse) {
                        ValidationResult.Pass("Normal conversation without inappropriate tool use")
                    } else {
                        ValidationResult.Fail("Inappropriate tool mention or invalid response")
                    }
                },
            ),
        )

    private suspend fun testToolHallucination(): TestResult =
        runTest(
            TestCase(
                name = "TEST 3: Tool Hallucination Prevention",
                description =
                    listOf(
                        "Available tools: GetLocation ONLY",
                        "Query asks about: Weather (requires GetWeather)",
                        "Expected: Model should NOT hallucinate GetWeather",
                    ),
                tools = listOf(GetLocation(locationProvider)),
                queries = listOf(TestQuery("What's the weather like?")),
                systemPrompt = "You are a helpful assistant. ONLY call functions that are explicitly available to you.",
                validator = { result ->
                    // Check if model handled unavailable tool gracefully
                    val apologizes =
                        result.contains("can't", ignoreCase = true) ||
                            result.contains("unable", ignoreCase = true) ||
                            result.contains("don't have", ignoreCase = true) ||
                            result.contains("doesn't exist", ignoreCase = true)

                    // OR model just answered without using tools (acceptable)
                    val answeredWithoutTools = !result.contains("{\"tool\"")

                    if (apologizes || answeredWithoutTools) {
                        ValidationResult.Pass("Correctly handled unavailable tool")
                    } else {
                        ValidationResult.Fail("May have attempted to use unavailable tool")
                    }
                },
            ),
        )

    private suspend fun testWeatherTool(): TestResult =
        runTest(
            TestCase(
                name = "TEST 4: Weather Tool Execution",
                description = listOf("Note: GetWeather should use default coordinates (no parameters)"),
                tools = listOf(GetWeather(locationProvider = locationProvider, weatherProvider = weatherProvider)),
                queries = listOf(TestQuery("What's the weather?")),
                systemPrompt = "You are a helpful weather assistant. Call GetWeather to check current weather.",
                validator = { result ->
                    val hasWeatherInfo =
                        result.contains("temperature", ignoreCase = true) ||
                            result.contains("weather", ignoreCase = true) ||
                            result.contains("sunny", ignoreCase = true) ||
                            result.contains("cloudy", ignoreCase = true) ||
                            result.contains("°", ignoreCase = true)

                    if (hasWeatherInfo) {
                        ValidationResult.Pass("Weather tool executed successfully")
                    } else {
                        ValidationResult.Fail("No weather data in response")
                    }
                },
            ),
        )

    private suspend fun testMultiTurnSequence(): TestResult =
        runTest(
            TestCase(
                name = "TEST 5: Multi-Turn Tool Sequence",
                description =
                    listOf(
                        "IMPORTANT: This test demonstrates the limitation:",
                        "  Gemma CANNOT call multiple tools in one turn",
                        "  Must use separate turns for location → weather",
                    ),
                tools =
                    listOf(
                        GetLocation(locationProvider),
                        GetWeatherFromLocation(weatherProvider),
                    ),
                queries = listOf(TestQuery("What's the weather where I am?")),
                systemPrompt =
                    """
                    You are a helpful assistant.
                    To answer weather questions, you need the user's location first.
                    Call GetLocation and pass the result to GetWeatherFromLocation.
                    """.trimIndent(),
                validator = { result ->
                    // Check if it contains weather info (unlikely in one turn with current protocol)
                    val hasWeather =
                        result.contains("temperature", ignoreCase = true) ||
                            result.contains("sunny", ignoreCase = true) ||
                            result.contains("cloudy", ignoreCase = true)

                    val hasLocation =
                        result.contains("latitude", ignoreCase = true) ||
                            result.contains("longitude", ignoreCase = true)

                    when {
                        hasWeather && hasLocation -> {
                            ValidationResult.Pass(
                                "⚠️  UNEXPECTED: Both location AND weather in one turn!\n" +
                                    "    This suggests agent made multiple tool calls somehow",
                            )
                        }
                        hasWeather -> {
                            ValidationResult.Pass(
                                "Weather information retrieved\n" +
                                    "    (But we don't know how it got coordinates)",
                            )
                        }
                        hasLocation -> {
                            ValidationResult.Fail(
                                message = "⚠️  PARTIAL: Got location, but not weather",
                                details =
                                    "    This is expected with single-tool protocol\n" +
                                        "    Would need another turn to get weather",
                            )
                        }
                        else -> {
                            ValidationResult.Fail("No location or weather information")
                        }
                    }
                },
            ),
        )
}

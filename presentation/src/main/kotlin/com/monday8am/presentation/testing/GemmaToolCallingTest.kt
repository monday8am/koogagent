package com.monday8am.presentation.testing

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.monday8am.agent.core.NotificationAgent
import com.monday8am.agent.core.ToolFormat
import com.monday8am.agent.local.LocalInferenceLLMClient
import com.monday8am.agent.tools.GetLocation
import com.monday8am.agent.tools.GetWeather
import com.monday8am.agent.tools.GetWeatherFromLocation
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

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

sealed interface TestResultFrame {
    data class Tool(
        val content: String,
        val accumulator: String,
    ) : TestResultFrame

    data class Content(
        val chunk: String,
        val accumulator: String,
    ) : TestResultFrame

    data class Thinking(
        val chunk: String,
        val accumulator: String,
    ) : TestResultFrame

    data class Validation(
        val result: ValidationResult,
        val duration: Long,
        val fullContent: String,
    ) : TestResultFrame
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
    val toolFormat: ToolFormat = ToolFormat.NATIVE,
)

internal class GemmaToolCallingTest(
    private val promptExecutor: suspend (String) -> String?,
    private val streamPromptExecutor: (String) -> Flow<String>,
    private val resetConversation: () -> Result<Unit>,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
) {
    private val logger = Logger.withTag("GemmaToolCallingTest")
    private val testIterations = 5

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runTest(
        testCase: TestCase,
        asStream: Boolean,
    ): Flow<TestResultFrame> {
        val llmClient =
            LocalInferenceLLMClient(
                promptExecutor = promptExecutor,
                streamPromptExecutor = streamPromptExecutor,
            )

        return testCase.queries
            .asFlow()
            .flatMapConcat { query ->
                if (asStream) {
                    runSingleQueryStream(llmClient = llmClient, testCase = testCase, query = query)
                } else {
                    runSingleQueryExecute(llmClient = llmClient, testCase = testCase, query = query)
                }
            }.onCompletion {
                resetConversation()
            }
    }

    /**
     * Executes a single query and returns a Flow of its result frames.
     * This function is more declarative and uses a chain of Flow operators.
     */
    private fun runSingleQueryStream(
        llmClient: LLMClient,
        testCase: TestCase,
        query: TestQuery,
    ): Flow<TestResultFrame> {
        val accumulatedContent = StringBuilder()
        var isThinking = false
        var isToolCall = false
        var duration = 0L

        val prompt =
            Prompt(
                id = "test",
                messages =
                    listOf(
                        Message.System(content = testCase.systemPrompt, metaInfo = RequestMetaInfo.Empty),
                        Message.User(content = query.text, metaInfo = RequestMetaInfo.Empty),
                    ),
            )

        // Start of the declarative stream execution for one query
        return llmClient
            .executeStreaming(
                prompt = prompt,
                model = getLLMModel(),
                tools = emptyList(),
            ).mapNotNull { frame ->
                when (frame) {
                    is StreamFrame.Append -> {
                        when {
                            frame.text.contains("<think>") -> {
                                isThinking = true
                            }

                            frame.text.contains("</think>") && isThinking -> {
                                isThinking = false
                                accumulatedContent.clear()
                            }

                            frame.text.contains("<tool_call") -> {
                                isToolCall = true
                            }

                            frame.text.contains("</tool_call>") && isToolCall -> {
                                accumulatedContent.clear()
                                isToolCall = false
                            }
                        }
                        accumulatedContent.append(frame.text)
                        if (isThinking) {
                            TestResultFrame.Thinking(frame.text, accumulatedContent.toString())
                        } else if (isToolCall) {
                            TestResultFrame.Tool(frame.text, accumulatedContent.toString())
                        } else {
                            TestResultFrame.Content(frame.text, accumulatedContent.toString())
                        }
                    }

                    is StreamFrame.ToolCall -> {
                        null
                    }

                    is StreamFrame.End -> {
                        TestResultFrame.Content("", accumulator = accumulatedContent.toString())
                    }
                }
            }.onStart {
                duration = System.currentTimeMillis()
            }.onCompletion { cause ->
                if (cause == null) {
                    // Success case: The stream finished without exceptions.
                    val finalDuration = System.currentTimeMillis() - duration
                    val finalContent = accumulatedContent.toString()
                    val validationResult = testCase.validator(finalContent)
                    emit(
                        TestResultFrame.Validation(
                            result = validationResult,
                            duration = finalDuration,
                            fullContent = finalContent,
                        ),
                    )
                }
            }.catch { e ->
                // Error case: An exception occurred in the upstream flow.
                logger.e(e) { "Test failed: ${query.text}" }
                val finalDuration = System.currentTimeMillis() - duration
                emit(
                    TestResultFrame.Validation(
                        result = ValidationResult.Fail("Exception: ${e.message}"),
                        duration = finalDuration,
                        fullContent = accumulatedContent.toString(), // Emit partial content on failure
                    ),
                )
            }
    }

    private fun runSingleQueryExecute(
        llmClient: LLMClient,
        testCase: TestCase,
        query: TestQuery,
    ): Flow<TestResultFrame> {
        val prompt =
            Prompt(
                id = "test",
                messages =
                    listOf(
                        Message.System(content = testCase.systemPrompt, metaInfo = RequestMetaInfo.Empty),
                        Message.User(content = query.text, metaInfo = RequestMetaInfo.Empty),
                    ),
            )

        return flow {
            val startTime = System.currentTimeMillis()

            try {
                // Execute non-streaming call
                val result =
                    llmClient.execute(
                        prompt = prompt,
                        model = getLLMModel(),
                        tools = testCase.tools?.map { it.descriptor } ?: listOf(),
                    )

                val duration = System.currentTimeMillis() - startTime
                val content = result.joinToString { it.content }

                emit(TestResultFrame.Content(chunk = content, accumulator = content))
                emit(TestResultFrame.Content("\n", accumulator = content))

                val validationResult = testCase.validator(content)
                emit(
                    TestResultFrame.Validation(
                        result = validationResult,
                        duration = duration,
                        fullContent = content,
                    ),
                )
            } catch (e: Exception) {
                logger.e(e) { "Test failed: ${query.text}" }
                val duration = System.currentTimeMillis() - startTime
                emit(
                    TestResultFrame.Validation(
                        result = ValidationResult.Fail("Exception: ${e.message}"),
                        duration = duration,
                        fullContent = "",
                    ),
                )
            }
        }
    }

    private suspend fun runTestUsingKoogAgent(testCase: TestCase): TestResult {
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
                val agent =
                    NotificationAgent.local(
                        promptExecutor = promptExecutor,
                        modelId = "gemma3-1b-it-int4",
                        toolFormat = testCase.toolFormat,
                    )

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

    /**
     * Runs all tests using the legacy non-streaming approach.
     * Returns Flow<TestResult> for backward compatibility.
     */
    fun runAllTestsOld(): Flow<TestResult> =
        flow {
            Logger.setMinSeverity(Severity.Debug)
            try {
                val tests =
                    listOf(
                        ::testMinimalInteraction,
                        // ::testBasicToolCall,
                        // ::testNoToolNeeded,
                        // ::testToolHallucination,
                        // ::testWeatherTool,
                        // ::testMultiTurnSequence,
                        // ::testHermesFormat,
                    )

                tests.forEach { test ->
                    emit(test())
                }
            } catch (e: Exception) {
                emit(TestResult(name = "Unknown", error = e))
                logger.e(e) { "Test suite failed" }
            }
        }

    fun runAllTest(): Flow<TestResultFrame> {
        Logger.setMinSeverity(Severity.Debug)

        val regressionTestSuite =
            listOf(
                TestCase(
                    name = "TEST 0: Basic Content",
                    tools = listOf(),
                    queries = listOf(TestQuery("Hi!")),
                    systemPrompt = "You are a helpful assistant!",
                    validator = { result ->
                        // The if-expression is more concise.
                        if (result.isNotBlank() && result.length > 5) {
                            ValidationResult.Pass("Valid response")
                        } else {
                            ValidationResult.Fail("Invalid response")
                        }
                    },
                ),
                // Add more test cases as needed
            )
        return runAllTestsStreaming(regressionTestSuite)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runAllTestsStreaming(testCases: List<TestCase>): Flow<TestResultFrame> =
        testCases
            .asFlow()
            .flatMapConcat { testCase ->
                runTest(testCase, asStream = false)
            }.catch { e ->
                // This catches exceptions from the upstream flow (runTest or its processing).
                logger.e(e) { "A failure occurred during the test suite execution" }
                emit(
                    TestResultFrame.Validation(
                        result = ValidationResult.Fail("Test suite failed: ${e.message}"),
                        duration = 0,
                        fullContent = "",
                    ),
                )
            }

    private suspend fun testMinimalInteraction(): TestResult =
        runTestUsingKoogAgent(
            TestCase(
                name = "TEST 0: Basic Content",
                tools = listOf(),
                queries = listOf(TestQuery("where am I located?")),
                systemPrompt = "If I asks \"where am I?\" or similar location queries, call the get_location function to retrieve their current coordinates.",
                validator = { result ->
                    val hasValidResponse = result.isNotBlank() && result.length > 5
                    if (hasValidResponse) {
                        ValidationResult.Pass("Valid response")
                    } else {
                        ValidationResult.Fail("Invalid response")
                    }
                },
            ),
        )

    private suspend fun testBasicToolCall(): TestResult =
        runTestUsingKoogAgent(
            TestCase(
                name = "TEST 1: Basic Location Tool Call",
                tools = listOf(GetLocation(locationProvider)),
                queries =
                    listOf(
                        TestQuery("Where am I located?", "short query"),
                        TestQuery("What is my current location?", "explicit query"),
                        TestQuery("Can you tell me my coordinates?", "coordinate query"),
                    ),
                systemPrompt =
                    """
                    When to use tools
                    If the user asks "where am I?" or similar location queries, call the GetLocation function to retrieve their current coordinates.
                    """.trimIndent(),
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
        runTestUsingKoogAgent(
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
        runTestUsingKoogAgent(
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
        runTestUsingKoogAgent(
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
        runTestUsingKoogAgent(
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

    private suspend fun testHermesFormat(): TestResult =
        runTestUsingKoogAgent(
            TestCase(
                name = "TEST 6: Hermes/Qwen XML Format",
                description =
                    listOf(
                        "Tests Qwen's official Hermes-style format with XML tags",
                        "  Format: <tool_call>{\"name\":\"...\", \"arguments\":{...}}</tool_call>",
                        "  Based on qwen-prompts.md scenarios",
                    ),
                tools =
                    listOf(
                        GetLocation(locationProvider),
                        GetWeatherFromLocation(weatherProvider),
                    ),
                queries =
                    listOf(
                        TestQuery(
                            "Where am I?",
                            "selective calling - only location",
                        ),
                        TestQuery(
                            "What's the weather?",
                            "weather with default location",
                        ),
                    ),
                systemPrompt =
                    """
                    You are Qwen, created by Alibaba Cloud. You are a helpful assistant.

                    When the user asks about their location, use GetLocation.
                    When the user asks about weather, use GetWeatherFromLocation.
                    """.trimIndent(),
                validator = { result ->
                    // For Hermes format, check if we get location or weather info
                    val hasLocation =
                        result.contains("latitude", ignoreCase = true) ||
                            result.contains("longitude", ignoreCase = true) ||
                            result.contains("40.4", ignoreCase = true)

                    val hasWeather =
                        result.contains("temperature", ignoreCase = true) ||
                            result.contains("weather", ignoreCase = true) ||
                            result.contains("sunny", ignoreCase = true) ||
                            result.contains("cloudy", ignoreCase = true)

                    when {
                        hasLocation || hasWeather -> {
                            ValidationResult.Pass(
                                "Hermes format successfully returned " +
                                    (if (hasLocation) "location" else "weather") + " information",
                            )
                        }

                        else -> {
                            ValidationResult.Fail(
                                message = "No location or weather data in response",
                                details = "Expected Hermes-style tool calling to retrieve data",
                            )
                        }
                    }
                },
                toolFormat = ToolFormat.HERMES,
            ),
        )

    private fun getLLMModel(): LLModel =
        LLModel(
            provider = LLMProvider.Alibaba,
            id = "qwen3-0.6b",
            capabilities =
                listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.Schema.JSON.Standard,
                ),
            maxOutputTokens = 1024L,
            contextLength = 4096L,
        )
}

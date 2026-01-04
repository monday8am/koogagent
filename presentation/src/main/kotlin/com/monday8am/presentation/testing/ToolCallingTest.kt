package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.monday8am.agent.tools.ToolTrace
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform

/**
 * A single test query with optional description.
 */
data class TestQuery(val text: String, val description: String? = null)

/**
 * Validation result for a test case.
 */
sealed class ValidationResult {
    data class Pass(val message: String) : ValidationResult()

    data class Fail(val message: String, val details: String? = null) : ValidationResult()
}

/**
 * Streaming test result frames emitted during test execution.
 * Used by UI to show real-time progress.
 */
sealed interface TestResultFrame {
    val testName: String
    val id: String

    data class Description(override val testName: String, val description: String, val systemPrompt: String) : TestResultFrame {
        override val id: String = "$testName-description"
    }

    data class Query(override val testName: String, val query: String) : TestResultFrame {
        override val id: String = "$testName-query"
    }

    data class Tool(override val testName: String, val content: String, val accumulator: String) : TestResultFrame {
        override val id: String = "$testName-tool"
    }

    data class Content(override val testName: String, val chunk: String, val accumulator: String) : TestResultFrame {
        override val id: String = "$testName-content"
    }

    data class Thinking(override val testName: String, val chunk: String, val accumulator: String) : TestResultFrame {
        override val id: String = "$testName-thinking"
    }

    data class Validation(override val testName: String, val result: ValidationResult, val duration: Long, val fullContent: String) :
        TestResultFrame {
        override val id: String = "$testName-validation"
    }
}

/**
 * Test case definition - framework-agnostic.
 *
 * @property name Display name for the test
 * @property description Additional context shown during test
 * @property queries List of prompts to send to the model
 * @property systemPrompt System-level instructions
 * @property validator Function to validate model output
 * @property parseThinkingTags Whether to parse <think> and <tool_call> tags (default: true)
 */
data class TestCase(
    val name: String,
    val description: List<String> = emptyList(),
    val queries: List<TestQuery>,
    val systemPrompt: String,
    val validator: (result: String) -> ValidationResult,
    val parseThinkingTags: Boolean = true,
)

/**
 * Framework-agnostic test runner for LLM inference.
 *
 * Uses promptExecutor and streamPromptExecutor directly without any intermediate
 * framework layers. Tools are configured at the platform layer (LiteRT-LM/MediaPipe),
 * tests just validate output.
 *
 * @param streamPromptExecutor Executes a prompt and streams the response
 * @param resetConversation Resets conversation state between tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolCallingTest(private val streamPromptExecutor: (String) -> Flow<String>, private val resetConversation: () -> Result<Unit>,) {
    private val logger = Logger.withTag("ToolCallingTest")
    private val cancelled = MutableStateFlow(false)

    /**
     * Cancels the test run gracefully. Tests will stop after the current query completes.
     */
    fun cancel() {
        cancelled.value = true
    }

    /**
     * Runs all predefined tests and emits streaming results.
     */
    fun runAllTest(): Flow<TestResultFrame> {
        Logger.setMinSeverity(Severity.Debug)
        cancelled.value = false
        return runAllTestsStreaming(REGRESSION_TEST_SUITE)
    }

    private fun runAllTestsStreaming(testCases: List<TestCase>): Flow<TestResultFrame> =
        testCases.asFlow()
            .takeWhile { !cancelled.value }
            .flatMapConcat { testCase -> runTestCase(testCase) }
            .catch { e ->
                logger.e(e) { "A failure occurred during the test suite execution" }
                emit(
                    TestResultFrame.Validation(
                        testName = "Test Suite",
                        result = ValidationResult.Fail("Test suite failed: ${e.message}"),
                        duration = 0,
                        fullContent = "",
                    ),
                )
            }

    private fun runTestCase(testCase: TestCase): Flow<TestResultFrame> = flow {
        emit(
            TestResultFrame.Description(
                testName = testCase.name,
                description = testCase.description.joinToString("\n"),
                systemPrompt = testCase.systemPrompt,
            ),
        )

        testCase.queries.asFlow()
            .takeWhile { !cancelled.value }
            .flatMapConcat { query -> runSingleQueryStream(testCase, query) }
            .collect { emit(it) }

        resetConversation()
    }

    /**
     * Executes a single query using streaming and returns result frames.
     */
    private fun runSingleQueryStream(testCase: TestCase, query: TestQuery): Flow<TestResultFrame> = flow {
        emit(TestResultFrame.Query(testName = testCase.name, query = query.text))

        val processor = TagProcessor(testCase.name, testCase.parseThinkingTags)
        val startTime = System.currentTimeMillis()
        val prompt = "${testCase.systemPrompt}\n\n${query.text}"

        // Clear tool trace before execution
        ToolTrace.clear()

        streamPromptExecutor(prompt)
            .transform { chunk -> emit(processor.process(chunk)) }
            .onCompletion { cause ->
                if (cause == null) {
                    val duration = System.currentTimeMillis() - startTime
                    val finalContent = processor.resultContent
                    val validationResult = testCase.validator(finalContent)
                    emit(
                        TestResultFrame.Validation(
                            testName = testCase.name,
                            result = validationResult,
                            duration = duration,
                            fullContent = finalContent,
                        ),
                    )
                }
            }.catch { e ->
                logger.e(e) { "Test failed: ${query.text}" }
                val duration = System.currentTimeMillis() - startTime
                emit(
                    TestResultFrame.Validation(
                        testName = testCase.name,
                        result = ValidationResult.Fail("Exception: ${e.message}"),
                        duration = duration,
                        fullContent = processor.resultContent,
                    ),
                )
            }.collect { emit(it) }
    }

    companion object {
        val REGRESSION_TEST_SUITE =
            listOf(
                TestCase(
                    name = "TEST 0: Basic Response",
                    queries = listOf(TestQuery("Hello, how are you?")),
                    systemPrompt = "You are a helpful assistant.",
                    validator = { result ->
                        if (ToolTrace.calls.isNotEmpty()) {
                            ValidationResult.Fail("Unexpected tool call: ${ToolTrace.calls}")
                        } else if (result.isNotBlank() && result.length > 5) {
                            ValidationResult.Pass("Valid response received")
                        } else {
                            ValidationResult.Fail("Response too short or empty")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 1: Location Query",
                    description = listOf("Expects model to use location tool. 'get_location' should be called."),
                    queries = listOf(TestQuery("Where am I located?")),
                    systemPrompt = "You are a helpful assistant with access to location tools.",
                    validator = { _ ->
                        if (ToolTrace.calls.any { it.name == "get_location" }) {
                            ValidationResult.Pass("get_location tool called")
                        } else {
                            ValidationResult.Fail("get_location tool NOT called. Calls: ${ToolTrace.calls.map { it.name }}")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 2: Weather Query (Sequential)",
                    description = listOf("Expects weather check for current location. Should call get_location then get_weather."),
                    queries = listOf(TestQuery("What's the weather like here?")),
                    systemPrompt = "You are a weather assistant with access to weather tools. " +
                        "First you should call get_location to get the location, and then call get_weather with the obtained location.",
                    validator = { _ ->
                        val callNames = ToolTrace.calls.map { it.name }
                        if (callNames.contains("get_location") && callNames.contains("get_weather")) {
                            ValidationResult.Pass("get_location and get_weather tools called")
                        } else {
                            ValidationResult.Fail("Missing tool calls. Calls: $callNames")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 3: Specific Weather (One Tool)",
                    description = listOf("Expects weather check for specific coordinates."),
                    queries = listOf(TestQuery("What is the weather at lat 35.6 and lon 139.7?")),
                    systemPrompt = "You are a weather assistant.",
                    validator = { _ ->
                        val weatherCall = ToolTrace.calls.find { it.name == "get_weather" }
                        if (weatherCall != null) {
                            val lat = weatherCall.args["latitude"] as? Double ?: 0.0
                            val lon = weatherCall.args["longitude"] as? Double ?: 0.0
                            if (abs(lat - 35.6) < 0.1 && abs(lon - 139.7) < 0.1) {
                                ValidationResult.Pass("get_weather tool called with correct Tokyo coordinates")
                            } else {
                                ValidationResult.Fail("get_weather called with wrong coordinates: lat=$lat, lon=$lon")
                            }
                        } else {
                            ValidationResult.Fail("get_weather tool NOT called. Calls: ${ToolTrace.calls.map { it.name }}")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 4: No Tool Needed",
                    description = listOf("General knowledge question. No tools should be called."),
                    queries = listOf(TestQuery("What is the capital of France?")),
                    systemPrompt = "You are a helpful assistant.",
                    validator = { _ ->
                        if (ToolTrace.calls.isEmpty()) {
                            ValidationResult.Pass("No tools called")
                        } else {
                            ValidationResult.Fail("Unexpected tool calls: ${ToolTrace.calls.map { it.name }}")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 5: Chat with Tool Awareness",
                    description = listOf("Chit-chat. No tools should be called."),
                    queries = listOf(TestQuery("Tell me a short joke.")),
                    systemPrompt = "You are a funny assistant.",
                    validator = { result ->
                        if (ToolTrace.calls.isEmpty() && result.isNotBlank()) {
                            ValidationResult.Pass("Chat response received without tool usage")
                        } else {
                            ValidationResult.Fail("Failed. Calls: ${ToolTrace.calls}, Result: $result")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 6: Parallel Tool Calls",
                    description = listOf(
                        "Expects multiple get_weather calls for different cities.",
                        "Model should call get_weather at least twice.",
                    ),
                    queries = listOf(TestQuery("What's the weather in Tokyo (lat 35.6, lon 139.7) and Paris (lat 48.8, lon 2.3)?")),
                    systemPrompt = "You are a weather assistant. When asked about weather in multiple locations, " +
                        "call get_weather for each location.",
                    validator = { _ ->
                        val weatherCalls = ToolTrace.calls.filter { it.name == "get_weather" }
                        if (weatherCalls.size >= 2) {
                            val hasTokyo = weatherCalls.any {
                                val lat = it.args["latitude"] as? Double ?: 0.0
                                val lon = it.args["longitude"] as? Double ?: 0.0
                                abs(lat - 35.6) < 0.1 && abs(lon - 139.7) < 0.1
                            }
                            val hasParis = weatherCalls.any {
                                val lat = it.args["latitude"] as? Double ?: 0.0
                                val lon = it.args["longitude"] as? Double ?: 0.0
                                abs(lat - 48.8) < 0.1 && abs(lon - 2.3) < 0.1
                            }

                            when {
                                hasTokyo && hasParis -> ValidationResult.Pass("Multiple get_weather calls made with correct coordinates")
                                hasTokyo -> ValidationResult.Fail("Paris coordinates not found. Calls: ${weatherCalls.map { it.args }}")
                                hasParis -> ValidationResult.Fail("Tokyo coordinates not found. Calls: ${weatherCalls.map { it.args }}")
                                else -> ValidationResult.Fail(
                                    "Coordinates for Tokyo and Paris not found. Calls: ${weatherCalls.map { it.args }}"
                                )
                            }
                        } else {
                            ValidationResult.Fail(
                                "Expected 2+ get_weather calls, got: ${weatherCalls.size}. Calls: ${ToolTrace.calls.map { it.name }}"
                            )
                        }
                    },
                ),
            )
    }
}

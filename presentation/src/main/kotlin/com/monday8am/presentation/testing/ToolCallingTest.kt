package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * A single test query with optional description.
 */
data class TestQuery(
    val text: String,
    val description: String? = null,
)

/**
 * Validation result for a test case.
 */
sealed class ValidationResult {
    data class Pass(
        val message: String,
    ) : ValidationResult()

    data class Fail(
        val message: String,
        val details: String? = null,
    ) : ValidationResult()
}

/**
 * Streaming test result frames emitted during test execution.
 * Used by UI to show real-time progress.
 */
sealed interface TestResultFrame {
    val testName: String
    val id: String

    data class Tool(
        override val testName: String,
        val content: String,
        val accumulator: String,
    ) : TestResultFrame {
        override val id: String = "$testName-tool"
    }

    data class Content(
        override val testName: String,
        val chunk: String,
        val accumulator: String,
    ) : TestResultFrame {
        override val id: String = "$testName-content"
    }

    data class Thinking(
        override val testName: String,
        val chunk: String,
        val accumulator: String,
    ) : TestResultFrame {
        override val id: String = "$testName-thinking"
    }

    data class Validation(
        override val testName: String,
        val result: ValidationResult,
        val duration: Long,
        val fullContent: String,
    ) : TestResultFrame {
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
internal data class TestCase(
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
class ToolCallingTest(
    private val streamPromptExecutor: (String) -> Flow<String>,
    private val resetConversation: () -> Result<Unit>,
) {
    private val logger = Logger.withTag("ToolCallingTest")

    /**
     * Runs all predefined tests and emits streaming results.
     */
    fun runAllTest(): Flow<TestResultFrame> {
        Logger.setMinSeverity(Severity.Debug)
        return runAllTestsStreaming(REGRESSION_TEST_SUITE)
    }

    private fun runAllTestsStreaming(testCases: List<TestCase>): Flow<TestResultFrame> = flow {
        for (testCase in testCases) {
            for (query in testCase.queries) {
                emitAll(runSingleQueryStream(testCase = testCase, query = query))
            }
            resetConversation()
        }
    }.catch { e ->
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

    /**
     * Executes a single query using streaming and returns result frames.
     */
    private fun runSingleQueryStream(testCase: TestCase, query: TestQuery): Flow<TestResultFrame> {
        val processor = TagProcessor(testCase.name, testCase.parseThinkingTags)
        var startTime = 0L

        val prompt = "${testCase.systemPrompt}\n\n${query.text}"

        return streamPromptExecutor(prompt)
            .map { chunk ->
                processor.process(chunk)
            }.onStart {
                startTime = System.currentTimeMillis()
            }.onCompletion { cause ->
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
            }
    }

    companion object {
        private val REGRESSION_TEST_SUITE =
            listOf(
                TestCase(
                    name = "TEST 0: Basic Response",
                    queries = listOf(TestQuery("Hello, how are you?")),
                    systemPrompt = "You are a helpful assistant.",
                    validator = { result ->
                        if (result.isNotBlank() && result.length > 5) {
                            ValidationResult.Pass("Valid response received")
                        } else {
                            ValidationResult.Fail("Response too short or empty")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 1: Location Query",
                    description = listOf("Expects model to use location tool"),
                    queries = listOf(TestQuery("Where am I located?")),
                    systemPrompt = "You are a helpful assistant with access to location tools.",
                    validator = { result ->
                        val hasCoordinates =
                            result.contains("40.4") ||
                                result.contains("latitude", ignoreCase = true) ||
                                result.contains("longitude", ignoreCase = true) ||
                                result.contains("location", ignoreCase = true)
                        if (hasCoordinates) {
                            ValidationResult.Pass("Location data returned")
                        } else {
                            ValidationResult.Fail("No location data in response")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 2: Weather Query",
                    description = listOf("Expects model to use weather tool"),
                    queries = listOf(TestQuery("What's the weather like?")),
                    systemPrompt = "You are a weather assistant with access to weather tools.",
                    validator = { result ->
                        val hasWeather =
                            result.contains("weather", ignoreCase = true) ||
                                result.contains("temperature", ignoreCase = true) ||
                                result.contains("sunny", ignoreCase = true) ||
                                result.contains("cloudy", ignoreCase = true) ||
                                result.contains("degrees", ignoreCase = true)
                        if (hasWeather) {
                            ValidationResult.Pass("Weather data returned")
                        } else {
                            ValidationResult.Fail("No weather data in response")
                        }
                    },
                ),
                TestCase(
                    name = "TEST 3: Math Reasoning (raw output)",
                    description = listOf("Raw output without tag parsing"),
                    queries = listOf(TestQuery("What is 15 + 27? Think step by step.")),
                    systemPrompt = "You are a math assistant. Show your work.",
                    validator = { result ->
                        if (result.contains("42")) {
                            ValidationResult.Pass("Correct answer: 42")
                        } else {
                            ValidationResult.Fail(
                                message = "Incorrect or missing answer",
                                details = "Expected: 42",
                            )
                        }
                    },
                ),
            )
    }
}

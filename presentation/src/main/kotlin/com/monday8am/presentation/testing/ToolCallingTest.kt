package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
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

/**
 * Aggregated test result with metrics.
 */
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
                    output.appendLine("PASS (duration: ${duration}ms): ${validationResult.message}")
                }

                is ValidationResult.Fail -> {
                    output.appendLine("FAIL (duration: ${duration}ms): ${validationResult.message}")
                    validationResult.details?.let { details ->
                        output.appendLine(details)
                    }
                }
            }
        }
        return output.toString()
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
 * @param promptExecutor Executes a prompt and returns the response
 * @param streamPromptExecutor Executes a prompt and streams the response
 * @param resetConversation Resets conversation state between tests
 */
class ToolCallingTest(
    private val promptExecutor: suspend (String) -> String?,
    private val streamPromptExecutor: (String) -> Flow<String>,
    private val resetConversation: () -> Result<Unit>,
) {
    private val logger = Logger.withTag("ToolCallingTest")

    /**
     * Runs all predefined tests and emits streaming results.
     */
    fun runAllTest(): Flow<TestResultFrame> {
        Logger.setMinSeverity(Severity.Debug)

        val regressionTestSuite = listOf(
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
                    val hasCoordinates = result.contains("40.4") ||
                            result.contains("latitude", ignoreCase = true) ||
                            result.contains("longitude", ignoreCase = true) ||
                            result.contains("location", ignoreCase = true)
                    if (hasCoordinates) {
                        ValidationResult.Pass("Location data returned")
                    } else {
                        ValidationResult.Fail("No location data in response")
                    }
                },
                parseThinkingTags = true,
            ),
            TestCase(
                name = "TEST 2: Weather Query",
                description = listOf("Expects model to use weather tool"),
                queries = listOf(TestQuery("What's the weather like?")),
                systemPrompt = "You are a weather assistant with access to weather tools.",
                validator = { result ->
                    val hasWeather = result.contains("weather", ignoreCase = true) ||
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
                parseThinkingTags = true,
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
                            details = "Expected: 42"
                        )
                    }
                },
                parseThinkingTags = false, // Raw output mode
            ),
        )

        return runAllTestsStreaming(regressionTestSuite)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runAllTestsStreaming(testCases: List<TestCase>): Flow<TestResultFrame> =
        testCases
            .asFlow()
            .flatMapConcat { testCase ->
                runTest(testCase)
            }.catch { e ->
                logger.e(e) { "A failure occurred during the test suite execution" }
                emit(
                    TestResultFrame.Validation(
                        result = ValidationResult.Fail("Test suite failed: ${e.message}"),
                        duration = 0,
                        fullContent = "",
                    ),
                )
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun runTest(testCase: TestCase): Flow<TestResultFrame> =
        testCase.queries
            .asFlow()
            .flatMapConcat { query ->
                runSingleQueryStream(testCase = testCase, query = query)
            }.onCompletion {
                resetConversation()
            }

    /**
     * Executes a single query using streaming and returns result frames.
     */
    private fun runSingleQueryStream(
        testCase: TestCase,
        query: TestQuery,
    ): Flow<TestResultFrame> {
        val accumulatedContent = StringBuilder()
        var isThinking = false
        var isToolCall = false
        var duration = 0L

        // Simple string concatenation for prompt
        val prompt = "${testCase.systemPrompt}\n\n${query.text}"

        return streamPromptExecutor(prompt)
            .map { chunk ->
                processChunk(
                    chunk = chunk,
                    parseThinkingTags = testCase.parseThinkingTags,
                    accumulatedContent = accumulatedContent,
                    isThinking = isThinking,
                    isToolCall = isToolCall,
                    onThinkingStateChange = { isThinking = it },
                    onToolCallStateChange = { isToolCall = it },
                )
            }.onStart {
                duration = System.currentTimeMillis()
            }.onCompletion { cause ->
                if (cause == null) {
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
                logger.e(e) { "Test failed: ${query.text}" }
                val finalDuration = System.currentTimeMillis() - duration
                emit(
                    TestResultFrame.Validation(
                        result = ValidationResult.Fail("Exception: ${e.message}"),
                        duration = finalDuration,
                        fullContent = accumulatedContent.toString(),
                    ),
                )
            }
    }

    /**
     * Processes a streaming chunk with optional tag parsing.
     */
    private fun processChunk(
        chunk: String,
        parseThinkingTags: Boolean,
        accumulatedContent: StringBuilder,
        isThinking: Boolean,
        isToolCall: Boolean,
        onThinkingStateChange: (Boolean) -> Unit,
        onToolCallStateChange: (Boolean) -> Unit,
    ): TestResultFrame {
        accumulatedContent.append(chunk)

        // If tag parsing is disabled, return raw content
        if (!parseThinkingTags) {
            return TestResultFrame.Content(chunk, accumulatedContent.toString())
        }

        // Parse tags for thinking and tool calls
        var currentThinking = isThinking
        var currentToolCall = isToolCall

        when {
            chunk.contains("<think>") -> {
                currentThinking = true
                onThinkingStateChange(true)
            }
            chunk.contains("</think>") && currentThinking -> {
                currentThinking = false
                onThinkingStateChange(false)
                accumulatedContent.clear()
            }
            chunk.contains("<tool_call") -> {
                currentToolCall = true
                onToolCallStateChange(true)
            }
            chunk.contains("</tool_call>") && currentToolCall -> {
                currentToolCall = false
                onToolCallStateChange(false)
                accumulatedContent.clear()
            }
        }

        return when {
            currentThinking -> TestResultFrame.Thinking(chunk, accumulatedContent.toString())
            currentToolCall -> TestResultFrame.Tool(chunk, accumulatedContent.toString())
            else -> TestResultFrame.Content(chunk, accumulatedContent.toString())
        }
    }

    /**
     * Runs a single query using non-streaming execution.
     */
    private fun runSingleQueryExecute(
        testCase: TestCase,
        query: TestQuery,
    ): Flow<TestResultFrame> {
        val prompt = "${testCase.systemPrompt}\n\n${query.text}"

        return flow {
            val startTime = System.currentTimeMillis()

            try {
                val result = promptExecutor(prompt)
                    ?: throw IllegalStateException("Executor returned null")

                val duration = System.currentTimeMillis() - startTime

                emit(TestResultFrame.Content(chunk = result, accumulator = result))

                val validationResult = testCase.validator(result)
                emit(
                    TestResultFrame.Validation(
                        result = validationResult,
                        duration = duration,
                        fullContent = result,
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
}

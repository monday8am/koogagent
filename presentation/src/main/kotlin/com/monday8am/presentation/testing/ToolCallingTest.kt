package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
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

private enum class ParserState {
    Content,
    Thinking,
    ToolCall,
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

    private fun runAllTestsStreaming(testCases: List<TestCase>): Flow<TestResultFrame> =
        flow {
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
                    result = ValidationResult.Fail("Test suite failed: ${e.message}"),
                    duration = 0,
                    fullContent = "",
                ),
            )
        }

    /**
     * Executes a single query using streaming and returns result frames.
     */
    private fun runSingleQueryStream(
        testCase: TestCase,
        query: TestQuery,
    ): Flow<TestResultFrame> {
        val processor = TagProcessor(testCase.parseThinkingTags)
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
                        result = ValidationResult.Fail("Exception: ${e.message}"),
                        duration = duration,
                        fullContent = processor.resultContent,
                    ),
                )
            }
    }

    /**
     * Internal processor to handle tag-based streaming state.
     */
    private class TagProcessor(
        private val parseTags: Boolean,
    ) {
        private val currentBlock = StringBuilder()
        private var state = ParserState.Content

        /** The content accumulated for validation (after stripping tags if enabled) */
        val resultContent: String get() = currentBlock.toString()

        fun process(chunk: String): TestResultFrame {
            currentBlock.append(chunk)

            if (!parseTags) {
                return TestResultFrame.Content(chunk, currentBlock.toString())
            }

            // Simple state machine to detect tags. Using a window to handle split tokens.
            // We check the tail of currentBlock for state transitions.
            val lookBack = currentBlock.takeLast(20).toString()

            when (state) {
                ParserState.Content -> {
                    if (lookBack.contains("<think>")) {
                        state = ParserState.Thinking
                        stripTag("<think>")
                    } else if (lookBack.contains("<tool_call")) {
                        state = ParserState.ToolCall
                        stripTag("<tool_call")
                    }
                }

                ParserState.Thinking -> {
                    if (lookBack.contains("</think>")) {
                        state = ParserState.Content
                        stripTag("</think>", clearBefore = true)
                    }
                }

                ParserState.ToolCall -> {
                    if (lookBack.contains("</tool_call>")) {
                        state = ParserState.Content
                        stripTag("</tool_call>", clearBefore = true)
                    }
                }
            }

            return when (state) {
                ParserState.Thinking -> TestResultFrame.Thinking(chunk, currentBlock.toString())
                ParserState.ToolCall -> TestResultFrame.Tool(chunk, currentBlock.toString())
                ParserState.Content -> TestResultFrame.Content(chunk, currentBlock.toString())
            }
        }

        private fun stripTag(
            tag: String,
            clearBefore: Boolean = false,
        ) {
            val content = currentBlock.toString()
            val index = content.lastIndexOf(tag)
            if (index != -1) {
                if (clearBefore) {
                    val remaining = content.substring(index + tag.length)
                    currentBlock.clear()
                    currentBlock.append(remaining)
                } else {
                    // Just remove the tag itself if we're starting a new block
                    // Actually, if we're starting <think>, we might want to clear previous content
                    // if it's just whitespace or if the contract is "one block at a time".
                    // The original code cleared on END tags.
                    // For start tags, let's just keep everything for now but remove the tag.
                    currentBlock.delete(index, index + tag.length)
                }
            }
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
                    parseThinkingTags = true,
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
                                details = "Expected: 42",
                            )
                        }
                    },
                    parseThinkingTags = false, // Raw output mode
                ),
            )
    }
}

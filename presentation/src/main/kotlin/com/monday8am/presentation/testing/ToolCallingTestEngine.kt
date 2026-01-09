package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.monday8am.agent.tools.ToolTrace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Framework-agnostic test runner for LLM inference.
 *
 * Uses promptExecutor and streamPromptExecutor directly without any intermediate framework layers.
 * Tools are configured at the platform layer (LiteRT-LM/MediaPipe), tests just validate output.
 *
 * @param streamPromptExecutor Executes a prompt and streams the response
 * @param resetConversation Resets conversation state between tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolCallingTestEngine(
    private val streamPromptExecutor: (String) -> Flow<String>,
    private val resetConversation: () -> Result<Unit>,
) {
    private val logger = Logger.withTag("ToolCallingTestEngine")
    private val cancelled = MutableStateFlow(false)

    /** Cancels the test run gracefully. Tests will stop after the current query completes. */
    fun cancel() {
        cancelled.value = true
    }

    private fun checkCancellation() {
        if (cancelled.value) throw TestCancelledException()
    }

    /** Runs all provided tests and emits streaming results. */
    fun runAllTests(testCases: List<TestCase>): Flow<TestResultFrame> {
        Logger.setMinSeverity(Severity.Debug)
        cancelled.value = false
        return runAllTestsStreaming(testCases)
    }

    private fun runAllTestsStreaming(testCases: List<TestCase>): Flow<TestResultFrame> =
        testCases
            .asFlow()
            .flatMapConcat { testCase -> runTestCase(testCase) }
            .catch { e ->
                if (e is TestCancelledException) throw e
                logger.e(e) { "A failure occurred during the test suite execution" }
                emit(
                    TestResultFrame.Validation(
                        testName = "Test Suite",
                        result = ValidationResult.Fail("Test suite failed: ${e.message}"),
                        duration = 0,
                        fullContent = "",
                    )
                )
            }

    private fun runTestCase(testCase: TestCase): Flow<TestResultFrame> = flow {
        emit(
            TestResultFrame.Description(
                testName = testCase.name,
                description = testCase.description.joinToString("\n"),
                systemPrompt = testCase.systemPrompt,
            )
        )

        testCase.queries
            .asFlow()
            .flatMapConcat { query -> runSingleQueryStream(testCase, query) }
            .collect { emit(it) }

        resetConversation()
    }

    /** Executes a single query using streaming and returns result frames. */
    private fun runSingleQueryStream(testCase: TestCase, query: TestQuery): Flow<TestResultFrame> =
        flow {
            emit(TestResultFrame.Query(testName = testCase.name, query = query.text))

            val processor = TagProcessor(testCase.name, testCase.parseThinkingTags)
            val startTime = System.currentTimeMillis()
            val prompt = "${testCase.systemPrompt}\n\n${query.text}"

            // Clear tool trace before execution
            ToolTrace.clear()

            streamPromptExecutor(prompt)
                .onEach { checkCancellation() }
                .map { chunk -> processor.process(chunk) }
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
                            )
                        )
                    }
                }
                .catch { e ->
                    if (e is TestCancelledException) throw e
                    logger.e(e) { "Test failed: ${query.text}" }
                    val duration = System.currentTimeMillis() - startTime
                    emit(
                        TestResultFrame.Validation(
                            testName = testCase.name,
                            result = ValidationResult.Fail("Exception: ${e.message}"),
                            duration = duration,
                            fullContent = processor.resultContent,
                        )
                    )
                }
                .collect { emit(it) }
        }
}

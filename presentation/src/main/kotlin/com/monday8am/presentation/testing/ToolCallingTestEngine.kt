package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.monday8am.agent.tools.ToolHandler
import com.monday8am.agent.tools.ToolHandlerFactory
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
 * Uses OpenAPI-based tools configured per test, with mock responses. Each test creates its own tool
 * handlers and resets the conversation with those tools.
 *
 * @param streamPromptExecutor Executes a prompt and streams the response
 * @param setToolsAndReset Sets tools and resets conversation for each test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolCallingTestEngine(
    private val streamPromptExecutor: (String) -> Flow<String>,
    private val setToolsAndReset: (tools: List<Any>) -> Result<Unit>,
) {
    private val logger = Logger.withTag("ToolCallingTestEngine")
    private val cancelled = MutableStateFlow(false)

    fun cancel() {
        cancelled.value = true
    }

    private fun checkCancellation() {
        if (cancelled.value) throw TestCancelledException()
    }

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

        val toolHandlers =
            testCase.toolDefinitions.map { toolDef ->
                ToolHandlerFactory.createOpenApiHandler(
                    toolSpec = toolDef,
                    mockResponse = testCase.mockToolResponses[toolDef.function.name] ?: "",
                )
            }

        // Set tools and reset conversation for this test
        setToolsAndReset(toolHandlers).getOrThrow()

        // Execute query with tool tracking
        runSingleQueryStream(testCase, testCase.query, toolHandlers).collect { emit(it) }
    }

    /** Executes a single query using streaming and returns result frames. */
    private fun runSingleQueryStream(
        testCase: TestCase,
        query: TestQuery,
        toolHandlers: List<ToolHandler>,
    ): Flow<TestResultFrame> = flow {
        emit(TestResultFrame.Query(testName = testCase.name, query = query.text))

        val processor = TagProcessor(testCase.name, testCase.parseThinkingTags)
        val startTime = System.currentTimeMillis()
        val prompt = "${testCase.systemPrompt}\n\n${query.text}"

        streamPromptExecutor(prompt)
            .onEach { checkCancellation() }
            .map { chunk -> processor.process(chunk) }
            .onCompletion { cause ->
                if (cause == null) {
                    val duration = System.currentTimeMillis() - startTime
                    val finalContent = processor.resultContent
                    // Collect all tool calls from handlers
                    val allToolCalls = toolHandlers.flatMap { it.calls }
                    val validationResult = testCase.validator(finalContent, allToolCalls)
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

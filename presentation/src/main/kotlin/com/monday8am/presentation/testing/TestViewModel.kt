package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

data class TestUiState(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val selectedModel: ModelConfiguration,
    val isRunning: Boolean = false,
    val isInitializing: Boolean = false,
    val testStatuses: ImmutableList<TestStatus> = persistentListOf(),
)

data class TestStatus(val name: String, val state: State) {
    enum class State {
        IDLE,
        RUNNING,
        PASS,
        FAIL,
    }
}

sealed class TestUiAction {
    data class RunTests(val useGpu: Boolean) : TestUiAction()
    data object CancelTests : TestUiAction()
}

interface TestViewModel {
    val uiState: Flow<TestUiState>

    fun onUiAction(uiAction: TestUiAction)

    fun dispose()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelImpl(
    initialModel: ModelConfiguration,
    private val modelPath: String,
    private val inferenceEngine: LocalInferenceEngine,
) : TestViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentTest: ToolCallingTest? = null

    // Action trigger flow (extraBufferCapacity allows tryEmit to succeed without suspension)
    private val runTestsTrigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    // Single source of truth: ExecutionState derived from trigger
    private val executionState: StateFlow<ExecutionState> = runTestsTrigger
        .flatMapLatest { useGpu -> executeTests(useGpu) }
        .stateIn(scope, SharingStarted.Eagerly, ExecutionState.Idle(initialModel))

    // UI state derived from ExecutionState
    override val uiState: StateFlow<TestUiState> = executionState
        .map { state -> deriveUiState(state) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = deriveUiState(ExecutionState.Idle(initialModel))
        )

    override fun onUiAction(uiAction: TestUiAction) {
        when (uiAction) {
            is TestUiAction.RunTests -> runTestsTrigger.tryEmit(uiAction.useGpu)
            TestUiAction.CancelTests -> currentTest?.cancel()
        }
    }

    /**
     * Pure flow transformation: triggers → ExecutionState emissions.
     * Handles backend configuration, engine initialization, and test execution.
     */
    private fun executeTests(useGpu: Boolean): Flow<ExecutionState> = flow {
        val modelConfig = prepareModelConfig(useGpu)
        emit(ExecutionState.Initializing(modelConfig))

        val initialResults = TestResults(
            statuses = ToolCallingTest.REGRESSION_TEST_SUITE.map {
                TestStatus(it.name, TestStatus.State.IDLE)
            }.toImmutableList()
        )

        inferenceEngine.initializeAsFlow(modelConfig, modelPath)
            .flatMapConcat { engine ->
                ToolCallingTest(
                    streamPromptExecutor = engine::promptStreaming,
                    resetConversation = engine::resetConversation,
                ).also { currentTest = it }.runAllTest()
            }
            .runningFold(initialResults) { current, frame ->
                TestResults(
                    frames = (current.frames + (frame.id to frame)).toImmutableMap(),
                    statuses = updateTestStatuses(current.statuses, frame).toImmutableList()
                )
            }
            .map { results -> ExecutionState.Running(modelConfig, results) }
            .onStart { emit(ExecutionState.Running(modelConfig, initialResults)) }
            .catch { e ->
                Logger.e("Error: ${e.message}")
                val errorFrame = TestResultFrame.Validation(
                    testName = "Error",
                    result = ValidationResult.Fail("Error: ${e.message}"),
                    duration = 0,
                    fullContent = "",
                )
                val errorResults = TestResults(
                    frames = persistentMapOf(errorFrame.id to errorFrame),
                    statuses = initialResults.statuses
                )
                emit(ExecutionState.Running(modelConfig, errorResults))
            }
            .collect { emit(it) }

        // Emit idle state when tests complete
        emit(ExecutionState.Idle(modelConfig))
    }

    /**
     * Prepares model configuration with backend selection.
     * Side effect: closes engine session if backend changes.
     */
    private fun prepareModelConfig(useGpu: Boolean): ModelConfiguration {
        val currentModel = executionState.value.model
        val newBackend = if (useGpu) HardwareBackend.GPU_SUPPORTED else HardwareBackend.CPU_ONLY

        if (currentModel.hardwareAcceleration != newBackend) {
            inferenceEngine.closeSession()
        }

        return currentModel.copy(hardwareAcceleration = newBackend)
    }

    /**
     * Pure function: derives UI state from execution state.
     */
    private fun deriveUiState(state: ExecutionState): TestUiState = when (state) {
        is ExecutionState.Idle -> TestUiState(
            selectedModel = state.model,
            isRunning = false,
            isInitializing = false,
            frames = persistentMapOf(),
            testStatuses = ToolCallingTest.REGRESSION_TEST_SUITE.map {
                TestStatus(it.name, TestStatus.State.IDLE)
            }.toImmutableList()
        )
        is ExecutionState.Initializing -> TestUiState(
            selectedModel = state.model,
            isRunning = true,
            isInitializing = true,
            frames = persistentMapOf(),
            testStatuses = ToolCallingTest.REGRESSION_TEST_SUITE.map {
                TestStatus(it.name, TestStatus.State.IDLE)
            }.toImmutableList()
        )
        is ExecutionState.Running -> TestUiState(
            selectedModel = state.model,
            isRunning = true,
            isInitializing = false,
            frames = state.results.frames,
            testStatuses = state.results.statuses
        )
    }

    private fun updateTestStatuses(
        currentStatuses: List<TestStatus>,
        frame: TestResultFrame,
    ): List<TestStatus> {
        return when (frame) {
            is TestResultFrame.Description -> {
                currentStatuses.map {
                    if (it.name == frame.testName) it.copy(state = TestStatus.State.RUNNING) else it
                }
            }

            is TestResultFrame.Validation -> {
                currentStatuses.map {
                    if (it.name == frame.testName) {
                        val state = if (frame.result is ValidationResult.Pass) {
                            TestStatus.State.PASS
                        } else {
                            TestStatus.State.FAIL
                        }
                        it.copy(state = state)
                    } else {
                        it
                    }
                }
            }

            else -> currentStatuses
        }
    }

    override fun dispose() {
        inferenceEngine.closeSession()
        scope.cancel()
    }
}

/**
 * Represents the complete execution state of the test runner.
 * Single source of truth for the reactive pipeline.
 */
private sealed class ExecutionState {
    abstract val model: ModelConfiguration

    data class Idle(override val model: ModelConfiguration) : ExecutionState()
    data class Initializing(override val model: ModelConfiguration) : ExecutionState()
    data class Running(override val model: ModelConfiguration, val results: TestResults) : ExecutionState()
}

private data class TestResults(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val statuses: ImmutableList<TestStatus> = ToolCallingTest.REGRESSION_TEST_SUITE.map {
        TestStatus(it.name, TestStatus.State.IDLE)
    }.toImmutableList()
)

package com.monday8am.presentation.testing

import co.touchlab.kermit.Logger
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.testing.AssetsTestRepository
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
import kotlinx.coroutines.flow.combine
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
    private val testRepository = AssetsTestRepository()

    private var currentTestEngine: ToolCallingTestEngine? = null
    private val runTestsTrigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    // Load tests on init
    private val loadedTests: StateFlow<List<TestCase>> = flow {
        try {
            val definitions = testRepository.getTests()
            emit(TestRuleValidator.convert(definitions))
        } catch (e: Exception) {
            Logger.e("Failed to load tests", e)
            emit(emptyList())
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val executionState: StateFlow<ExecutionState> =
        runTestsTrigger
            .flatMapLatest { useGpu -> executeTests(useGpu, loadedTests.value) }
            .stateIn(scope, SharingStarted.Eagerly, ExecutionState.Idle(initialModel))

    // UI state derived from ExecutionState and Loaded Tests
    override val uiState: StateFlow<TestUiState> =
        combine(executionState, loadedTests) { execState, tests ->
            deriveUiState(execState, tests)
        }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = deriveUiState(ExecutionState.Idle(initialModel), emptyList()),
            )

    override fun onUiAction(uiAction: TestUiAction) {
        when (uiAction) {
            is TestUiAction.RunTests -> runTestsTrigger.tryEmit(uiAction.useGpu)
            TestUiAction.CancelTests -> currentTestEngine?.cancel()
        }
    }

    /**
     * Pure flow transformation: triggers → ExecutionState emissions. Handles backend configuration,
     * engine initialization, and test execution.
     */
    private fun executeTests(useGpu: Boolean, testCases: List<TestCase>): Flow<ExecutionState> = flow {
        if (testCases.isEmpty()) {
            Logger.w("No tests loaded to run")
            return@flow
        }

        val modelConfig = prepareModelConfig(useGpu)
        val initialResults =
            TestResults(
                statuses = testCases.map {
                    TestStatus(it.name, TestStatus.State.IDLE)
                }
                    .toImmutableList()
            )

        inferenceEngine
            .initializeAsFlow(modelConfig, modelPath)
            .onStart { emit(ExecutionState.Initializing(modelConfig)) }
            .flatMapConcat { engine ->
                ToolCallingTestEngine(
                    streamPromptExecutor = engine::promptStreaming,
                    resetConversation = engine::resetConversation,
                )
                    .also { currentTestEngine = it }
                    .runAllTests(testCases)
            }
            .runningFold(initialResults) { current, frame ->
                TestResults(
                    frames = (current.frames + (frame.id to frame)).toImmutableMap(),
                    statuses = updateTestStatuses(current.statuses, frame).toImmutableList(),
                )
            }
            .map { results ->
                if (
                    results.statuses.last().state in
                    listOf(TestStatus.State.FAIL, TestStatus.State.PASS)
                ) {
                    ExecutionState.Finish(modelConfig, results)
                } else {
                    ExecutionState.Running(modelConfig, results)
                }
            }
            .catch { e ->
                if (e is TestCancelledException) {
                    emit(ExecutionState.Idle(modelConfig))
                } else {
                    Logger.e("Error: ${e.message}")
                    val errorFrame =
                        TestResultFrame.Validation(
                            testName = "Error",
                            result = ValidationResult.Fail("Error: ${e.message}"),
                            duration = 0,
                            fullContent = "",
                        )
                    val errorResults =
                        TestResults(
                            frames = persistentMapOf(errorFrame.id to errorFrame),
                            statuses = initialResults.statuses,
                        )
                    emit(ExecutionState.Finish(modelConfig, errorResults))
                }
            }
            .collect { emit(it) }
    }

    private fun prepareModelConfig(useGpu: Boolean): ModelConfiguration {
        val currentModel = executionState.value.model
        val newBackend = if (useGpu) HardwareBackend.GPU_SUPPORTED else HardwareBackend.CPU_ONLY

        if (currentModel.hardwareAcceleration != newBackend) {
            inferenceEngine.closeSession()
        }

        return currentModel.copy(hardwareAcceleration = newBackend)
    }

    /** Pure function: derives UI state from execution state. */
    private fun deriveUiState(state: ExecutionState, tests: List<TestCase>): TestUiState {
        // If we are IDLE or INITIALIZING, but have loaded tests, we should show them as IDLE statuses
        val defaultStatuses = tests.map { TestStatus(it.name, TestStatus.State.IDLE) }.toImmutableList()

        return when (state) {
            is ExecutionState.Idle ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = false,
                    isInitializing = false,
                    frames = persistentMapOf(),
                    testStatuses = defaultStatuses,
                )

            is ExecutionState.Initializing ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = true,
                    isInitializing = true,
                    frames = persistentMapOf(),
                    testStatuses = defaultStatuses,
                )

            is ExecutionState.Running ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = true,
                    isInitializing = false,
                    frames = state.results.frames,
                    testStatuses = state.results.statuses.ifEmpty { defaultStatuses },
                )

            is ExecutionState.Finish ->
                TestUiState(
                    selectedModel = state.model,
                    isRunning = false,
                    isInitializing = false,
                    frames = state.results.frames,
                    testStatuses = state.results.statuses.ifEmpty { defaultStatuses },
                )
        }
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
                        val state =
                            if (frame.result is ValidationResult.Pass) {
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

private sealed class ExecutionState {
    abstract val model: ModelConfiguration

    data class Idle(override val model: ModelConfiguration) : ExecutionState()

    data class Initializing(override val model: ModelConfiguration) : ExecutionState()

    data class Running(override val model: ModelConfiguration, val results: TestResults) :
        ExecutionState()

    data class Finish(override val model: ModelConfiguration, val results: TestResults) :
        ExecutionState()
}

private data class TestResults(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val statuses: ImmutableList<TestStatus> = persistentListOf(),
)

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TestUiState(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val selectedModel: ModelConfiguration,
    val isRunning: Boolean = false,
    val isInitializing: Boolean = false,
    val testStatuses: ImmutableList<TestStatus> = persistentListOf(),
)

private sealed interface TestRunState {
    data object Idle : TestRunState
    data object Initializing : TestRunState
    data object Running : TestRunState
}

private data class TestResults(
    val frames: ImmutableMap<String, TestResultFrame> = persistentMapOf(),
    val statuses: ImmutableList<TestStatus> = ToolCallingTest.REGRESSION_TEST_SUITE.map {
        TestStatus(it.name, TestStatus.State.IDLE)
    }.toImmutableList()
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

    private val loadingState = MutableStateFlow<TestRunState>(TestRunState.Idle)
    private val selectedModel = MutableStateFlow(initialModel)
    private val testResults = MutableStateFlow(TestResults())

    override val uiState: StateFlow<TestUiState> = combine(
        loadingState,
        selectedModel,
        testResults
    ) { loadingStateValue, selectedModelValue, testResultsValue ->
        TestUiState(
            selectedModel = selectedModelValue,
            isRunning = loadingStateValue is TestRunState.Running || loadingStateValue is TestRunState.Initializing,
            isInitializing = loadingStateValue is TestRunState.Initializing,
            frames = testResultsValue.frames,
            testStatuses = testResultsValue.statuses
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TestUiState(
            selectedModel = initialModel,
            testStatuses = ToolCallingTest.REGRESSION_TEST_SUITE.map {
                TestStatus(it.name, TestStatus.State.IDLE)
            }.toImmutableList()
        )
    )

    private var currentTest: ToolCallingTest? = null

    override fun onUiAction(uiAction: TestUiAction) {
        when (uiAction) {
            is TestUiAction.RunTests -> runTests(uiAction.useGpu)
            TestUiAction.CancelTests -> cancelTests()
        }
    }

    private fun runTests(useGpu: Boolean) {
        if (uiState.value.isRunning) return

        val currentModel = selectedModel.value
        val newBackend = if (useGpu) HardwareBackend.GPU_SUPPORTED else HardwareBackend.CPU_ONLY

        if (currentModel.hardwareAcceleration != newBackend) {
            selectedModel.update { currentModel.copy(hardwareAcceleration = newBackend) }
            inferenceEngine.closeSession()
        }

        scope.launch {
            loadingState.value = TestRunState.Initializing

            val initialResults = TestResults(
                statuses = ToolCallingTest.REGRESSION_TEST_SUITE.map {
                    TestStatus(it.name, TestStatus.State.IDLE)
                }.toImmutableList()
            )

            val modelConfig = selectedModel.value

            inferenceEngine
                .initializeAsFlow(
                    modelConfig = modelConfig,
                    modelPath = modelPath,
                ).onEach {
                    loadingState.value = TestRunState.Running
                }.flatMapConcat { engine ->
                    ToolCallingTest(
                        streamPromptExecutor = engine::promptStreaming,
                        resetConversation = engine::resetConversation,
                    ).also { currentTest = it }.runAllTest()
                }.catch { throwable ->
                    val errorFrame = TestResultFrame.Validation(
                        testName = "Error",
                        result = ValidationResult.Fail("Error: ${throwable.message}"),
                        duration = 0,
                        fullContent = "",
                    )
                    Logger.e("Error: ${throwable.message}")
                    emit(errorFrame)
                }.runningFold(initialResults) { current, frame ->
                    TestResults(
                        frames = (current.frames + (frame.id to frame)).toImmutableMap(),
                        statuses = updateTestStatuses(current.statuses, frame).toImmutableList()
                    )
                }.onCompletion {
                    loadingState.value = TestRunState.Idle
                }.collect { results ->
                    testResults.value = results
                }
        }
    }

    private fun cancelTests() {
        currentTest?.cancel()
        loadingState.value = TestRunState.Idle
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

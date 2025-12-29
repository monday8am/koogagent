package com.monday8am.presentation.testing

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TestUiState(
    val frames: Map<String, TestResultFrame> = emptyMap(),
    val selectedModel: ModelConfiguration,
    val isRunning: Boolean = false,
    val isInitializing: Boolean = false,
    val testStatuses: List<TestStatus> = emptyList(),
)

data class TestStatus(
    val name: String,
    val state: State,
) {
    enum class State {
        IDLE,
        RUNNING,
        PASS,
        FAIL,
    }
}

sealed class TestUiAction {
    data object RunTests : TestUiAction()
    data object CancelTests : TestUiAction()
}

interface TestViewModel {
    val uiState: Flow<TestUiState>

    fun onUiAction(uiAction: TestUiAction)

    fun dispose()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestViewModelImpl(
    private val selectedModel: ModelConfiguration,
    private val modelPath: String,
    private val inferenceEngine: LocalInferenceEngine,
) : TestViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(
        TestUiState(
            selectedModel = selectedModel,
            testStatuses =
            ToolCallingTest.REGRESSION_TEST_SUITE.map {
                TestStatus(it.name, TestStatus.State.IDLE)
            },
        ),
    )
    override val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    override fun onUiAction(uiAction: TestUiAction) {
        when (uiAction) {
            TestUiAction.RunTests -> runTests()
            TestUiAction.CancelTests -> cancelTests()
        }
    }

    private fun runTests() {
        if (_uiState.value.isRunning) return
        if (_uiState.value.isInitializing) return

        testJob = scope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    isInitializing = true,
                    frames = emptyMap(),
                    testStatuses =
                    it.testStatuses.map { status ->
                        status.copy(state = TestStatus.State.IDLE)
                    },
                )
            }

            inferenceEngine
                .initializeAsFlow(
                    modelConfig = selectedModel,
                    modelPath = modelPath,
                ).onEach {
                    _uiState.update { it.copy(isInitializing = false) }
                }.flatMapConcat { engine ->
                    ToolCallingTest(
                        streamPromptExecutor = engine::promptStreaming,
                        resetConversation = engine::resetConversation,
                    ).runAllTest()
                }.catch { throwable ->
                    val errorFrame = TestResultFrame.Validation(
                        testName = "Error",
                        result = ValidationResult.Fail("Error: ${throwable.message}"),
                        duration = 0,
                        fullContent = "",
                    )
                    emit(errorFrame)
                }.collect { frame ->
                    _uiState.update { currentState ->
                        val updatedFrames = currentState.frames + (frame.id to frame)
                        val updatedStatuses = updateTestStatuses(currentState.testStatuses, frame)
                        currentState.copy(frames = updatedFrames, testStatuses = updatedStatuses)
                    }
                }

            _uiState.update { it.copy(isRunning = false) }
        }
    }

    private fun cancelTests() {
        testJob?.cancel()
        _uiState.update { it.copy(isRunning = false, isInitializing = false) }
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

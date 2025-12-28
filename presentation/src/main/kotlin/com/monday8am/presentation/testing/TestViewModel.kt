package com.monday8am.presentation.testing

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

data class TestUiState(
    val frames: List<TestResultFrame> = emptyList(),
    val selectedModel: ModelConfiguration,
    val isRunning: Boolean = false,
)

sealed class TestUiAction {
    data object RunTests : TestUiAction()

    internal data object Initialize : TestUiAction()

    internal data class TestFrameReceived(
        val frame: TestResultFrame,
    ) : TestUiAction()
}

internal sealed interface TestActionState {
    data object Loading : TestActionState

    data class Success(
        val result: Any,
    ) : TestActionState

    data class Error(
        val throwable: Throwable,
    ) : TestActionState
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

    internal val userActions = MutableSharedFlow<TestUiAction>(replay = 0)

    override val uiState =
        userActions
            .onStart { emit(TestUiAction.Initialize) }
            .flatMapConcat { action ->
                val actionFlow =
                    when (action) {
                        TestUiAction.Initialize -> {
                            flowOf(value = Unit)
                        }

                        TestUiAction.RunTests -> {
                            inferenceEngine
                                .initializeAsFlow(
                                    modelConfig = selectedModel,
                                    modelPath = modelPath,
                                ).flatMapConcat { engine ->
                                    ToolCallingTest(
                                        streamPromptExecutor = engine::promptStreaming,
                                        resetConversation = engine::resetConversation,
                                    ).runAllTest()
                                }
                        }

                        is TestUiAction.TestFrameReceived -> {
                            flowOf(value = action.frame)
                        }
                    }

                actionFlow
                    .map<Any, TestActionState> { result -> TestActionState.Success(result) }
                    .onStart {
                        if (action is TestUiAction.RunTests) {
                            emit(TestActionState.Loading)
                        }
                    }.catch { throwable -> emit(TestActionState.Error(throwable)) }
                    .map { actionState -> action to actionState }
            }.flowOn(Dispatchers.IO)
            .scan(TestUiState(selectedModel = selectedModel)) { previousState, (action, actionState) ->
                reduce(state = previousState, action = action, actionState = actionState)
            }.distinctUntilChanged()

    override fun onUiAction(uiAction: TestUiAction) {
        scope.launch {
            userActions.emit(uiAction)
        }
    }

    override fun dispose() {
        inferenceEngine.closeSession()
        scope.cancel()
    }

    internal fun reduce(
        state: TestUiState,
        action: TestUiAction,
        actionState: TestActionState,
    ): TestUiState =
        when (actionState) {
            is TestActionState.Loading -> {
                when (action) {
                    TestUiAction.RunTests -> state.copy(
                        isRunning = true,
                        frames = emptyList(),
                    )
                    else -> state
                }
            }

            is TestActionState.Success -> {
                when (action) {
                    is TestUiAction.RunTests -> {
                        val frame = actionState.result as? TestResultFrame
                        if (frame != null) {
                            val updatedFrames = mergeFrame(state.frames, frame)
                            val isCompleted = frame is TestResultFrame.Validation
                            state.copy(
                                frames = updatedFrames,
                                isRunning = !isCompleted || state.isRunning,
                            )
                        } else {
                            state.copy(isRunning = false)
                        }
                    }

                    is TestUiAction.Initialize -> state

                    is TestUiAction.TestFrameReceived -> {
                        val updatedFrames = mergeFrame(state.frames, action.frame)
                        state.copy(frames = updatedFrames)
                    }
                }
            }

            is TestActionState.Error -> {
                val errorFrame = TestResultFrame.Validation(
                    testName = "Error",
                    result = ValidationResult.Fail("Error: ${actionState.throwable.message}"),
                    duration = 0,
                    fullContent = "",
                )
                state.copy(
                    isRunning = false,
                    frames = state.frames + errorFrame,
                )
            }
        }

    /**
     * Merges a new frame into the frame list.
     * For streaming frames (Thinking, Content, Tool): replaces existing frame with same testName + type.
     * For Validation frames or if no match found: appends to list.
     */
    private fun mergeFrame(
        frames: List<TestResultFrame>,
        newFrame: TestResultFrame,
    ): List<TestResultFrame> {
        // Validation frames are always appended (they mark test completion)
        if (newFrame is TestResultFrame.Validation) {
            return frames + newFrame
        }

        // Find existing frame with same testName and type
        val existingIndex = frames.indexOfFirst { existing ->
            existing.testName == newFrame.testName && existing::class == newFrame::class
        }

        return if (existingIndex >= 0) {
            // Replace existing frame
            frames.toMutableList().apply { set(existingIndex, newFrame) }
        } else {
            // Append new frame
            frames + newFrame
        }
    }
}

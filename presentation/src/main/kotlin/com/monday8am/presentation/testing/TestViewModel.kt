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
    val logMessages: List<String> = listOf("Ready to run tests"),
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
                        logMessages = state.logMessages + "Initializing model...",
                    )
                    else -> state
                }
            }

            is TestActionState.Success -> {
                when (action) {
                    is TestUiAction.RunTests -> {
                        val frame = actionState.result as? TestResultFrame
                        if (frame != null) {
                            val newMessage = frame.toLogMessage()
                            val isCompleted = frame is TestResultFrame.Validation
                            state.copy(
                                logMessages = state.logMessages + newMessage,
                                isRunning = !isCompleted || state.isRunning,
                            )
                        } else {
                            state.copy(isRunning = false)
                        }
                    }

                    is TestUiAction.Initialize -> {
                        state.copy(
                            logMessages = listOf("Model: ${selectedModel.displayName}", "Ready to run tests"),
                        )
                    }

                    is TestUiAction.TestFrameReceived -> {
                        val newMessage = action.frame.toLogMessage()
                        state.copy(logMessages = state.logMessages + newMessage)
                    }
                }
            }

            is TestActionState.Error -> {
                state.copy(
                    isRunning = false,
                    logMessages = state.logMessages + "Error: ${actionState.throwable.message}",
                )
            }
        }
}

private fun TestResultFrame.toLogMessage(): String =
    when (this) {
        is TestResultFrame.Content -> chunk
        is TestResultFrame.Thinking -> "Thinking: $accumulator"
        is TestResultFrame.Tool -> "Tool: $content"
        is TestResultFrame.Validation ->
            when (result) {
                is ValidationResult.Pass -> "PASS (${duration}ms): ${result.message}"
                is ValidationResult.Fail -> "FAIL (${duration}ms): ${result.message}"
            }
    }

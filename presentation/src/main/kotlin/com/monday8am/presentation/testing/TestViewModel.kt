package com.monday8am.presentation.testing

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TestUiState(
    val frames: Map<String, TestResultFrame> = emptyMap(),
    val selectedModel: ModelConfiguration,
    val isRunning: Boolean = false,
)

sealed class TestUiAction {
    data object RunTests : TestUiAction()
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

    private val _uiState = MutableStateFlow(TestUiState(selectedModel = selectedModel))
    override val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    override fun onUiAction(uiAction: TestUiAction) {
        when (uiAction) {
            TestUiAction.RunTests -> runTests()
        }
    }

    private fun runTests() {
        if (_uiState.value.isRunning) return

        scope.launch {
            _uiState.update { it.copy(isRunning = true, frames = emptyMap()) }

            inferenceEngine
                .initializeAsFlow(
                    modelConfig = selectedModel,
                    modelPath = modelPath,
                ).flatMapConcat { engine ->
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
                        currentState.copy(frames = updatedFrames)
                    }
                }

            _uiState.update { it.copy(isRunning = false) }
        }
    }

    override fun dispose() {
        inferenceEngine.closeSession()
        scope.cancel()
    }
}

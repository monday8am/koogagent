package com.monday8am.presentation.testing

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.agent.core.LocalLLModel
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherProvider
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

private const val MODEL_NAME = "gemma3-1b-it-int4.litertlm"

/**
 * UI State for testing screen
 */
data class TestingUiState(
    val statusMessage: TestLogMessage = TestLogMessage.Initializing,
    val isModelReady: Boolean = false,
    val testResults: String? = null,
)

/**
 * UI Actions for testing
 */
sealed class TestingUiAction {
    data object RunTests : TestingUiAction()

    internal data object Initialize : TestingUiAction()

    internal data class TestProgressUpdate(
        val progress: String,
    ) : TestingUiAction()

    internal data class TestsCompleted(
        val results: String,
    ) : TestingUiAction()
}

/**
 * Internal action states for async operations
 */
internal sealed interface TestingActionState {
    data object Loading : TestingActionState

    data class Success(
        val result: Any,
    ) : TestingActionState

    data class Error(
        val throwable: Throwable,
    ) : TestingActionState
}

/**
 * Testing ViewModel interface
 */
interface TestingViewModel {
    val uiState: Flow<TestingUiState>

    fun onUiAction(uiAction: TestingUiAction)

    fun dispose()
}

/**
 * Testing ViewModel implementation
 *
 * Simplified version of NotificationViewModel that only handles:
 * - Inference engine initialization
 * - Running tests from GemmaToolCallingTest
 * - Progressive test result updates
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestingViewModelImpl(
    private val inferenceEngine: LocalInferenceEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val modelPath: () -> String,
) : TestingViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val userActions = MutableSharedFlow<TestingUiAction>(replay = 0)

    override val uiState =
        userActions
            .onStart { emit(TestingUiAction.Initialize) }
            .distinctUntilChanged()
            .flatMapConcat { action ->
                val actionFlow =
                    when (action) {
                        TestingUiAction.Initialize -> inferenceEngine.initializeAsFlow(model = getLocalModel())
                        TestingUiAction.RunTests -> runTests()
                        is TestingUiAction.TestProgressUpdate -> flowOf(action.progress)
                        is TestingUiAction.TestsCompleted -> flowOf(action.results)
                    }

                actionFlow
                    .map<Any, TestingActionState> { result -> TestingActionState.Success(result) }
                    .onStart {
                        if (action is TestingUiAction.RunTests) {
                            emit(TestingActionState.Loading)
                        }
                    }.catch { throwable -> emit(TestingActionState.Error(throwable)) }
                    .map { actionState -> action to actionState }
            }.flowOn(Dispatchers.IO)
            .scan(TestingUiState()) { previousState, (action, actionState) ->
                reduce(state = previousState, action = action, actionState = actionState)
            }.distinctUntilChanged()

    override fun onUiAction(uiAction: TestingUiAction) {
        scope.launch {
            userActions.emit(uiAction)
        }
    }

    override fun dispose() {
        inferenceEngine.closeSession()
        scope.cancel()
    }

    internal fun reduce(
        state: TestingUiState,
        action: TestingUiAction,
        actionState: TestingActionState,
    ): TestingUiState =
        when (actionState) {
            is TestingActionState.Loading -> {
                when (action) {
                    TestingUiAction.RunTests -> state.copy(statusMessage = TestLogMessage.RunningTests)
                    else -> state
                }
            }

            is TestingActionState.Success -> {
                when (action) {
                    is TestingUiAction.Initialize -> {
                        state.copy(
                            statusMessage = TestLogMessage.ModelReady,
                            isModelReady = true,
                        )
                    }

                    is TestingUiAction.RunTests -> {
                        state.copy(statusMessage = TestLogMessage.RunningTests)
                    }

                    is TestingUiAction.TestProgressUpdate -> {
                        state.copy(statusMessage = TestLogMessage.TestProgress(action.progress))
                    }

                    is TestingUiAction.TestsCompleted -> {
                        state.copy(
                            statusMessage = TestLogMessage.TestCompleted(action.results),
                            testResults = action.results,
                        )
                    }
                }
            }

            is TestingActionState.Error -> {
                state.copy(statusMessage = TestLogMessage.Error(actionState.throwable.message ?: "Unknown error"))
            }
        }

    private fun runTests(): Flow<String> =
        kotlinx.coroutines.flow.flow {
            val test =
                GemmaToolCallingTest(
                    promptExecutor = { prompt ->
                        inferenceEngine.prompt(prompt).getOrThrow()
                    },
                    weatherProvider = weatherProvider,
                    locationProvider = locationProvider,
                )

            // Collect progress updates from test
            test.runAllTests().collect { progress ->
                emit(progress)
                onUiAction(TestingUiAction.TestProgressUpdate(progress))
            }
        }.catch { throwable ->
            emit("Test failed: ${throwable.message}")
        }

    private fun getLocalModel() =
        LocalLLModel(
            path = modelPath(),
            temperature = 0.8f,
        )
}

package com.monday8am.presentation.notifications

import ai.koog.agents.core.tools.ToolRegistry
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.agent.core.LocalLLModel
import com.monday8am.agent.core.NotificationAgent
import com.monday8am.agent.core.NotificationGenerator
import com.monday8am.agent.core.ToolFormat
import com.monday8am.agent.tools.GetLocation
import com.monday8am.agent.tools.GetWeatherFromLocation
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.presentation.testing.GemmaToolCallingTest
import com.monday8am.presentation.testing.TestResult
import com.monday8am.presentation.testing.TestResultFrame
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

private const val MODEL_URL = "https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip"
private const val MODEL_NAME1 = "gemma3-1b-it-int4.litertlm"
private const val MODEL_NAME = "qwen3_0.6b_q8_ekv4096.litertlm"

val defaultNotificationContext =
    NotificationContext(
        mealType = MealType.WATER,
        motivationLevel = MotivationLevel.HIGH,
        alreadyLogged = true,
        userLocale = "en-US",
        country = "ES",
    )

data class UiState(
    val statusMessage: LogMessage = LogMessage.Initializing,
    val context: NotificationContext = defaultNotificationContext,
    val isModelReady: Boolean = false,
    val notification: NotificationResult? = null,
    val downloadStatus: ModelDownloadManager.Status = ModelDownloadManager.Status.Pending,
)

sealed class UiAction {
    data object DownloadModel : UiAction()

    data object ShowNotification : UiAction()

    data object RunModelTests : UiAction()

    data class UpdateContext(
        val context: NotificationContext,
    ) : UiAction()

    internal data object Initialize : UiAction()

    internal data class NotificationReady(
        val content: NotificationResult,
    ) : UiAction()
}

internal sealed interface ActionState {
    data object Loading : ActionState

    data class Success(
        val result: Any,
    ) : ActionState

    data class Error(
        val throwable: Throwable,
    ) : ActionState
}

interface NotificationViewModel {
    val uiState: Flow<UiState>

    fun onUiAction(uiAction: UiAction)

    fun dispose()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelImpl(
    private val inferenceEngine: LocalInferenceEngine,
    private val notificationEngine: NotificationEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val deviceContextProvider: DeviceContextProvider,
    private val modelManager: ModelDownloadManager,
) : NotificationViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val toolRegistry =
        ToolRegistry {
            tool(tool = GetWeatherFromLocation(weatherProvider))
            tool(tool = GetLocation(locationProvider))
        }

    internal val userActions = MutableSharedFlow<UiAction>(replay = 0)

    override val uiState =
        userActions
            .onStart { emit(UiAction.Initialize) }
            .flatMapConcat { action ->
                val actionFlow =
                    when (action) {
                        UiAction.Initialize -> {
                            flowOf(value = modelManager.modelExists(modelName = MODEL_NAME))
                        }

                        UiAction.DownloadModel -> {
                            modelManager.downloadModel(url = MODEL_URL, modelName = MODEL_NAME)
                        }

                        UiAction.ShowNotification -> {
                            inferenceEngine.initializeAsFlow(model = getLocalModel())
                        }

                        UiAction.RunModelTests -> {
                            inferenceEngine.initializeAsFlow(model = getLocalModel()).flatMapConcat { engine ->
                                runModelTests(
                                    promptExecutor = engine::prompt,
                                    streamPromptExecutor = engine::promptStreaming,
                                    resetConversation = engine::resetConversation
                                )
                            }
                        }

                        is UiAction.UpdateContext -> {
                            flowOf(value = action.context)
                        }

                        is UiAction.NotificationReady -> {
                            flowOf(value = action.content)
                        }
                    }

                actionFlow
                    .map<Any, ActionState> { result -> ActionState.Success(result) }
                    .onStart {
                        if (action is UiAction.DownloadModel || action is UiAction.ShowNotification || action is UiAction.RunModelTests) {
                            emit(ActionState.Loading)
                        }
                    }.catch { throwable -> emit(ActionState.Error(throwable)) }
                    .map { actionState -> action to actionState }
            }.flowOn(Dispatchers.IO)
            .scan(UiState()) { previousState, (action, actionState) ->
                reduce(state = previousState, action = action, actionState = actionState)
            }.distinctUntilChanged()
            .onEach { state ->
                // Side effects!
                if (state.notification != null) {
                    notificationEngine.showNotification(state.notification)
                }
            }

    override fun onUiAction(uiAction: UiAction) {
        scope.launch {
            userActions.emit(uiAction)
        }
    }

    override fun dispose() {
        modelManager.cancelDownload()
        inferenceEngine.closeSession()
        scope.cancel()
    }

    internal fun reduce(
        state: UiState,
        action: UiAction,
        actionState: ActionState,
    ): UiState =
        when (actionState) {
            is ActionState.Loading -> {
                when (action) {
                    UiAction.DownloadModel -> state.copy(downloadStatus = ModelDownloadManager.Status.InProgress(0f))
                    UiAction.ShowNotification -> state.copy(statusMessage = LogMessage.InitializingModel)
                    UiAction.RunModelTests -> state.copy(statusMessage = LogMessage.InitTests)
                    else -> state
                }
            }

            is ActionState.Success -> {
                when (action) {
                    is UiAction.DownloadModel -> {
                        val status = actionState.result as ModelDownloadManager.Status
                        val logMessage =
                            when (status) {
                                is ModelDownloadManager.Status.InProgress -> LogMessage.Downloading(status.progress ?: 0f)
                                is ModelDownloadManager.Status.Completed -> LogMessage.DownloadComplete
                                else -> LogMessage.DownloadFinished
                            }
                        state.copy(
                            downloadStatus = status,
                            statusMessage = logMessage,
                            isModelReady = status is ModelDownloadManager.Status.Completed,
                        )
                    }

                    is UiAction.ShowNotification -> {
                        createNotification(promptExecutor = inferenceEngine::prompt, context = state.context)
                        state.copy(statusMessage = LogMessage.PromptingWithContext(state.context.formatted))
                    }

                    is UiAction.NotificationReady -> {
                        state.copy(
                            statusMessage = LogMessage.NotificationGenerated(action.content.formatted),
                            notification = action.content,
                        )
                    }

                    is UiAction.UpdateContext -> {
                        state.copy(context = action.context)
                    }

                    is UiAction.Initialize -> {
                        val isModelReady = actionState.result as Boolean
                        state.copy(
                            statusMessage =
                                if (isModelReady) {
                                    LogMessage.WelcomeModelReady(MODEL_NAME)
                                } else {
                                    LogMessage.WelcomeDownloadRequired
                                },
                            isModelReady = isModelReady,
                        )
                    }

                    UiAction.RunModelTests -> {
                        state.copy(statusMessage = LogMessage.TestResultMessage(actionState.result as TestResultFrame))
                    }
                }
            }

            is ActionState.Error -> {
                state.copy(statusMessage = LogMessage.Error(actionState.throwable.message ?: "Unknown error"))
            }
        }

    private fun createNotification(
        promptExecutor: suspend (String) -> Result<String>,
        context: NotificationContext,
    ) {
        scope.launch {
            val deviceContext = deviceContextProvider.getDeviceContext()
            val notificationContext =
                context.copy(
                    userLocale = deviceContext.language,
                    country = deviceContext.country,
                )

            // Using NATIVE format for LiteRT-LM's native tool calling
            // Tools are passed via ConversationConfig (in LocalInferenceEngineImpl)
            // and handled by Qwen3DataProcessor automatically
            val agent =
                NotificationAgent.local(
                    promptExecutor = { prompt ->
                        promptExecutor(prompt).getOrThrow()
                    },
                    modelId = MODEL_NAME.removeSuffix(".litertlm"),
                    toolFormat = ToolFormat.NATIVE, // Native LiteRT-LM tool calling
                )
            agent.initializeWithTools(toolRegistry = toolRegistry)
            val content = NotificationGenerator(agent = agent).generate(notificationContext)
            onUiAction(uiAction = UiAction.NotificationReady(content = content))
        }
    }

    private fun runModelTests(
        promptExecutor: suspend (String) -> Result<String>,
        streamPromptExecutor: (String) -> Flow<String>,
        resetConversation: () -> Result<Unit>,
    ) = GemmaToolCallingTest(
        promptExecutor = { prompt ->
            promptExecutor(prompt).getOrThrow()
        },
        streamPromptExecutor = streamPromptExecutor,
        resetConversation = resetConversation,
        weatherProvider = weatherProvider,
        locationProvider = locationProvider,
    ).runAllTest()

    private fun getLocalModel() =
        LocalLLModel(
            path = modelManager.getModelPath(MODEL_NAME),
            temperature = 0.8f,
        )
}

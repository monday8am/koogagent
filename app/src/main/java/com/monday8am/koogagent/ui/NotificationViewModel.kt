package com.monday8am.koogagent.ui

import ai.koog.agents.core.tools.ToolRegistry
import com.monday8am.agent.GetLocationTool
import com.monday8am.agent.GetWeatherToolFromLocation
import com.monday8am.agent.LocalLLModel
import com.monday8am.agent.NotificationGenerator
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.WeatherProvider
import com.monday8am.koogagent.mediapipe.GemmaAgent
import com.monday8am.koogagent.mediapipe.LocalInferenceEngine
import com.monday8am.koogagent.mediapipe.download.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val defaultNotificationContext =
    NotificationContext(
        mealType = MealType.WATER,
        motivationLevel = MotivationLevel.HIGH,
        alreadyLogged = true,
        userLocale = "en-US",
        country = "ES",
    )

data class UiState(
    val textLog: String = "Initializing!",
    val context: NotificationContext = defaultNotificationContext,
    val isModelReady: Boolean = false,
    val notification: NotificationResult? = null,
    val downloadStatus: ModelDownloadManager.Status = ModelDownloadManager.Status.Pending,
)

internal sealed interface ActionState {
    data object Loading : ActionState
    data class Success(val result: Any) : ActionState
    data class Error(val throwable: Throwable) : ActionState
}

sealed class UiAction {
    data object DownloadModel : UiAction()
    data object ShowNotification : UiAction()
    data class UpdateContext(val context: NotificationContext) : UiAction()

    internal data object Initialize : UiAction()
    internal data class NotificationReady(val content: NotificationResult): UiAction()
}

private const val GemmaModelUrl = "https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip"
private const val GemmaModelName = "gemma3-1b-it-int4.litertlm"

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
            tool(tool = GetWeatherToolFromLocation(weatherProvider))
            tool(tool = GetLocationTool(locationProvider))
        }

    internal val userActions: MutableStateFlow<UiAction> = MutableStateFlow(UiAction.Initialize)

    override val uiState = userActions
        .flatMapConcat { action ->
            val actionFlow = when (action) {
                UiAction.Initialize -> flowOf(modelManager.modelExists(modelName = GemmaModelName))
                UiAction.DownloadModel -> modelManager.downloadModel(url = GemmaModelUrl, modelName = GemmaModelName)
                UiAction.ShowNotification -> inferenceEngine.initializeAsFlow(model = getLocalModel())
                is UiAction.UpdateContext -> flowOf(action.context)
                is UiAction.NotificationReady -> flowOf(value = action.content)
            }

            actionFlow
                .map<Any, ActionState> { result -> ActionState.Success(result) }
                .onStart {
                    if (action is UiAction.DownloadModel || action is UiAction.ShowNotification) {
                        emit(ActionState.Loading)
                    }
                }
                .catch { throwable -> emit(ActionState.Error(throwable)) }
                .map { actionState -> action to actionState }
        }
        .flowOn(Dispatchers.IO)
        .scan(UiState()) { previousState, (action, actionState) ->
            reduce(state = previousState, action = action, actionState = actionState)
        }
        .distinctUntilChanged()
        .onEach { state -> // Side effects!
            if (state.notification != null) {
                notificationEngine.showNotification(state.notification)
            }
        }

    override fun onUiAction(uiAction: UiAction) = userActions.update { uiAction }

    override fun dispose() {
        scope.cancel()
    }

    private fun reduce(state: UiState, action: UiAction, actionState: ActionState): UiState {
        return when (actionState) {
            is ActionState.Loading -> {
                when (action) {
                    UiAction.DownloadModel -> state.copy(downloadStatus = ModelDownloadManager.Status.InProgress(0f))
                    UiAction.ShowNotification -> state.copy(textLog = "Initializing model for notification...")
                    else -> state
                }
            }

            is ActionState.Success -> {
                when (action) {
                    is UiAction.DownloadModel -> {
                        val status = actionState.result as ModelDownloadManager.Status
                        val logMessage = when (status) {
                            is ModelDownloadManager.Status.InProgress -> "Downloading: ${"%.1f".format(status.progress?.times(100))}%"
                            is ModelDownloadManager.Status.Completed -> "Download complete! Model is ready."
                            else -> "Download finished."
                        }
                        state.copy(
                            downloadStatus = status,
                            textLog = logMessage,
                            isModelReady = status is ModelDownloadManager.Status.Completed
                        )
                    }

                    is UiAction.ShowNotification -> {
                        createNotification(promptExecutor = inferenceEngine::prompt, context = state.context)
                        state.copy(textLog = "Prompting with context:\n ${state.context.formatted}")
                    }

                    is UiAction.NotificationReady -> {
                        state.copy(
                            textLog = "Notification:\n ${action.content.formatted}",
                            notification = action.content
                        )
                    }

                    is UiAction.UpdateContext -> {
                        state.copy(context = action.context)
                    }

                    is UiAction.Initialize -> {
                        val isModelReady = actionState.result as Boolean
                        state.copy(
                            textLog = if (isModelReady) {
                                "Welcome to Yazio notificator :)\nInitialized with model $GemmaModelName"
                            } else {
                                "Welcome!\nPress download model button. It's a one time operation and it will take close to 4 minutes."
                            },
                            isModelReady = isModelReady
                        )
                    }
                }
            }

            is ActionState.Error -> {
                state.copy(textLog = "An error occurred: ${actionState.throwable.message}")
            }
        }
    }

    private fun createNotification(promptExecutor: suspend (String) -> Result<String>, context: NotificationContext) {
        scope.launch {
            val deviceContext = deviceContextProvider.getDeviceContext()
            val notificationContext = context.copy(
                    userLocale = deviceContext.language,
                    country = deviceContext.country,
                )

            val agent = GemmaAgent(
                promptExecutor = { prompt ->
                    promptExecutor(prompt).getOrThrow()
                }
            )
            agent.initializeWithTools(toolRegistry = toolRegistry)
            val content = NotificationGenerator(agent = agent).generate(notificationContext)
            onUiAction(uiAction = UiAction.NotificationReady(content = content))
        }
    }

    private fun getLocalModel() = LocalLLModel(
        path = modelManager.getModelPath(GemmaModelName),
        temperature = 0.8f,
    )
}

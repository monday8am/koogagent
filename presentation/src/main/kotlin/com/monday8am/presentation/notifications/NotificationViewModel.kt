package com.monday8am.presentation.notifications

import ai.koog.agents.core.tools.ToolRegistry
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.agent.core.NotificationAgent
import com.monday8am.agent.core.NotificationGenerator
import com.monday8am.agent.tools.GetLocation
import com.monday8am.agent.tools.GetWeatherFromLocation
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
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
    val statusMessage: LogMessage = LogMessage.Initializing,
    val context: NotificationContext = defaultNotificationContext,
    val selectedModel: ModelConfiguration,
    val notification: NotificationResult? = null,
)

sealed class UiAction {
    data object ShowNotification : UiAction()

    data class UpdateContext(val context: NotificationContext) : UiAction()

    internal data object Initialize : UiAction()

    internal data class NotificationReady(val content: NotificationResult) : UiAction()
}

internal sealed interface ActionState {
    data object Loading : ActionState

    data class Success(val result: Any) : ActionState

    data class Error(val throwable: Throwable) : ActionState
}

interface NotificationViewModel {
    val uiState: Flow<UiState>

    fun onUiAction(uiAction: UiAction)

    fun dispose()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelImpl(
    private val selectedModel: ModelConfiguration,
    private val modelPath: String,
    private val inferenceEngine: LocalInferenceEngine,
    private val notificationEngine: NotificationEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val deviceContextProvider: DeviceContextProvider,
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
                            flowOf(value = Unit)
                        }

                        UiAction.ShowNotification -> {
                            inferenceEngine.initializeAsFlow(
                                modelConfig = selectedModel,
                                modelPath = modelPath,
                            )
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
                        if (action is UiAction.ShowNotification) {
                            emit(ActionState.Loading)
                        }
                    }.catch { throwable -> emit(ActionState.Error(throwable)) }
                    .map { actionState -> action to actionState }
            }.flowOn(Dispatchers.IO)
            .scan(UiState(selectedModel = selectedModel)) { previousState, (action, actionState) ->
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
        inferenceEngine.closeSession()
        scope.cancel()
    }

    internal fun reduce(state: UiState, action: UiAction, actionState: ActionState): UiState = when (actionState) {
        is ActionState.Loading -> {
            when (action) {
                UiAction.ShowNotification -> state.copy(statusMessage = LogMessage.InitializingModel)
                else -> state
            }
        }

        is ActionState.Success -> {
            when (action) {
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
                    state.copy(
                        statusMessage = LogMessage.WelcomeModelReady(selectedModel.displayName),
                    )
                }
            }
        }

        is ActionState.Error -> {
            state.copy(statusMessage = LogMessage.Error(actionState.throwable.message ?: "Unknown error"))
        }
    }

    private fun createNotification(promptExecutor: suspend (String) -> Result<String>, context: NotificationContext) {
        scope.launch {
            val deviceContext = deviceContextProvider.getDeviceContext()
            val notificationContext =
                context.copy(
                    userLocale = deviceContext.language,
                    country = deviceContext.country,
                )

            val agent =
                NotificationAgent.local(
                    promptExecutor = { prompt ->
                        promptExecutor(prompt).getOrThrow()
                    },
                    modelId = selectedModel.modelId,
                )
            agent.initializeWithTools(toolRegistry = toolRegistry)
            val content = NotificationGenerator(agent = agent).generate(notificationContext)
            onUiAction(uiAction = UiAction.NotificationReady(content = content))
        }
    }
}

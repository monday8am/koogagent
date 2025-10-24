package com.monday8am.koogagent.ui

import ai.koog.agents.core.tools.ToolRegistry
import android.util.Log
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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

sealed interface ActionState {
    data object Loading : ActionState
    data class Success(val result: Any) : ActionState
    data class Error(val throwable: Throwable) : ActionState
}

sealed interface UiAction {
    data object DownloadModel: UiAction
    data object ShowNotification: UiAction
    data class UpdateContext(val context: NotificationContext): UiAction
}

private const val GemmaModelUrl = "https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip"
private const val GemmaModelName = "gemma3-1b-it-int4.litertlm"

// (action -> create flow -> )
class Notification2ViewModel(
    private val inferenceEngine: LocalInferenceEngine,
    private val notificationEngine: NotificationEngine,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val deviceContextProvider: DeviceContextProvider,
    private val modelManager: ModelDownloadManager,
    private val viewModelScope: CoroutineScope,
) {
    private val toolRegistry =
        ToolRegistry {
            tool(tool = GetWeatherToolFromLocation(weatherProvider))
            tool(tool = GetLocationTool(locationProvider))
        }

    private val localModel =
        LocalLLModel(
            path = modelManager.getModelPath(GemmaModelName),
            temperature = 0.8f,
        )

    internal val userActions: MutableStateFlow<UiAction?> = MutableStateFlow(null)

    val uiState = userActions
        .filterNotNull()
        .flatMapConcat { action ->
            getActionFlow(action)
                .map { state -> action to state }
                .catch { throwable ->
                    emit(action to ActionState.Error(throwable))
                }
        }
        .scan(UiState()) { previousState, (action, actionState) ->
            // The reducer now handles Loading, Success, and Error states
            reduce(state = previousState, action = action, state = actionState)
        }
        .distinctUntilChanged()
        .onEach { state ->
            // This side-effect logic remains the same
            if (state.notification != null) {
                notificationEngine.showNotification(state.notification)
            }
        }
        .launchIn(scope = viewModelScope)

    fun runToolTests() {
        if (_uiState.value.isModelReady.not()) {
            printLog("Model isn't ready yet. Please wait.")
            return
        }

        /*
        instance?.let { gemmaInstance ->
            viewModelScope.launch {
                printLog("Starting tool calling tests...\n")

                val tester = GemmaToolCallingTest(instance = gemmaInstance)
                val results = tester.runAllTests()
                printLog(results)
            }
        }
         */
    }

    fun updateContext(context: NotificationContext) {
        _uiState.update { it.copy(context = context) }
    }

    // Helper function to create the flow for a given action
    private fun getActionFlow(action: UiAction): Flow<ActionState> {
        return when (action) {
            UiAction.DownloadModel ->
                modelManager.downloadModel(url = GemmaModelUrl, modelName = GemmaModelName)
                    .map<ModelDownloadManager.Status, ActionState> { ActionState.Success(it) }
                    .onStart { emit(ActionState.Loading) }

            UiAction.ShowNotification ->
                inferenceEngine.initializeAsFlow(localModel)
                    .map<Unit, ActionState> { ActionState.Success(Unit) } // Wrap result in Success
                    .onStart { emit(ActionState.Loading) } // <-- EMIT LOADING STATE HERE

            is UiAction.UpdateContext ->
                flowOf(ActionState.Success(action.context)) // This is a synchronous action

            else -> flowOf() // No operation
        }
    }

    suspend fun reduce(state: UiState, action: UiAction?, result: Any): UiState {
        return when (action) {
            is UiAction.DownloadModel if result is ModelDownloadManager.Status -> {
                state.copy(downloadStatus = result)
            }

            is UiAction.ShowNotification if result is Result<*> -> {
                val notification = (result.getOrNull() as? LocalInferenceEngine)?.let { engine ->
                    createNotification(promptModel = engine::prompt, uiState = state)
                }
                state.copy(notification = notification)
            }

            else -> state
        }
    }

    private suspend fun createNotification(promptModel: suspend (String) -> String?, uiState: UiState): NotificationResult {
        val deviceContext = deviceContextProvider.getDeviceContext()
        val notificationContext =
            uiState.context.copy(
                userLocale = deviceContext.language,
                country = deviceContext.country,
            )
        val agent = GemmaAgent(promptModel = promptModel)
        agent.initializeWithTools(toolRegistry = toolRegistry)
        return NotificationGenerator(agent = agent).generate(notificationContext)
    }

    private fun printLog(log: String) {
        // _uiState.update { it.copy(textLog = log) }
        Log.d("NotificationViewModel", log)
    }
}

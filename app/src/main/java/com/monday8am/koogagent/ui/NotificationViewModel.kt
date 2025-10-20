package com.monday8am.koogagent.ui

import ai.koog.agents.core.tools.ToolRegistry
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.monday8am.agent.GetLocationTool
import com.monday8am.agent.GetWeatherTool
import com.monday8am.agent.LocalLLModel
import com.monday8am.agent.NotificationGenerator
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult
import com.monday8am.koogagent.data.OpenMeteoWeatherProvider
import com.monday8am.koogagent.mediapipe.GemmaAgent
import com.monday8am.koogagent.mediapipe.LlmModelInstance
import com.monday8am.koogagent.mediapipe.LocalInferenceUtils
import com.monday8am.koogagent.mediapipe.download.GemmaToolCallingTest
import com.monday8am.koogagent.mediapipe.download.ModelDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

private const val GemmaModelUrl = "https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip"
private const val GemmaModelName = "gemma3-1b-it-int4.litertlm"

class NotificationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val modelManager = ModelDownloadManager(application)
    private val weatherProvider = OpenMeteoWeatherProvider()
    private val locationProvider = MockLocationProvider()
    private val toolRegistry =
        ToolRegistry {
            tool(tool = GetWeatherTool(weatherProvider))
            tool(tool = GetLocationTool(locationProvider))
        }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private var instance: LlmModelInstance? = null
    private val localModel =
        LocalLLModel(
            path = modelManager.getModelPath(GemmaModelName),
            temperature = 0.8f,
        )

    init {
        if (modelManager.modelExists(GemmaModelName)) {
            initGemmaModel()
        } else {
            printLog("Welcome!\nPress download model button. It's a one time operation and it will take close to 4 minutes.")
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            modelManager.downloadModel(url = GemmaModelUrl, modelName = GemmaModelName).collect { status ->
                when (status) {
                    ModelDownloadManager.DownloadStatus.Pending -> { }
                    ModelDownloadManager.DownloadStatus.Cancelled -> { }
                    is ModelDownloadManager.DownloadStatus.Completed -> {
                        printLog("Download ready!")
                        initGemmaModel()
                    }
                    is ModelDownloadManager.DownloadStatus.Failed -> {
                        printLog("Download failed with error: ${status.message}")
                    }
                    is ModelDownloadManager.DownloadStatus.InProgress -> {
                        printLog("Download in progress: ${String.format("%.2f", status.progress ?: 0.00)}%")
                    }
                }
            }
        }
    }

    fun processAndShowNotification() {
        if (_uiState.value.isModelReady.not()) {
            printLog("Model isn't ready yet. Please wait.")
            return
        }

        instance?.let { instance ->
            viewModelScope.launch {
                printLog("Generating notification with agentic tool-based approach...")
                printLog("Agent will autonomously decide if weather data is needed...")

                val deviceContext = DeviceContextUtil.getDeviceContext(application)
                val currentContext =
                    _uiState.value.context.copy(
                        userLocale = deviceContext.language,
                        country = deviceContext.country,
                        // Note: weather is no longer set here - agent will fetch it via tool if needed
                    )

                printLog("Prompting with context:\n ${currentContext.formatted}")

                val processedPayload = doExtraProcessing(instance = instance, context = currentContext)
                with(processedPayload) {
                    if (isFallback) {
                        printLog("Failed with error: ${errorMessage}\nFallback message:\n $formatted")
                    } else {
                        printLog("Notification:\n $formatted")
                        NotificationUtils.showNotification(getApplication(), this)
                    }
                }
            }
        }
    }

    fun runToolTests() {
        if (_uiState.value.isModelReady.not()) {
            printLog("Model isn't ready yet. Please wait.")
            return
        }

        instance?.let { gemmaInstance ->
            viewModelScope.launch {
                printLog("Starting tool calling tests...\n")

                val tester = GemmaToolCallingTest(instance = gemmaInstance)
                val results = tester.runAllTests()
                printLog(results)
            }
        }
    }

    fun updateContext(context: NotificationContext) {
        _uiState.update { it.copy(context = context) }
    }

    override fun onCleared() {
        super.onCleared()
        instance?.let {
            LocalInferenceUtils.close(instance = it)
        }
    }

    private fun initGemmaModel() {
        viewModelScope.launch {
            LocalInferenceUtils
                .initialize(context = application.applicationContext, model = localModel)
                .onSuccess { result ->
                    instance = result
                    printLog("Welcome to Yazio notificator :)\nInitialized with model $GemmaModelName")
                    _uiState.update { it.copy(isModelReady = true) }
                }.onFailure { error ->
                    printLog("Failed to initialize model:\n${error.message}")
                }
        }
    }

    private suspend fun doExtraProcessing(
        instance: LlmModelInstance,
        context: NotificationContext,
    ): NotificationResult {
        val agent = GemmaAgent(instance = instance)
        agent.initializeWithTools(toolRegistry = toolRegistry)
        return NotificationGenerator(agent = agent).generate(context)
    }

    private fun printLog(log: String) {
        _uiState.update { it.copy(textLog = log) }
        Log.d("NotificationViewModel", log)
    }
}

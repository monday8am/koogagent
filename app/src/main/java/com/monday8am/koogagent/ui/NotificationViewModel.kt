package com.monday8am.koogagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.agent.LocalLLModel
import com.monday8am.agent.MealType
import com.monday8am.agent.MotivationLevel
import com.monday8am.agent.NotificationContext
import com.monday8am.agent.NotificationGenerator
import com.monday8am.agent.WeatherCondition
import com.monday8am.koogagent.local.GemmaAgent
import com.monday8am.koogagent.local.LlmModelInstance
import com.monday8am.koogagent.local.LocalInferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

val notificationContext =
    NotificationContext(
        mealType = MealType.WATER,
        motivationLevel = MotivationLevel.HIGH,
        weather = WeatherCondition.SUNNY,
        alreadyLogged = true,
        userLocale = "en-US",
        country = "ES",
    )

private const val GemmaModelName = "gemma3-1b-it-int4.litertlm"
private const val GemmaModelFile = "/data/local/tmp/slm/$GemmaModelName"

class NotificationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow("Initializing!")
    val uiState = _uiState.asStateFlow()

    private var instance: LlmModelInstance? = null
    private val localModel =
        LocalLLModel(
            path = GemmaModelFile,
            temperature = 0.8f,
        )

    init {
        viewModelScope.launch {
            LocalInferenceUtils
                .initialize(context = application.applicationContext, model = localModel)
                .onSuccess { result ->
                    instance = result
                    _uiState.update { "Welcome to Yazio notificator :)\nInitialized with model $GemmaModelName" }
                }.onFailure { error ->
                    _uiState.update { "Failed to initialize model:\n${error.message}" }
                }
        }
    }

    fun processAndShowNotification() {
        _uiState.update { "Prompting with context:\n ${notificationContext.formatted}" }
        instance?.let { instance ->
            viewModelScope.launch {
                val processedPayload = doExtraProcessing(instance = instance, context = notificationContext)
                with(processedPayload) {
                    if (isFallback) {
                        _uiState.update { "Failed with error: ${errorMessage}\nFallback message:\n $formatted" }
                    } else {
                        _uiState.update { "Notification:\n $formatted" }
                        NotificationUtils.showNotification(getApplication(), this)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        instance?.let {
            LocalInferenceUtils.close(instance = it)
        }
    }

    private suspend fun doExtraProcessing(
        instance: LlmModelInstance,
        context: NotificationContext,
    ) = NotificationGenerator(
        agent = GemmaAgent(instance = instance),
    ).generate(context)
}

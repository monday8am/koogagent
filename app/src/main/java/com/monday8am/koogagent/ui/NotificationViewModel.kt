package com.monday8am.koogagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.agent.LocalLLModel
import com.monday8am.agent.MealType
import com.monday8am.agent.MotivationLevel
import com.monday8am.agent.NotificationContext
import com.monday8am.agent.NotificationGenerator
import com.monday8am.agent.NotificationResult
import com.monday8am.agent.WeatherCondition
import com.monday8am.koogagent.local.GemmaAgent
import com.monday8am.koogagent.local.LlmModelInstance
import com.monday8am.koogagent.local.LocalInferenceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val notificationContext =
    NotificationContext(
        mealType = MealType.WATER,
        motivationLevel = MotivationLevel.HIGH,
        weather = WeatherCondition.SUNNY,
        alreadyLogged = true,
        userLocale = "en-US",
        country = "ES",
    )

class NotificationViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow("Initializing!")
    val uiState = _uiState.asStateFlow()

    private var instance: LlmModelInstance? = null
    private val localModel =
        LocalLLModel(
            path = "",
            temperature = 0.8f,
        )

    init {
        LocalInferenceUtils.initialize(context = application.applicationContext, model = localModel)
            .onSuccess { result ->
                instance = result
                _uiState.update {
                    """Welcome to the KoogAgent!
               Initialized with model Gemma
            """.trimIndent()
                }
            }
            .onFailure { error ->
                _uiState.update { "Failed to initialize model: ${error.message}" }
            }
    }

    override fun onCleared() {
        super.onCleared()
        instance?.let {
            LocalInferenceUtils.close(instance = it)
        }
    }

    fun prompt() {
        _uiState.update { "Context: $notificationContext" }
        instance?.let { instance ->
            viewModelScope.launch {
                val message =
                    NotificationGenerator(
                        agent = GemmaAgent(instance = instance),
                    ).generate(notificationContext)

                if (message.isFallback) {
                    _uiState.update {
                        """
                            Failed with error: ${message.errorMessage}
                            Fallback message: ${message.formatted}
                        """.trimIndent()
                    }
                } else {
                    _uiState.update { "Generated Notification: ${message.formatted}" }
                }
            }
        }
    }

    fun processAndShowNotification() {
        viewModelScope.launch {
            val processedPayload = doExtraProcessing()
            NotificationUtils.showNotification(getApplication(), processedPayload)
        }
    }

    private fun doExtraProcessing(): NotificationResult {
        // Example: Add a prefix to the message
        return NotificationResult(
            title = "Processed",
            body = "This is a processed notification message",
            language = "en",
            confidence = 0.9,
            isFallback = false,
        )
    }
}

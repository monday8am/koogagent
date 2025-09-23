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
import com.monday8am.agent.OllamaAgent
import com.monday8am.agent.WeatherCondition
import com.monday8am.koogagent.local.LlmModelInstance
import com.monday8am.koogagent.local.LocalInferenceUtils
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
    private val instance: LlmModelInstance?
    private val localModel =
        LocalLLModel(
            path = "",
            temperature = 0.8f,
        )

    init {
        instance = LocalInferenceUtils.initialize(context = application.applicationContext, model = localModel).getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        instance?.let {
            LocalInferenceUtils.close(instance = it)
        }
    }

    fun prompt() {
        viewModelScope.launch {
            val message =
                NotificationGenerator(
                    agent = OllamaAgent(),
                ).generate(notificationContext)
            println(message)
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

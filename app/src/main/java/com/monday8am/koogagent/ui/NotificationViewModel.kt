package com.monday8am.koogagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.agent.LocalLLModel
import com.monday8am.agent.NotificationResult
import com.monday8am.koogagent.local.LocalInferenceUtils
import com.monday8am.koogagent.local.LlmModelInstance
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val instance: LlmModelInstance?
    private val localModel = LocalLLModel(
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

    fun prompt(message: String) {
        viewModelScope.launch {
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

package com.monday8am.koogagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.mcpserver.NotificationResult
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

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

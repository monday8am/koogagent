package com.monday8am.koogagent.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    fun processAndShowNotification(payload: NotificationPayload) {
        viewModelScope.launch {
            // Extra processing step (e.g., analytics, parsing, etc.)
            val processedPayload = doExtraProcessing(payload)
            NotificationHandler.showNotification(getApplication(), processedPayload)
        }
    }

    private fun doExtraProcessing(payload: NotificationPayload): NotificationPayload {
        // Example: Add a prefix to the message
        return payload.copy(
            message = "[Processed] ${payload.message}"
        )
    }
}

object NotificationHandler {
    private const val CHANNEL_ID = "main_channel"
    private const val CHANNEL_NAME = "Main Notifications"

    fun showNotification(context: Context, payload: NotificationPayload) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

data class NotificationPayload(
    val title: String,
    val message: String,
    val extraData: String? = null
)

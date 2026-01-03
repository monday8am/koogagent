package com.monday8am.presentation.notifications

sealed interface LogMessage {
    data object Initializing : LogMessage

    data object InitializingModel : LogMessage

    data class PromptingWithContext(val contextFormatted: String) : LogMessage

    data class NotificationGenerated(val notificationFormatted: String) : LogMessage

    data class WelcomeModelReady(val modelName: String) : LogMessage

    data class Error(val message: String) : LogMessage
}

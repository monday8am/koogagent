package com.monday8am.presentation.notifications

import com.monday8am.presentation.testing.TestResultFrame

sealed interface LogMessage {
    data object Initializing : LogMessage

    data object InitializingModel : LogMessage

    data class Downloading(
        val progress: Float,
    ) : LogMessage

    data object DownloadComplete : LogMessage

    data object DownloadFinished : LogMessage

    data class PromptingWithContext(
        val contextFormatted: String,
    ) : LogMessage

    data class NotificationGenerated(
        val notificationFormatted: String,
    ) : LogMessage

    data class WelcomeModelReady(
        val modelName: String,
    ) : LogMessage

    data object WelcomeDownloadRequired : LogMessage

    data class Error(
        val message: String,
    ) : LogMessage

    data class TestResultMessage(
        val content: TestResultFrame,
    ) : LogMessage

    data object InitTests : LogMessage
}

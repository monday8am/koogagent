package com.monday8am.koogagent.ui

import com.monday8am.presentation.notifications.LogMessage

fun LogMessage.toDisplayString(): String =
    when (this) {
        is LogMessage.Initializing -> "Initializing!"
        is LogMessage.InitializingModel -> "Initializing model for notification..."
        is LogMessage.Downloading -> "Downloading: ${"%.1f".format(progress * 100)}%"
        is LogMessage.DownloadComplete -> "Download complete! Model is ready."
        is LogMessage.DownloadFinished -> "Download finished."
        is LogMessage.PromptingWithContext -> "Prompting with context:\n $contextFormatted"
        is LogMessage.NotificationGenerated -> "Notification:\n $notificationFormatted"
        is LogMessage.WelcomeModelReady -> "Welcome to Yazio notificator :)\nInitialized with model $modelName"
        is LogMessage.WelcomeDownloadRequired ->
            "Welcome!\nPress download model button. It's a one time operation and it will take close to 4 minutes."
        is LogMessage.Error -> "An error occurred: $message"
    }

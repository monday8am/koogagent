package com.monday8am.koogagent.ui

import com.monday8am.presentation.notifications.LogMessage

fun LogMessage.toDisplayString(): String = when (this) {
    is LogMessage.Initializing -> {
        "Initializing!"
    }

    is LogMessage.InitializingModel -> {
        "Initializing model for notification..."
    }

    is LogMessage.PromptingWithContext -> {
        "Prompting with context:\n $contextFormatted"
    }

    is LogMessage.NotificationGenerated -> {
        "Notification:\n $notificationFormatted"
    }

    is LogMessage.WelcomeModelReady -> {
        "Welcome to Yazio notificator :)\nInitialized with model $modelName"
    }

    is LogMessage.Error -> {
        "An error occurred: $message"
    }
}

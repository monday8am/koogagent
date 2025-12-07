package com.monday8am.koogagent.ui

import com.monday8am.presentation.notifications.LogMessage
import com.monday8am.presentation.testing.TestResultFrame

fun LogMessage.toDisplayString(): String =
    when (this) {
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

        is LogMessage.InitTests -> {
            "=== GEMMA TOOL CALLING TESTS ===\nModel: Gemma 3n-1b-it-int4\n" +
                "Protocol: Simplified JSON (single tool, no parameters)"
        }

        is LogMessage.TestResultMessage -> {
            when (content) {
                is TestResultFrame.Content -> {
                    "Content: ${(content as TestResultFrame.Content).accumulator}"
                }

                is TestResultFrame.Tool -> {
                    "Tool: ${(content as TestResultFrame.Tool).accumulator}"
                }

                is TestResultFrame.Validation -> {
                    "Validation: ${(content as TestResultFrame.Validation).result}"
                }

                is TestResultFrame.Thinking -> {
                    "Thinking: ${(content as TestResultFrame.Thinking).accumulator}"
                }
            }
        }
    }

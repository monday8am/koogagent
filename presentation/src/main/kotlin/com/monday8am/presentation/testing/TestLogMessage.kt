package com.monday8am.presentation.testing

sealed interface TestLogMessage {
    data object Initializing : TestLogMessage

    data object InitializingModel : TestLogMessage

    data object ModelReady : TestLogMessage

    data object RunningTests : TestLogMessage

    data class TestProgress(
        val message: String,
    ) : TestLogMessage

    data class TestCompleted(
        val results: String,
    ) : TestLogMessage

    data class Error(
        val message: String,
    ) : TestLogMessage
}

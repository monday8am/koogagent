package com.monday8am.agent

interface NotificationAgent {
    suspend fun generateMessage(
        systemPrompt: String,
        userPrompt: String,
    ): String

    fun initializeWithTool(tool: WeatherTool)
}

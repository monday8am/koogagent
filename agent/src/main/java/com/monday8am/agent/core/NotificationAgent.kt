package com.monday8am.agent.core

import ai.koog.agents.core.tools.ToolRegistry

interface NotificationAgent {
    suspend fun generateMessage(
        systemPrompt: String,
        userPrompt: String,
    ): String

    fun initializeWithTools(toolRegistry: ToolRegistry)
}

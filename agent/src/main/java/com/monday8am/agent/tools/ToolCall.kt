package com.monday8am.agent.tools

/** Represents a recorded tool execution. */
data class ToolCall(
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val args: Map<String, Any?> = emptyMap(),
)

package com.monday8am.agent.tools

/**
 * Represents a recorded tool execution.
 */
data class ToolCall(
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val args: Map<String, Any?> = emptyMap(),
)

/**
 * Singleton to trace tool execution for testing purposes.
 * This allows verifying if a tool was called by the inference engine,
 * even if we don't have access to the conversation history.
 */
object ToolTrace {
    private val _calls = mutableListOf<ToolCall>()

    /**
     * list of tool names called in order.
     */
    val calls: List<ToolCall>
        get() = synchronized(this) { _calls.toList() }

    /**
     * Log a tool execution.
     */
    fun log(toolCall: ToolCall) {
        synchronized(this) {
            _calls.add(toolCall)
        }
    }

    /**
     * Clear the trace history.
     */
    fun clear() {
        synchronized(this) {
            _calls.clear()
        }
    }
}

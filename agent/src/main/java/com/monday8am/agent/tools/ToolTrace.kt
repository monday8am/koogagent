package com.monday8am.agent.tools

/**
 * Singleton to trace tool execution for testing purposes.
 * This allows verifying if a tool was called by the inference engine,
 * even if we don't have access to the conversation history.
 */
object ToolTrace {
    private val _calls = mutableListOf<String>()

    /**
     * list of tool names called in order.
     */
    val calls: List<String>
        get() = synchronized(this) { _calls.toList() }

    /**
     * Log a tool execution.
     */
    fun log(toolName: String) {
        synchronized(this) {
            _calls.add(toolName)
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

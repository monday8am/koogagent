package com.monday8am.agent.tools

/**
 * Interface for tool handlers that can be used in tests. Tracks tool calls and provides mock
 * responses.
 */
interface ToolHandler {
    /** Returns a list of all recorded tool calls. */
    val calls: List<ToolCall>

    /** Clears all recorded tool calls. */
    fun clear()
}

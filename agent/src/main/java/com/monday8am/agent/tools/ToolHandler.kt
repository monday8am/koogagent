package com.monday8am.agent.tools

/**
 * Interface for tool handlers that can be used in tests. Tracks tool calls and provides mock
 * responses.
 */
interface ToolHandler {
    val calls: List<ToolCall>
}

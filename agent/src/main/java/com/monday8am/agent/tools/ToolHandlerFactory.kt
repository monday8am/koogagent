package com.monday8am.agent.tools

import com.monday8am.koogagent.data.testing.ToolSpecification

/**
 * Factory for creating ToolHandler instances without exposing the concrete implementation. This
 * allows other modules to create tool handlers without needing direct access to OpenApiTool.
 */
object ToolHandlerFactory {
    fun createOpenApiHandler(toolSpec: ToolSpecification, mockResponse: String): ToolHandler {
        return OpenApiToolHandler(toolSpec, mockResponse)
    }
}

package com.monday8am.edgelab.agent.tools

import com.monday8am.edgelab.data.testing.ToolSpecification

/**
 * Factory for creating ToolHandler instances without exposing the concrete implementation. This
 * allows other modules to create tool handlers without needing direct access to OpenApiTool.
 */
object ToolHandlerFactory {
    fun createOpenApiHandler(toolSpec: ToolSpecification, mockResponse: String): ToolHandler {
        return OpenApiToolHandler(toolSpec, mockResponse)
    }
}

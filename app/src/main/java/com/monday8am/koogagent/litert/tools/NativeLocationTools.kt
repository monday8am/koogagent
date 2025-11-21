package com.monday8am.koogagent.litert.tools

import com.google.ai.edge.litertlm.Tool

/**
 * Native LiteRT-LM tool set for location operations.
 * Uses @Tool annotations for automatic schema generation.
 *
 * These tools are passed directly to ConversationConfig and handled by
 * LiteRT-LM's native tool calling system (Qwen3DataProcessor for Qwen models).
 */
class NativeLocationTools {
    /**
     * Gets the user's current location coordinates.
     * Returns hardcoded Madrid coordinates for testing.
     */
    @Tool(description = "Get the user's current location coordinates")
    fun getLocation(): Map<String, Double> {
        return mapOf(
            "latitude" to 40.4168,
            "longitude" to -3.7038
        )
    }
}

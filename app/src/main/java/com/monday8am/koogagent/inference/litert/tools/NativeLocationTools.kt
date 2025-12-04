package com.monday8am.koogagent.inference.litert.tools

import android.util.Log
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
    @Tool(description = "No arguments required. Get the user's current location in latitude and longitude format")
    fun get_location(): String {
        val result = """{"latitude": 40.4168, "longitude": -3.7038}"""
        Log.e("NativeLocationTools", "🔧 Returning: $result")
        return result
    }
}

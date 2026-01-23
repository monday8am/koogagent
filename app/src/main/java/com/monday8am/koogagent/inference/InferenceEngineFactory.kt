package com.monday8am.koogagent.inference

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.inference.litertlm.LiteRTLmInferenceEngineImpl

/**
 * Factory helper for creating LiteRT inference engine.
 * Called once at app startup to create the engine for the selected model.
 */
object InferenceEngineFactory {
    fun create(tools: List<Any> = emptyList()): LocalInferenceEngine {
        return LiteRTLmInferenceEngineImpl(tools = tools)
    }
}

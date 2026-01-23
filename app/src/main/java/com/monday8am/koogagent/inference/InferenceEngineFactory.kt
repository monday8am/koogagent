package com.monday8am.koogagent.inference

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.inference.litertlm.LiteRTLmInferenceEngineImpl

/**
 * Factory helper for creating LiteRT inference engine.
 * Called once at app startup to create the engine for the selected model.
 * Tools are now configured per-test via setToolsAndResetConversation().
 */
object InferenceEngineFactory {
    fun create(): LocalInferenceEngine {
        return LiteRTLmInferenceEngineImpl()
    }
}

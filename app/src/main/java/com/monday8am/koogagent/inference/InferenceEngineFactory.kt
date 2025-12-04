package com.monday8am.koogagent.inference

import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.InferenceLibrary
import com.monday8am.koogagent.inference.litert.LocalInferenceEngineImpl

/**
 * Factory helper for creating appropriate inference engine based on inference library type.
 * Called once at app startup to create the engine for the selected model.
 */
object InferenceEngineFactory {
    fun create(
        inferenceLibrary: InferenceLibrary,
        liteRtTools: List<Any> = emptyList(),
    ): LocalInferenceEngine =
        when (inferenceLibrary) {
            InferenceLibrary.LITERT -> {
                LocalInferenceEngineImpl(tools = liteRtTools)
            }
            InferenceLibrary.MEDIAPIPE -> {
                throw UnsupportedOperationException("MediaPipe not yet implemented")
            }
        }
}

package com.monday8am.koogagent.inference

import android.content.Context
import com.google.ai.edge.localagents.core.proto.Tool
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.data.InferenceLibrary
import com.monday8am.koogagent.inference.litertlm.LiteRTLmInferenceEngineImpl
import com.monday8am.koogagent.inference.mediapipe.MediaPipeInferenceEngineImpl

/**
 * Factory helper for creating appropriate inference engine based on inference library type. Called
 * once at app startup to create the engine for the selected model.
 */
object InferenceEngineFactory {
    fun create(
        context: Context,
        inferenceLibrary: InferenceLibrary,
        liteRtTools: List<Any> = emptyList(),
        mediaPipeTools: List<Tool> = emptyList(),
    ): LocalInferenceEngine =
        when (inferenceLibrary) {
            InferenceLibrary.LITERT -> {
                LiteRTLmInferenceEngineImpl(tools = liteRtTools)
            }

            InferenceLibrary.MEDIAPIPE -> {
                MediaPipeInferenceEngineImpl(context = context, tools = mediaPipeTools)
            }
        }
}

package com.monday8am.agent.core

import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.flow.Flow

interface LocalInferenceEngine {
    suspend fun initialize(modelConfig: ModelConfiguration, modelPath: String): Result<Unit>

    suspend fun prompt(prompt: String): Result<String>

    fun promptStreaming(prompt: String): Flow<String>

    fun initializeAsFlow(
        modelConfig: ModelConfiguration,
        modelPath: String,
    ): Flow<LocalInferenceEngine>

    fun setToolsAndResetConversation(tools: List<Any>): Result<Unit>

    fun closeSession(): Result<Unit>
}

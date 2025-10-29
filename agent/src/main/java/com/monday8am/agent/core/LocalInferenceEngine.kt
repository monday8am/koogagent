package com.monday8am.agent.core

import kotlinx.coroutines.flow.Flow

interface LocalInferenceEngine {
    suspend fun initialize(model: LocalLLModel): Result<Unit>
    suspend fun prompt(prompt: String): Result<String>
    fun initializeAsFlow(model: LocalLLModel): Flow<LocalInferenceEngine>
    fun resetConversation(): Result<Unit>
    fun closeSession(): Result<Unit>
}

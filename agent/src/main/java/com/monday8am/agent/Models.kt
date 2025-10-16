package com.monday8am.agent

const val DEFAULT_MAX_TOKEN = 1024
const val DEFAULT_TOPK = 40
const val DEFAULT_TOPP = 0.9f
const val DEFAULT_TEMPERATURE = 0.5f

data class LocalLLModel(
    val path: String,
    val maxToken: Int = DEFAULT_MAX_TOKEN,
    val topK: Int = DEFAULT_TOPK,
    val topP: Float = DEFAULT_TOPP,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val shouldEnableImage: Boolean = false,
    val shouldEnableAudio: Boolean = false,
    val isGPUAccelerated: Boolean = true,
)

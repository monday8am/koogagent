package com.monday8am.koogagent.data

/**
 * Catalog of predefined model configurations.
 * This serves as the "source of truth" for available models in the app.
 */
object ModelCatalog {

    val QWEN3_0_6B =
        ModelConfiguration(
            id = "qwen3-0.6b-litert-q8-4k",
            displayName = "Qwen3 0.6B (LiteRT, 4K)",
            modelFamily = "qwen3",
            parameterCount = 0.6f,
            quantization = "int8",
            contextLength = 4096,
            downloadUrl = "https://github.com/monday8am/koogagent/releases/download/0.0.2/qwen3_0.6b_q8_ekv4096.zip",
            bundleFilename = "qwen3_0.6b_q8_ekv4096.litertlm",
            inferenceLibrary = InferenceLibrary.LITERT,
            hardwareAcceleration = HardwareBackend.CPU_ONLY,
            defaultTopK = 40,
            defaultTopP = 0.85f,
            defaultTemperature = 0.2f,
        )

    val GEMMA3_1B =
        ModelConfiguration(
            id = "gemma3-1b-litert-int4-4k",
            displayName = "Gemma 3 1B (LiteRT, 4K)",
            modelFamily = "gemma3",
            parameterCount = 1.0f,
            quantization = "int4",
            contextLength = 4096,
            downloadUrl = "https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip",
            bundleFilename = "gemma3-1b-it-int4.litertlm",
            inferenceLibrary = InferenceLibrary.LITERT,
            hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
            defaultTopK = 40,
            defaultTopP = 0.85f,
            defaultTemperature = 0.7f, // Gemma benefits from higher temp
        )

    val HAMMER2_1_5B =
        ModelConfiguration(
            id = "hammer2-0.5b-litert-q8-2k",
            displayName = "Hammer 2.1 0.5B (LiteRT, 2K)",
            modelFamily = "hammer2",
            parameterCount = 0.5f,
            quantization = "int8",
            contextLength = 2048,
            // TODO: Update with actual download URL when available
            downloadUrl = "https://github.com/monday8am/koogagent/releases/download/TODO/hammer2_0.5b_q8_ekv2048.zip",
            bundleFilename = "hammer2_0.5b_q8_ekv2048.litertlm",
            inferenceLibrary = InferenceLibrary.LITERT,
            hardwareAcceleration = HardwareBackend.CPU_ONLY,
            defaultTopK = 40,
            defaultTopP = 0.9f,
            defaultTemperature = 0.3f,
        )

    val ALL_MODELS =
        listOf(
            QWEN3_0_6B, // Default (best balance)
            GEMMA3_1B, // High-end
            HAMMER2_1_5B, // Low-end
        )

    val DEFAULT = QWEN3_0_6B

    fun findById(id: String): ModelConfiguration? = ALL_MODELS.find { it.id == id }
}

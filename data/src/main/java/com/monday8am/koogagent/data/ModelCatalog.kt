package com.monday8am.koogagent.data

/**
 * Catalog of predefined model configurations.
 * This serves as the "source of truth" for available models in the app.
 */
object ModelCatalog {

    /**
     * Qwen3 0.6B model with LiteRT-LM (4K context, int8 quantization).
     * Supports native tool calling via Qwen3DataProcessor.
     * CPU-only, best for mid-range devices.
     */
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

    /**
     * Gemma 3 1B model with LiteRT-LM (4K context, int4 quantization).
     * Requires custom tool calling protocols (REACT/HERMES).
     * GPU-accelerated, best for high-end devices.
     */
    val GEMMA3_1B_LITERT_4K =
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

    /**
     * Hammer 2.1 0.5B model with LiteRT-LM (2K context, int8 quantization).
     * Smallest model, fastest inference.
     * CPU-only, best for low-end devices.
     */
    val HAMMER2_0_5B_LITERT_2K =
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

    // Future: MediaPipe variants
    // val QWEN3_0_6B_MEDIAPIPE_4K = ModelConfiguration(...)
    // val GEMMA3_1B_MEDIAPIPE_4K = ModelConfiguration(...)

    /**
     * All available models in the catalog.
     * Order determines UI display order.
     */
    val ALL_MODELS =
        listOf(
            QWEN3_0_6B, // Default (best balance)
            GEMMA3_1B_LITERT_4K, // High-end
            HAMMER2_0_5B_LITERT_2K, // Low-end
        )

    /**
     * Default model configuration.
     * Qwen3 chosen for: native tool calling, good context, mid-range performance.
     */
    val DEFAULT = QWEN3_0_6B

    /**
     * Find a model by its unique ID.
     */
    fun findById(id: String): ModelConfiguration? = ALL_MODELS.find { it.id == id }
}

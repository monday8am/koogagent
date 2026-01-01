package com.monday8am.koogagent.data

/**
 * Catalog of predefined model configurations.
 * This serves as the "source of truth" for available models in the app.
 */
object ModelCatalog {
    val QWEN3_0_6B =
        ModelConfiguration(
            displayName = "Qwen3 0.6B (LiteRT, 4K)",
            modelFamily = "qwen3",
            parameterCount = 0.6f,
            quantization = "int8",
            contextLength = 4096,
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            bundleFilename = "Qwen3-0.6B.litertlm",
            inferenceLibrary = InferenceLibrary.LITERT,
            hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
            defaultTopK = 40,
            defaultTopP = 0.85f,
            defaultTemperature = 0.2f,
            description = "Efficient bilingual model optimized for on-device inference",
            fileSizeBytes = 642_000_000L, // ~642 MB
            huggingFaceUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
        )

    val QWEN2_5_1_5B =
        ModelConfiguration(
            displayName = "Qwen2.5 1.5B (LiteRT, 4K)",
            modelFamily = "qwen2.5",
            parameterCount = 0.6f,
            quantization = "int8",
            contextLength = 4096,
            downloadUrl =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            bundleFilename = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            inferenceLibrary = InferenceLibrary.LITERT,
            hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
            defaultTopK = 40,
            defaultTopP = 0.85f,
            defaultTemperature = 0.2f,
            description = "Improved Qwen model with enhanced multilingual capabilities",
            fileSizeBytes = 1_600_000_000L, // ~1.6 GB
            huggingFaceUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
        )

    val GEMMA3_1B =
        ModelConfiguration(
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
            // Gemma benefits from higher temp
            defaultTemperature = 0.7f,
            description = "Google's Gemma 3 model optimized for mobile devices",
            fileSizeBytes = 535_000_000L, // ~535 MB
            huggingFaceUrl = "https://huggingface.co/google/gemma-3-1b-it",
        )

    val HAMMER2_1_0_5B =
        ModelConfiguration(
            displayName = "Hammer 2.1 0.5B (Mediapipe, 2K)",
            modelFamily = "hammer2",
            parameterCount = 0.5f,
            quantization = "int8",
            contextLength = 4096,
            // TODO: Update with actual download URL when available
            downloadUrl = "https://github.com/monday8am/koogagent/releases/download/TODO/hammer2_0.5b_q8_ekv2048.zip",
            bundleFilename = "hammer2.1_0.5b_q8_ekv4096.task",
            inferenceLibrary = InferenceLibrary.MEDIAPIPE,
            hardwareAcceleration = HardwareBackend.CPU_ONLY,
            defaultTopK = 40,
            defaultTopP = 0.9f,
            defaultTemperature = 0.7f,
            defaultMaxOutputTokens = 4096,
            description = "Compact model with tool calling support",
            fileSizeBytes = 535_000_000L, // ~535 MB
            huggingFaceUrl = "https://huggingface.co/google/hammer-2.1-0.5b",
        )

    val SMOLLM_135M =
        ModelConfiguration(
            displayName = "SmolLM 135M (Mediapipe, 2K)",
            modelFamily = "smollm",
            parameterCount = 0.5f,
            quantization = "int8",
            contextLength = 1280,
            // TODO: Update with actual download URL when available
            downloadUrl =
            "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/" +
                "SmolLM-135M-Instruct_multi-prefill-seq_f32_ekv1280.task",
            bundleFilename = "SmolLM-135M-Instruct_multi-prefill-seq_f32_ekv1280.task",
            inferenceLibrary = InferenceLibrary.MEDIAPIPE,
            hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
            defaultTopK = 40,
            defaultTopP = 0.9f,
            defaultTemperature = 0.7f,
            defaultMaxOutputTokens = 1280,
            description = "Ultra-compact model for resource-constrained devices",
            fileSizeBytes = 540_000_000L, // ~540 MB
            huggingFaceUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-135M-Instruct",
        )

    val ALL_MODELS =
        listOf(
            QWEN3_0_6B,
            GEMMA3_1B,
            QWEN2_5_1_5B,
            HAMMER2_1_0_5B,
            SMOLLM_135M,
        )

    val DEFAULT = GEMMA3_1B

    fun findById(id: String): ModelConfiguration? = ALL_MODELS.find { it.modelId == id }
}

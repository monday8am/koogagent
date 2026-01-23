package com.monday8am.koogagent.data

/**
 * Catalog of predefined model configurations. This serves as the "source of truth" for available
 * models in the app.
 */
object ModelCatalog {
    val QWEN3_0_6B =
        ModelConfiguration(
            displayName = "Qwen3 0.6B (LiteRT, 4K)",
            modelFamily = "qwen3",
            parameterCount = 0.6f,
            quantization = "int8",
            contextLength = 4096,
            downloadUrl =
                "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            bundleFilename = "Qwen3-0.6B.litertlm",
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
            downloadUrl =
                "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
            bundleFilename = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
            hardwareAcceleration = HardwareBackend.GPU_SUPPORTED,
            defaultTopK = 40,
            defaultTopP = 0.85f,
            // Gemma benefits from higher temp
            defaultTemperature = 0.7f,
            description = "Google's Gemma 3 model optimized for mobile devices",
            fileSizeBytes = 535_000_000L, // ~535 MB
            huggingFaceUrl = "https://huggingface.co/google/gemma-3-1b-it",
        )

    val ALL_MODELS = listOf(QWEN3_0_6B, GEMMA3_1B, QWEN2_5_1_5B)

    val DEFAULT = GEMMA3_1B

    fun findById(id: String): ModelConfiguration? = ALL_MODELS.find { it.modelId == id }
}

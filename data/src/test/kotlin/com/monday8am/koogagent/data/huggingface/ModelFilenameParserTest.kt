package com.monday8am.koogagent.data.huggingface

import com.monday8am.koogagent.data.InferenceLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelFilenameParserTest {

    @Test
    fun `parse simple litertlm filename`() {
        val result = ModelFilenameParser.parse("Qwen3-0.6B.litertlm", "litert-community/Qwen3-0.6B")

        assertNotNull(result)
        assertEquals("qwen3", result.modelFamily)
        assertEquals(0.6f, result.parameterCount)
        assertEquals(InferenceLibrary.LITERT, result.inferenceLibrary)
    }

    @Test
    fun `parse filename with context and quantization`() {
        val result =
            ModelFilenameParser.parse(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                "litert-community/Gemma3-1B-IT",
            )

        assertNotNull(result)
        assertEquals("gemma3", result!!.modelFamily)
        assertEquals(1.0f, result.parameterCount)
        assertEquals("q4", result.quantization)
        assertEquals(4096, result.contextLength)
        assertEquals(InferenceLibrary.LITERT, result.inferenceLibrary)
    }

    @Test
    fun `parse task file for MediaPipe`() {
        val result =
            ModelFilenameParser.parse(
                "SmolLM-135M-Instruct_multi-prefill-seq_f32_ekv1280.task",
                "litert-community/SmolLM-135M-Instruct",
            )

        assertNotNull(result)
        assertEquals("smollm", result.modelFamily)
        assertEquals(0.135f, result.parameterCount)
        assertEquals("f32", result.quantization)
        assertEquals(1280, result.contextLength)
        assertEquals(InferenceLibrary.MEDIAPIPE, result.inferenceLibrary)
    }

    @Test
    fun `parse int4 quantization`() {
        val result =
            ModelFilenameParser.parse("gemma3-1b-it-int4.litertlm", "litert-community/Gemma3-1B-IT")

        assertNotNull(result)
        assertEquals("gemma3", result.modelFamily)
        assertEquals("int4", result.quantization)
    }

    @Test
    fun `parse int8 quantization`() {
        val result =
            ModelFilenameParser.parse(
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                "litert-community/Qwen2.5-1.5B-Instruct",
            )

        assertNotNull(result)
        assertEquals("qwen2.5", result.modelFamily)
        assertEquals(1.5f, result.parameterCount)
        assertEquals("q8", result.quantization)
    }

    @Test
    fun `skip device-specific Snapdragon files`() {
        val result =
            ModelFilenameParser.parse(
                "Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm",
                "litert-community/Gemma3-1B-IT",
            )

        assertNull(result)
    }

    @Test
    fun `skip device-specific MediaTek files`() {
        val result =
            ModelFilenameParser.parse(
                "Gemma3-1B-IT_q4_ekv1280_mt6989.litertlm",
                "litert-community/Gemma3-1B-IT",
            )

        assertNull(result)
    }

    @Test
    fun `skip non-model files`() {
        assertNull(ModelFilenameParser.parse("README.md", "litert-community/model"))
        assertNull(ModelFilenameParser.parse(".gitattributes", "litert-community/model"))
        assertNull(ModelFilenameParser.parse("tokenizer.model", "litert-community/model"))
        assertNull(ModelFilenameParser.parse("notebook.ipynb", "litert-community/model"))
    }

    @Test
    fun `parse generates display name`() {
        val result =
            ModelFilenameParser.parse(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                "litert-community/Gemma3-1B-IT",
            )

        assertNotNull(result)
        // Display name should contain family and params
        val displayName = result!!.displayName
        assert(displayName.contains("Gemma3", ignoreCase = true)) {
            "Expected 'Gemma3' in '$displayName'"
        }
        assert(displayName.contains("1B", ignoreCase = true)) { "Expected '1B' in '$displayName'" }
    }

    @Test
    fun `extract family from various model IDs`() {
        assertEquals(
            "qwen3",
            ModelFilenameParser.parse("model.litertlm", "litert-community/Qwen3-0.6B")?.modelFamily,
        )
        assertEquals(
            "qwen2.5",
            ModelFilenameParser.parse("model.litertlm", "litert-community/Qwen2.5-1.5B-Instruct")
                ?.modelFamily,
        )
        assertEquals(
            "gemma3",
            ModelFilenameParser.parse("model.litertlm", "litert-community/Gemma3-1B-IT")
                ?.modelFamily,
        )
        assertEquals(
            "smollm",
            ModelFilenameParser.parse("model.litertlm", "litert-community/SmolLM-135M-Instruct")
                ?.modelFamily,
        )
        assertEquals(
            "tinyllama",
            ModelFilenameParser.parse("model.litertlm", "litert-community/TinyLlama-1.1B-Chat-v1.0")
                ?.modelFamily,
        )
        assertEquals(
            "phi",
            ModelFilenameParser.parse("model.litertlm", "litert-community/Phi-4-mini-instruct")
                ?.modelFamily,
        )
        assertEquals(
            "deepseek",
            ModelFilenameParser.parse(
                    "model.litertlm",
                    "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
                )
                ?.modelFamily,
        )
    }

    @Test
    fun `parse default context when not specified`() {
        val result = ModelFilenameParser.parse("Qwen3-0.6B.litertlm", "litert-community/Qwen3-0.6B")

        assertNotNull(result)
        // When context is not specified in filename, it should be null
        assertNull(result!!.contextLength)
    }

    @Test
    fun `parse 1280 context length`() {
        val result =
            ModelFilenameParser.parse("model_q8_ekv1280.litertlm", "litert-community/Model")

        assertNotNull(result)
        assertEquals(1280, result!!.contextLength)
    }
}

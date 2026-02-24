package com.monday8am.edgelab.data.huggingface

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
    }

    @Test
    fun `parse filename with context and quantization`() {
        val result =
            ModelFilenameParser.parse(
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                "litert-community/Gemma3-1B-IT",
            )

        assertNotNull(result)
        assertEquals("gemma3", result.modelFamily)
        assertEquals(1.0f, result.parameterCount)
        assertEquals("q4", result.quantization)
        assertEquals(4096, result.contextLength)
    }

    @Test
    fun `skip task files`() {
        val result =
            ModelFilenameParser.parse(
                "SmolLM-135M-Instruct_multi-prefill-seq_f32_ekv1280.task",
                "litert-community/SmolLM-135M-Instruct",
            )

        assertNull(result)
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
        val displayName = result.displayName
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
        assertNull(result.contextLength)
    }

    @Test
    fun `parse 1280 context length`() {
        val result =
            ModelFilenameParser.parse("model_q8_ekv1280.litertlm", "litert-community/Model")

        assertNotNull(result)
        assertEquals(1280, result.contextLength)
    }

    @Test
    fun `parse million parameter count`() {
        val result = ModelFilenameParser.parse("Model-500M.litertlm", "litert-community/Model-500M")

        assertNotNull(result)
        assertEquals(0.5f, result.parameterCount)
    }

    @Test
    fun `parse various quantization formats`() {
        val fp16 = ModelFilenameParser.parse("model_fp16.litertlm", "org/model")
        assertEquals("fp16", fp16?.quantization)

        val f16 = ModelFilenameParser.parse("model_f16.litertlm", "org/model")
        assertEquals("f16", f16?.quantization)

        val unknown = ModelFilenameParser.parse("model_none.litertlm", "org/model")
        assertEquals("unknown", unknown?.quantization)
    }

    @Test
    fun `extract family fallback when no prefix matches`() {
        val result = ModelFilenameParser.parse("CustomModel-1B.litertlm", "user/CustomModel-1B")

        assertNotNull(result)
        assertEquals("custommodel", result.modelFamily)
    }

    @Test
    fun `parse mixed case and multiple dots`() {
        val result =
            ModelFilenameParser.parse("My.Model.Name.V1.Q4.LITerTLm", "org/My.Model.Name.V1")

        assertNotNull(result)
        assertEquals("q4", result.quantization)
    }

    @Test
    fun `verify display name formatting`() {
        val gemma = ModelFilenameParser.parse("gemma-2b.litertlm", "google/gemma-2b")
        assertEquals("Gemma-2B", gemma?.displayName)

        val qwen = ModelFilenameParser.parse("qwen-0.5b.litertlm", "qwen/qwen-0.5b")
        assertEquals("Qwen-0.5B", qwen?.displayName)

        val smol = ModelFilenameParser.parse("smollm-135m.litertlm", "org/smollm-135m")
        assertEquals("Smollm-0.135B", smol?.displayName)
    }
}

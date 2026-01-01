package com.monday8am.koogagent.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelRepositoryTest {
    private val model1 = ModelCatalog.QWEN3_0_6B
    private val model2 = ModelCatalog.GEMMA3_1B

    private val fakeProvider = object : ModelCatalogProvider {
        override suspend fun fetchModels(): Result<List<ModelConfiguration>> =
            Result.success(listOf(model1, model2))
    }

    private val repository = ModelRepositoryImpl(fakeProvider)

    @Test
    fun `findById should return model when exists`() {
        repository.setModels(listOf(model1, model2))

        val result = repository.findById(model1.modelId)

        assertNotNull(result)
        assertEquals(model1.modelId, result.modelId)
    }

    @Test
    fun `findById should return null when not exists`() {
        repository.setModels(listOf(model1))

        val result = repository.findById("non-existent-id")

        assertNull(result)
    }

    @Test
    fun `getAllModels should return all stored models`() {
        repository.setModels(listOf(model1, model2))

        val result = repository.getAllModels()

        assertEquals(2, result.size)
    }

    @Test
    fun `setModels should replace existing models`() {
        repository.setModels(listOf(model1, model2))
        repository.setModels(listOf(model1))

        val result = repository.getAllModels()

        assertEquals(1, result.size)
    }

    @Test
    fun `getByFamily should filter by model family`() {
        repository.setModels(listOf(model1, model2))

        val result = repository.getByFamily("qwen3")

        assertTrue(result.all { it.modelFamily.equals("qwen3", ignoreCase = true) })
    }

    @Test
    fun `getByInferenceLibrary should filter by library`() {
        repository.setModels(listOf(model1, model2))

        val result = repository.getByInferenceLibrary(InferenceLibrary.LITERT)

        assertTrue(result.all { it.inferenceLibrary == InferenceLibrary.LITERT })
    }
}

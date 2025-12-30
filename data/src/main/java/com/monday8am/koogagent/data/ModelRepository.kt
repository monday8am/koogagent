package com.monday8am.koogagent.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelRepository {
    private val _models = MutableStateFlow<List<ModelConfiguration>>(emptyList())
    val models: StateFlow<List<ModelConfiguration>> = _models.asStateFlow()

    fun setModels(newModels: List<ModelConfiguration>) {
        _models.value = newModels.toList()
    }

    fun findById(modelId: String): ModelConfiguration? =
        _models.value.find { it.modelId == modelId }

    fun getAllModels(): List<ModelConfiguration> = _models.value

    fun getByFamily(family: String): List<ModelConfiguration> =
        _models.value.filter { it.modelFamily.equals(family, ignoreCase = true) }

    fun getByInferenceLibrary(library: InferenceLibrary): List<ModelConfiguration> =
        _models.value.filter { it.inferenceLibrary == library }
}

package com.monday8am.koogagent.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

sealed interface RepositoryState {
    data object Idle : RepositoryState
    data object Loading : RepositoryState
    data class Success(val models: List<ModelConfiguration>) : RepositoryState
    data class Error(val message: String) : RepositoryState
}

interface ModelRepository {
    val models: StateFlow<List<ModelConfiguration>>
    val loadingState: StateFlow<RepositoryState>
    suspend fun refreshModels()
    fun findById(modelId: String): ModelConfiguration?
    fun getAllModels(): List<ModelConfiguration>
    fun getByFamily(family: String): List<ModelConfiguration>
    fun getByInferenceLibrary(library: InferenceLibrary): List<ModelConfiguration>
    fun updateModel(modelId: String, updater: (ModelConfiguration) -> ModelConfiguration)
}

class ModelRepositoryImpl(
    private val modelCatalogProvider: ModelCatalogProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelRepository {
    private val _models = MutableStateFlow<List<ModelConfiguration>>(emptyList())
    override val models: StateFlow<List<ModelConfiguration>> = _models.asStateFlow()

    private val _loadingState = MutableStateFlow<RepositoryState>(RepositoryState.Idle)
    override val loadingState: StateFlow<RepositoryState> = _loadingState.asStateFlow()

    override suspend fun refreshModels() {
        _loadingState.value = RepositoryState.Loading
        withContext(ioDispatcher) {
            val result = modelCatalogProvider.fetchModels()
            if (result.isSuccess) {
                val newModels = result.getOrThrow()
                _models.value = newModels
                _loadingState.value = RepositoryState.Success(newModels)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _loadingState.value = RepositoryState.Error(error)
            }
        }
    }

    @VisibleForTesting
    fun setModels(models: List<ModelConfiguration>) {
        _models.value = models
    }

    override fun findById(modelId: String): ModelConfiguration? =
        _models.value.find { it.modelId == modelId }

    override fun getAllModels(): List<ModelConfiguration> = _models.value

    override fun getByFamily(family: String): List<ModelConfiguration> =
        _models.value.filter { it.modelFamily.equals(family, ignoreCase = true) }

    override fun getByInferenceLibrary(library: InferenceLibrary): List<ModelConfiguration> =
        _models.value.filter { it.inferenceLibrary == library }

    override fun updateModel(modelId: String, updater: (ModelConfiguration) -> ModelConfiguration) {
        val currentModels = _models.value
        val updatedModels = currentModels.map { model ->
            if (model.modelId == modelId) {
                updater(model)
            } else {
                model
            }
        }
        _models.value = updatedModels
    }
}

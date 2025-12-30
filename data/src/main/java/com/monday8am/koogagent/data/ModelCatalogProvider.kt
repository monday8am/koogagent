package com.monday8am.koogagent.data

/**
 * Interface for providing available model configurations.
 * Implementations can fetch from API or return hardcoded catalog.
 */
interface ModelCatalogProvider {
    /**
     * Fetches available models. Returns Result to handle failures gracefully.
     */
    suspend fun fetchModels(): Result<List<ModelConfiguration>>
}

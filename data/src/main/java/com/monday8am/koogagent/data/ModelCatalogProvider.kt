package com.monday8am.koogagent.data

/**
 * Interface for providing available model configurations. Implementations can fetch from API or
 * return hardcoded catalog.
 */
interface ModelCatalogProvider {
    suspend fun fetchModels(): Result<List<ModelConfiguration>>
}

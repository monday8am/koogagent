package com.monday8am.edgelab.data.model

import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing available model configurations. Implementations can fetch from API or
 * return hardcoded catalog.
 */
interface ModelCatalogProvider {
    fun getModels(): Flow<List<ModelConfiguration>>
}

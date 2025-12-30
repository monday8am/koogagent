package com.monday8am.koogagent.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration

/**
 * Wraps a primary provider with a fallback to hardcoded catalog.
 *
 * If the primary provider fails (e.g., network error), returns the fallback models.
 * This ensures the app always has some models available.
 */
class FallbackModelCatalogProvider(
    private val primary: ModelCatalogProvider,
    private val fallback: List<ModelConfiguration> = ModelCatalog.ALL_MODELS,
) : ModelCatalogProvider {

    private val logger = Logger.withTag("FallbackModelCatalogProvider")

    override suspend fun fetchModels(): Result<List<ModelConfiguration>> {
        val primaryResult = primary.fetchModels()

        return if (primaryResult.isSuccess && primaryResult.getOrNull()?.isNotEmpty() == true) {
            primaryResult
        } else {
            // Log the original failure for debugging
            primaryResult.exceptionOrNull()?.let { error ->
                logger.w { "Primary provider failed: ${error.message}" }
                logger.w { "Using fallback catalog with ${fallback.size} models" }
            }
            Result.success(fallback)
        }
    }
}

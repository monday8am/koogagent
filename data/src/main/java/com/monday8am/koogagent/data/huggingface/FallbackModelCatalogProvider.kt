package com.monday8am.koogagent.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Wraps a primary provider with a fallback to hardcoded catalog.
 *
 * If the primary provider fails (e.g., network error), returns the fallback models. This ensures
 * the app always has some models available.
 */
class FallbackModelCatalogProvider(
    private val primary: ModelCatalogProvider,
    private val fallback: List<ModelConfiguration> = ModelCatalog.ALL_MODELS,
) : ModelCatalogProvider {

    private val logger = Logger.withTag("FallbackModelCatalogProvider")

    override fun getModels(): Flow<List<ModelConfiguration>> =
        primary
            .getModels()
            .catch { error ->
                logger.w { "Primary provider failed: ${error.message}" }
                logger.w { "Using fallback catalog with ${fallback.size} models" }
                emit(fallback)
            }
            .onStart {
                // Determine if we need to emit fallback immediately if primary is slow or fails
                // fast
                // For now, reliance on catch is sufficient for failure
            }
            .map { models ->
                models.ifEmpty {
                    logger.w { "Primary provider returned empty list. Using fallback." }
                    fallback
                }
            }
}

package com.monday8am.koogagent.copilot

import android.content.Context
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.core.di.CoreDependencies
import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.koogagent.data.ModelRepository
import com.monday8am.koogagent.data.ModelRepositoryImpl
import com.monday8am.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple service locator for Cycling Copilot dependencies.
 * Centralizes dependency creation following edgelab pattern.
 */
object Dependencies {
    lateinit var appContext: Context

    val applicationScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Reuse core infrastructure
    val modelDownloadManager: ModelDownloadManager by lazy {
        CoreDependencies.createDownloadManager(appContext, authRepository)
    }

    val authRepository: AuthRepository by lazy {
        CoreDependencies.createAuthRepository(appContext, applicationScope)
    }

    val inferenceEngine: LocalInferenceEngine by lazy {
        CoreDependencies.createInferenceEngine()
    }

    // For onboarding, we need a simple model repository
    // We'll use a minimal catalog for copilot (just the models we need)
    val modelRepository: ModelRepository by lazy {
        // For now, use all models from the catalog
        // TODO: Create CopilotModelCatalogProvider with only FunctionGemma 2B + Gemma 2 550M
        ModelRepositoryImpl(
            object : com.monday8am.koogagent.data.ModelCatalogProvider {
                override fun getModels() = kotlinx.coroutines.flow.flowOf(
                    com.monday8am.koogagent.data.ModelCatalog.ALL_MODELS
                )
            }
        )
    }
}

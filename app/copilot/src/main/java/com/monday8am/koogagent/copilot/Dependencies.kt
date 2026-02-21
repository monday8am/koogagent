package com.monday8am.koogagent.copilot

import android.content.Context
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.core.di.CoreDependencies
import com.monday8am.koogagent.core.oauth.HuggingFaceOAuthManager
import com.monday8am.koogagent.data.auth.AuthRepository
import com.monday8am.koogagent.data.model.ModelRepository
import com.monday8am.koogagent.data.model.ModelRepositoryImpl
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

    // OAuth manager for HuggingFace authentication
    val oAuthManager: HuggingFaceOAuthManager by lazy {
        CoreDependencies.createOAuthManager(
            context = appContext,
            clientId = BuildConfig.HF_CLIENT_ID,
            redirectScheme = "copilot",
            activityClass = MainActivity::class.java,
        )
    }

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
            object : com.monday8am.koogagent.data.model.ModelCatalogProvider {
                override fun getModels() = kotlinx.coroutines.flow.flowOf(
                    com.monday8am.koogagent.data.model.ModelCatalog.ALL_MODELS
                )
            }
        )
    }
}

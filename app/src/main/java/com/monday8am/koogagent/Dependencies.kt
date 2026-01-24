package com.monday8am.koogagent

import android.content.Context
import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.koogagent.data.AuthRepositoryImpl
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelRepository
import com.monday8am.koogagent.data.ModelRepositoryImpl
import com.monday8am.koogagent.data.huggingface.FallbackModelCatalogProvider
import com.monday8am.koogagent.data.huggingface.HuggingFaceModelCatalogProvider
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.oauth.HuggingFaceOAuthManager
import com.monday8am.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple service locator for app dependencies. Centralizes dependency creation and avoids factory
 * boilerplate.
 */
object Dependencies {
    lateinit var appContext: Context

    val applicationScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    val modelDownloadManager: ModelDownloadManager by lazy {
        ModelDownloadManagerImpl(appContext, authRepository)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(appContext, applicationScope)
    }

    private var _oAuthManager: HuggingFaceOAuthManager? = null
    val oAuthManager: HuggingFaceOAuthManager
        get() {
            if (_oAuthManager == null) {
                _oAuthManager =
                    HuggingFaceOAuthManager(
                        context = appContext,
                        clientId = BuildConfig.HF_CLIENT_ID,
                    )
            }
            return _oAuthManager!!
        }

    /** Disposes of resources that should be cleaned up when the activity is finishing. */
    fun dispose() {
        _oAuthManager?.dispose()
        _oAuthManager = null
    }

    val modelCatalogProvider: ModelCatalogProvider by lazy {
        FallbackModelCatalogProvider(
            primary = HuggingFaceModelCatalogProvider(authRepository = authRepository),
            fallback = ModelCatalog.ALL_MODELS,
        )
    }

    val modelRepository: ModelRepository by lazy { ModelRepositoryImpl(modelCatalogProvider) }
}

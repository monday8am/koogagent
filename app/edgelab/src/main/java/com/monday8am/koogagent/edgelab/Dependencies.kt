package com.monday8am.koogagent.edgelab

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.monday8am.koogagent.core.di.CoreDependencies
import com.monday8am.koogagent.core.oauth.HuggingFaceOAuthManager
import com.monday8am.koogagent.core.storage.DataStoreModelDataSource
import com.monday8am.koogagent.core.storage.DataStoreTestDataSource
import com.monday8am.koogagent.data.auth.AuthRepository
import com.monday8am.koogagent.data.auth.AuthorRepository
import com.monday8am.koogagent.data.model.ModelCatalog
import com.monday8am.koogagent.data.model.ModelCatalogProvider
import com.monday8am.koogagent.data.model.ModelRepository
import com.monday8am.koogagent.data.model.ModelRepositoryImpl
import com.monday8am.koogagent.data.huggingface.FallbackModelCatalogProvider
import com.monday8am.koogagent.data.huggingface.HuggingFaceApiClient
import com.monday8am.koogagent.data.huggingface.HuggingFaceModelRepository
import com.monday8am.koogagent.data.testing.AssetsTestRepository
import com.monday8am.koogagent.data.testing.TestRepository
import com.monday8am.koogagent.data.testing.TestRepositoryImpl
import com.monday8am.koogagent.edgelab.BuildConfig
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
        CoreDependencies.createDownloadManager(appContext, authRepository)
    }

    val authRepository: AuthRepository by lazy {
        CoreDependencies.createAuthRepository(appContext, applicationScope)
    }

    @SuppressLint("StaticFieldLeak") // appContext is Application context, safe to hold statically
    private var _oAuthManager: HuggingFaceOAuthManager? = null
    val oAuthManager: HuggingFaceOAuthManager
        get() {
            if (_oAuthManager == null) {
                _oAuthManager =
                    CoreDependencies.createOAuthManager(
                        context = appContext,
                        clientId = BuildConfig.HF_CLIENT_ID,
                        redirectScheme = "koogagent",
                        activityClass = MainActivity::class.java
                    )
            }
            return _oAuthManager!!
        }

    /** Disposes of resources that should be cleaned up when the activity is finishing. */
    fun dispose() {
        _oAuthManager?.dispose()
        _oAuthManager = null
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "settings"
    )

    val huggingFaceApiClient: HuggingFaceApiClient by lazy {
        HuggingFaceApiClient(authRepository = authRepository)
    }

    val authorRepository: AuthorRepository by lazy {
        CoreDependencies.createAuthorRepository(
            dataStore = appContext.dataStore,
            apiClient = huggingFaceApiClient,
            scope = applicationScope,
        )
    }

    val modelCatalogProvider: ModelCatalogProvider by lazy {
        FallbackModelCatalogProvider(
            primary = HuggingFaceModelRepository(
                apiClient = huggingFaceApiClient,
                authorRepository = authorRepository,
                localModelDataSource = DataStoreModelDataSource(appContext.dataStore),
            ),
            fallback = ModelCatalog.ALL_MODELS,
        )
    }

    val modelRepository: ModelRepository by lazy { ModelRepositoryImpl(modelCatalogProvider) }

    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val testRepository: TestRepository by lazy {
        TestRepositoryImpl(
            remoteUrl = REMOTE_TESTS_URL,
            localTestDataSource = DataStoreTestDataSource(appContext.dataStore, json),
            bundledRepository = AssetsTestRepository(),
            json = json
        )
    }
}

private const val REMOTE_TESTS_URL = "https://edgeagentlab.dev/tests/tool_tests.json"

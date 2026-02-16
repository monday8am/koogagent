package com.monday8am.koogagent.core.di

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.monday8am.agent.core.LocalInferenceEngine
import com.monday8am.koogagent.core.inference.LiteRTLmInferenceEngineImpl
import com.monday8am.koogagent.core.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.core.oauth.HuggingFaceOAuthManager
import com.monday8am.koogagent.core.storage.AuthRepositoryImpl
import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.CoroutineScope

/**
 * Factory object for creating core Android infrastructure services.
 * Provides implementations for inference, download, OAuth, and storage layers.
 */
object CoreDependencies {

    /**
     * Creates a LiteRT-LM inference engine instance.
     */
    fun createInferenceEngine(): LocalInferenceEngine {
        return LiteRTLmInferenceEngineImpl()
    }

    /**
     * Creates a model download manager instance.
     */
    fun createDownloadManager(
        context: Context,
        authRepository: AuthRepository
    ): ModelDownloadManager {
        return ModelDownloadManagerImpl(context, authRepository)
    }

    /**
     * Creates an OAuth manager instance.
     *
     * @param context Android context
     * @param clientId HuggingFace OAuth client ID
     * @param redirectScheme App-specific redirect scheme (e.g., "koogagent", "edgelab", "agentic")
     * @param activityClass The Activity class to return to after OAuth flow
     */
    fun createOAuthManager(
        context: Context,
        clientId: String,
        redirectScheme: String,
        activityClass: Class<out Activity>
    ): HuggingFaceOAuthManager {
        return HuggingFaceOAuthManager(context, clientId, redirectScheme, activityClass)
    }

    /**
     * Creates an auth repository instance.
     */
    fun createAuthRepository(
        context: Context,
        applicationScope: CoroutineScope
    ): AuthRepository {
        return AuthRepositoryImpl(context, applicationScope)
    }
}

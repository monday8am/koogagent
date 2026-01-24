package com.monday8am.koogagent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AuthRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AuthRepository {

    private val dataStore = androidx.datastore.core.DataStoreFactory.create(
        serializer = AuthTokenSerializer.factory(context),
        produceFile = { context.filesDir.resolve("auth_token.pb") }
    )

    override val authToken: StateFlow<String?> = dataStore.data
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    override suspend fun saveToken(token: String) {
        dataStore.updateData { token }
    }

    override suspend fun clearToken() {
        dataStore.updateData { null }
    }
}

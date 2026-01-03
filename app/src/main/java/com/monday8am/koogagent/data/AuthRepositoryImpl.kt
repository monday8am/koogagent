package com.monday8am.koogagent.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepositoryImpl(context: Context) : AuthRepository {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hf_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _authToken = MutableStateFlow(sharedPreferences.getString(TOKEN_KEY, null))
    override val authToken: StateFlow<String?> = _authToken.asStateFlow()

    override suspend fun saveToken(token: String) {
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
        _authToken.value = token
    }

    override suspend fun clearToken() {
        sharedPreferences.edit().remove(TOKEN_KEY).apply()
        _authToken.value = null
    }

    companion object {
        private const val TOKEN_KEY = "hf_auth_token"
    }
}

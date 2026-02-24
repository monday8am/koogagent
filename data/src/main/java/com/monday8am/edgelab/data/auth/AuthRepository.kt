package com.monday8am.edgelab.data.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authToken: StateFlow<String?>

    suspend fun saveToken(token: String)

    suspend fun clearToken()
}

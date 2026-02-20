package com.monday8am.koogagent.data.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authToken: StateFlow<String?>

    suspend fun saveToken(token: String)

    suspend fun clearToken()
}

package com.monday8am.koogagent.data

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authToken: StateFlow<String?>

    suspend fun saveToken(token: String)

    suspend fun clearToken()
}

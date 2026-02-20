package com.monday8am.koogagent.data

import kotlinx.coroutines.flow.StateFlow

interface AuthorRepository {
    val authors: StateFlow<List<AuthorInfo>>

    suspend fun addAuthor(name: String): Result<AuthorInfo>

    suspend fun removeAuthor(name: String)
}

package com.monday8am.koogagent.data

import kotlinx.serialization.Serializable

@Serializable
data class AuthorInfo(
    val name: String,
    val avatarUrl: String? = null,
    val modelCount: Int? = null,
    val isDefault: Boolean = false,
)

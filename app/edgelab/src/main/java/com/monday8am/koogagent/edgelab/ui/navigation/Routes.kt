package com.monday8am.koogagent.edgelab.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object ModelSelector : Route

    @Serializable data class Testing(val modelId: String) : Route

    @Serializable data object TestDetails : Route

    @Serializable data object AuthorManager : Route
}

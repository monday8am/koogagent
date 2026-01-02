package com.monday8am.koogagent.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object ModelSelector : Route

    @Serializable
    data class Notification(val modelId: String) : Route

    @Serializable
    data class Testing(val modelId: String) : Route
}

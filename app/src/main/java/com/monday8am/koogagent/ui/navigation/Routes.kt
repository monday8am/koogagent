package com.monday8am.koogagent.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object ModelSelector : Route

    @Serializable
    data object Notification : Route
}

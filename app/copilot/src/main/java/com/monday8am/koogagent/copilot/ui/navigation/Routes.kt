package com.monday8am.koogagent.copilot.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Onboard : Route

    @Serializable data object RideSetup : Route

    @Serializable
    data class LiveRide(
        val routeId: String,
        val playbackSpeed: Float = 1.0f,
    ) : Route
}

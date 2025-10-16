package com.monday8am.koogagent.weather

import com.monday8am.agent.Location as AgentLocation
import com.monday8am.agent.LocationProvider as AgentLocationProvider

/**
 * Bridge adapter to convert app's LocationProvider to agent's LocationProvider.
 * This allows us to use the app's LocationProvider implementations with the agent module.
 */
class LocationProviderBridge(
    private val appLocationProvider: LocationProvider
) : AgentLocationProvider {
    override suspend fun getLocation(): AgentLocation {
        val location = appLocationProvider.getCurrentLocation()
        return if (location != null) {
            AgentLocation(latitude = location.latitude, longitude = location.longitude)
        } else {
            // Default fallback location (Madrid, Spain)
            AgentLocation(latitude = 40.4168, longitude = -3.7038)
        }
    }
}

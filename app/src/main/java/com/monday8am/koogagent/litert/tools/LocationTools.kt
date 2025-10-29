package com.monday8am.koogagent.litert.tools

import com.google.ai.edge.litertlm.Tool
import com.monday8am.koogagent.data.LocationProvider

/**
 * LiteRT-LM native tool for location services.
 *
 * Uses @Tool annotation for automatic schema generation and registration with LiteRT-LM's
 * OpenAPI-based function calling system.
 */
class LocationTools(
    private val locationProvider: LocationProvider,
) {
    /**
     * Gets the user's current geographic location.
     *
     * @return A string containing latitude and longitude in the format:
     *         "location: latitude X.XXXX, longitude Y.YYYY"
     */
    @Tool(description = "Get the user's current geographic location (latitude and longitude)")
    suspend fun getLocation(): String =
        try {
            val location = locationProvider.getLocation()
            "location: latitude ${location.latitude}, longitude ${location.longitude}"
        } catch (e: Exception) {
            "location: error - ${e.message}"
        }
}

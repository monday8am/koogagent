package com.monday8am.koogagent.weather

/**
 * Represents a geographic location.
 */
data class Location(
    val latitude: Double,
    val longitude: Double
)

/**
 * Interface for location providers.
 */
interface LocationProvider {
    /**
     * Get the current device location.
     * @return Location or null if unavailable
     */
    suspend fun getCurrentLocation(): Location?
}

/**
 * Mock location provider that returns a default location.
 * In a real implementation, this would use Android's LocationManager or FusedLocationProviderClient.
 */
class MockLocationProvider(
    private val defaultLocation: Location = Location(
        latitude = 40.4168,  // Madrid, Spain
        longitude = -3.7038
    )
) : LocationProvider {
    override suspend fun getCurrentLocation(): Location = defaultLocation
}

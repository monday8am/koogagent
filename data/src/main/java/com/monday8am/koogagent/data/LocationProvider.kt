package com.monday8am.koogagent.data

/**
 * Interface for location providers.
 * Implementations can use Android LocationManager, mock data, or other sources.
 */
interface LocationProvider {
    suspend fun getLocation(): Location
}

class MockLocationProvider : LocationProvider {
    override suspend fun getLocation(): Location {
        return Location(latitude = 40.4168, longitude = -3.7038) // Madrid, Spain coordinates
    }
}

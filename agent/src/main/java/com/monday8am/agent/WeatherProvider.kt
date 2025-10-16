package com.monday8am.agent

/**
 * Interface for weather data providers.
 * Implementations can use MCP servers, REST APIs, or mock data.
 */
interface WeatherProvider {
    /**
     * Get current weather for a location.
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @return Weather condition or null if unavailable
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherCondition?
}

/**
 * Mock weather provider for testing and fallback.
 */
class MockWeatherProvider : WeatherProvider {
    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherCondition {
        // Simple mock based on latitude (northern vs southern hemisphere season approximation)
        return if (latitude > 40) WeatherCondition.COLD else WeatherCondition.SUNNY
    }
}

/**
 * Interface for location providers.
 * Implementations can use Android LocationManager, mock data, or other sources.
 */
interface LocationProvider {
    /**
     * Get current location coordinates.
     * @return Location with latitude and longitude
     */
    suspend fun getLocation(): Location
}

/**
 * Data class representing a geographic location.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Mock location provider for testing.
 * Returns Madrid, Spain coordinates by default.
 */
class MockLocationProvider : LocationProvider {
    override suspend fun getLocation(): Location {
        // Madrid, Spain coordinates
        return Location(latitude = 40.4168, longitude = -3.7038)
    }
}

package com.monday8am.koogagent.data

/**
 * Interface for weather data providers.
 * Implementations can use MCP servers, REST APIs, or mock data.
 */
interface WeatherProvider {
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherCondition?
}

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
    suspend fun getLocation(): Location
}

data class Location(
    val latitude: Double,
    val longitude: Double,
)

class MockLocationProvider : LocationProvider {
    override suspend fun getLocation(): Location {
        return Location(latitude = 40.4168, longitude = -3.7038) // Madrid, Spain coordinates
    }
}

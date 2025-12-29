package com.monday8am.koogagent.data

/**
 * Interface for weather providers.
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

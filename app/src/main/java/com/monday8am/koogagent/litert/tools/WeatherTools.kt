package com.monday8am.koogagent.litert.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider

/**
 * LiteRT-LM native tool for weather services.
 *
 * Provides two variants:
 * 1. Zero-parameter: getWeather() - gets weather at user's current location
 * 2. Parameterized: getWeatherAtLocation(lat, lon) - gets weather at specific coordinates
 */
class WeatherTools(
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
) {
    /**
     * Gets the current weather at the user's current location (zero parameters).
     *
     * This is simpler for the model to call as it requires no parameters.
     * Internally fetches the user's location first, then queries weather.
     *
     * @return A string containing weather condition and approximate temperature
     */
    @Tool(description = "Get the current weather conditions at your current location")
    suspend fun getWeather(): String =
        try {
            val location = locationProvider.getLocation()
            val weather = weatherProvider.getCurrentWeather(latitude = location.latitude, longitude = location.longitude)
            if (weather != null) {
                val temperature = getApproximateTemperature(weather)
                "Weather: ${weather.name.lowercase()}, Temperature: $temperature°C at your location (${location.latitude}, ${location.longitude})"
            } else {
                "Weather: unknown at your location"
            }
        } catch (e: Exception) {
            "Weather: error - ${e.message}"
        }

    /**
     * Gets the current weather for a specific geographic location (with parameters).
     *
     * Requires the model to extract and pass latitude/longitude from context.
     *
     * @param latitude Latitude coordinate in decimal degrees (e.g., 40.4168 for Madrid)
     * @param longitude Longitude coordinate in decimal degrees (e.g., -3.7038 for Madrid)
     * @return A string containing weather condition and approximate temperature
     */
    @Tool(description = "Get the current weather conditions for a specific latitude and longitude location")
    suspend fun getWeatherAtLocation(
        @ToolParam(description = "Latitude coordinate in decimal degrees") latitude: Double,
        @ToolParam(description = "Longitude coordinate in decimal degrees") longitude: Double,
    ): String =
        try {
            val weather = weatherProvider.getCurrentWeather(latitude = latitude, longitude = longitude)
            if (weather != null) {
                val temperature = getApproximateTemperature(weather)
                "Weather: ${weather.name.lowercase()}, Temperature: $temperature°C at $latitude, $longitude"
            } else {
                "Weather: unknown at $latitude, $longitude"
            }
        } catch (e: Exception) {
            "Weather: error - ${e.message}"
        }

    /**
     * Maps weather conditions to approximate temperatures in Celsius.
     * These are realistic estimates for each weather type.
     */
    private fun getApproximateTemperature(condition: WeatherCondition): Double =
        when (condition) {
            WeatherCondition.HOT -> 32.0
            WeatherCondition.COLD -> 5.0
            WeatherCondition.SUNNY -> 22.0
            WeatherCondition.CLOUDY -> 18.0
            WeatherCondition.RAINY -> 15.0
        }
}

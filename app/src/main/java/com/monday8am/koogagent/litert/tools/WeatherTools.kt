package com.monday8am.koogagent.litert.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider

/**
 * LiteRT-LM native tool for weather services.
 *
 * Uses @Tool annotation for automatic OpenAPI schema generation.
 * Supports parameterized function calling with latitude/longitude coordinates.
 */
class WeatherTools(
    private val weatherProvider: WeatherProvider,
) {
    /**
     * Gets the current weather for a specific geographic location.
     *
     * @param latitude Latitude coordinate in decimal degrees (e.g., 40.4168 for Madrid)
     * @param longitude Longitude coordinate in decimal degrees (e.g., -3.7038 for Madrid)
     * @return A string containing weather condition and approximate temperature:
     *         "Weather: sunny, Temperature: 22.0°C at 40.4168, -3.7038"
     */
    @Tool(description = "Get the current weather conditions for a specific latitude and longitude location")
    suspend fun getWeather(
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

package com.monday8am.agent

import ai.koog.agents.ext.tool.ToolAction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/**
 * Koog Tool for fetching weather information.
 * This tool allows the AI agent to autonomously request weather data
 * when it determines weather context is needed for notification generation.
 *
 * Using annotation-based tool definition for Koog.
 */
class WeatherTool(
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
) {

    @Serializable
    data class WeatherResult(
        val condition: String,
        val temperature: Double?,
        val location: String,
        val success: Boolean
    )

    /**
     * Fetches current weather information for the user's location.
     * Use this tool when you need weather context to personalize meal or hydration suggestions.
     * Examples:
     * - Suggest hot soup on cold days
     * - Recommend hydration on hot days
     * - Consider rainy weather for comfort food suggestions
     */
    @ToolAction
    fun getCurrentWeather(): String {
        return runBlocking {
            try {
                val location = locationProvider.getLocation()
                val weather = weatherProvider.getCurrentWeather(location.latitude, location.longitude)

                println("WeatherTool: Agent requested weather data")

                if (weather != null) {
                    val temperature = getApproximateTemperature(weather)
                    val result = WeatherResult(
                        condition = weather.name.lowercase(),
                        temperature = temperature,
                        location = "${location.latitude}, ${location.longitude}",
                        success = true
                    )
                    "Weather: ${result.condition}, Temperature: ${result.temperature}°C at ${result.location}"
                } else {
                    "Weather: unknown at ${location.latitude}, ${location.longitude}"
                }
            } catch (e: Exception) {
                println("WeatherTool: Error fetching weather: ${e.message}")
                "Weather: error - ${e.message}"
            }
        }
    }

    /**
     * Approximates temperature based on weather condition.
     */
    private fun getApproximateTemperature(condition: WeatherCondition): Double {
        return when (condition) {
            WeatherCondition.HOT -> 32.0
            WeatherCondition.COLD -> 5.0
            WeatherCondition.SUNNY -> 22.0
            WeatherCondition.CLOUDY -> 18.0
            WeatherCondition.RAINY -> 15.0
        }
    }
}

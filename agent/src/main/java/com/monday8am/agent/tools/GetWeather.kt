package com.monday8am.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.monday8am.agent.core.logger
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.serialization.builtins.serializer

/**
 * Koog Tool for fetching weather information.
 * This tool allows the AI agent to autonomously request weather data
 * when it determines weather context is needed for notification generation.
 */
class GetWeather(
    private val locationProvider: LocationProvider,
    private val weatherProvider: WeatherProvider,
) : SimpleTool<Unit>(
    name = "GetWeather",
    description = "Call this function to get the current weather. Parameters not required!",
    argsSerializer = Unit.serializer(),
) {
    /**
     * Fetches current weather information for the user's location.
     * Use this tool when you need weather context to personalize meal or hydration suggestions.
     */
    override suspend fun execute(args: Unit): String =
        try {
            val location = locationProvider.getLocation()
            val weather = weatherProvider.getCurrentWeather(latitude = location.latitude, longitude = location.longitude)
            if (weather != null) {
                val temperature = getApproximateTemperature(weather)
                val result =
                    WeatherResult(
                        condition = weather.name.lowercase(),
                        temperature = temperature,
                        location = "${location.latitude}, ${location.longitude}",
                        success = true,
                    )
                "weather: ${result.condition}, temperature: ${result.temperature}°C"
            } else {
                "Weather: unknown at ${location.latitude}, ${location.longitude}"
            }
        } catch (e: Exception) {
            logger.e { "GetWeatherTool: Error fetching weather: ${e.message}" }
            "Weather: error - ${e.message}"
        }

    private fun getApproximateTemperature(condition: WeatherCondition): Double =
        when (condition) {
            WeatherCondition.HOT -> 32.0
            WeatherCondition.COLD -> 5.0
            WeatherCondition.SUNNY -> 22.0
            WeatherCondition.CLOUDY -> 18.0
            WeatherCondition.RAINY -> 15.0
        }
}

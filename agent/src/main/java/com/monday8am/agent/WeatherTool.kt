package com.monday8am.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Koog Tool for fetching weather information.
 * This tool allows the AI agent to autonomously request weather data
 * when it determines weather context is needed for notification generation.
 */
class WeatherTool(
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
) : SimpleTool<Unit>() {

    @Serializable
    private data class WeatherResult(
        val condition: String,
        val temperature: Double?,
        val location: String,
        val success: Boolean,
    )

    override val argsSerializer = Unit.serializer()

    override val description: String
        get() = "Get the current weather"

    override val descriptor = ToolDescriptor(
        name = "WeatherTool",
        description = "Get the current weather for a country",
        requiredParameters = listOf(),
    )

    /**
     * Fetches current weather information for the user's location.
     * Use this tool when you need weather context to personalize meal or hydration suggestions.
     */
    override suspend fun doExecute(args: Unit): String =
        try {
            val location = locationProvider.getLocation()
            val weather = weatherProvider.getCurrentWeather(location.latitude, location.longitude)

            println("WeatherTool: Agent requested weather data")

            if (weather != null) {
                val temperature = getApproximateTemperature(weather)
                val result =
                    WeatherResult(
                        condition = weather.name.lowercase(),
                        temperature = temperature,
                        location = "${location.latitude}, ${location.longitude}",
                        success = true,
                    )
                "Weather: ${result.condition}, Temperature: ${result.temperature}°C at ${result.location}"
            } else {
                "Weather: unknown at ${location.latitude}, ${location.longitude}"
            }
        } catch (e: Exception) {
            println("WeatherTool: Error fetching weather: ${e.message}")
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

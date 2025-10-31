package com.monday8am.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.monday8am.agent.core.logger
import com.monday8am.koogagent.data.WeatherCondition
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.serialization.Serializable

/**
 * Koog Tool for fetching weather information.
 * This tool allows the AI agent to autonomously request weather data
 * when it determines weather context is needed for notification generation.
 */

@Serializable
internal data class WeatherResult(
    val condition: String,
    val temperature: Double?,
    val location: String,
    val success: Boolean,
)

class GetWeatherFromLocation(
    private val weatherProvider: WeatherProvider,
) : SimpleTool<GetWeatherFromLocation.Args>() {
    @Serializable
    data class Args(
        @property:LLMDescription("Latitude of the location in double format")
        val latitude: Double,
        @property:LLMDescription("Longitude of the location in double format")
        val longitude: Double,
    )

    override val argsSerializer = Args.serializer()

    override val description: String
        get() = "Get the current weather from latitude and longitude parameters"

    override val descriptor =
        ToolDescriptor(
            name = "GetWeatherFromLocation",
            description = "Get the current weather from the provided latitude and longitude parameters",
            requiredParameters =
                listOf(
                    ToolParameterDescriptor(name = "latitude", description = "geo latitude", type = ToolParameterType.String),
                    ToolParameterDescriptor(name = "longitude", description = "geo longitude", type = ToolParameterType.String),
                ),
        )

    /**
     * Fetches current weather information for the user's location.
     * Use this tool when you need weather context to personalize meal or hydration suggestions.
     */
    override suspend fun doExecute(args: Args): String =
        try {
            val weather = weatherProvider.getCurrentWeather(latitude = args.latitude, longitude = args.longitude)
            if (weather != null) {
                val temperature = getApproximateTemperature(weather)
                val result =
                    WeatherResult(
                        condition = weather.name.lowercase(),
                        temperature = temperature,
                        location = "${args.latitude}, ${args.longitude}",
                        success = true,
                    )
                "weather: ${result.condition}, temperature: ${result.temperature}°C"
            } else {
                "Weather: unknown at ${args.latitude}, ${args.longitude}"
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

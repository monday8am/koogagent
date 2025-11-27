package com.monday8am.agent.tools

import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.monday8am.koogagent.data.WeatherProviderImpl
import kotlinx.coroutines.runBlocking

/**
 * Native LiteRT-LM tool set for weather operations.
 * Uses @Tool annotations for automatic schema generation.
 *
 * Fetches real weather data from Open-Meteo API.
 */
class NativeWeatherTools {
    private val logger = Logger.withTag("NativeWeatherTools")
    private val weatherProvider = WeatherProviderImpl()

    /**
     * Gets current weather for a given location.
     *
     * @param latitude Latitude coordinate (-90 to 90)
     * @param longitude Longitude coordinate (-180 to 180)
     * @return String describing the weather condition
     */
    @Tool(description = "Get current weather conditions for a specific location")
    fun getWeather(
        @ToolParam(description = "Latitude coordinate (between -90 and 90)")
        latitude: Double,
        @ToolParam(description = "Longitude coordinate (between -180 and 180)")
        longitude: Double,
    ): String =
        runBlocking {
            try {
                val weatherCondition = weatherProvider.getCurrentWeather(latitude, longitude)

                if (weatherCondition != null) {
                    logger.d { "Weather fetched: condition=${weatherCondition.name}" }
                    "The weather is ${weatherCondition.name.lowercase()}"
                } else {
                    logger.w { "Weather fetch returned null" }
                    "Weather data unavailable"
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to fetch weather" }
                "Failed to fetch weather: ${e.message}"
            }
        }
}

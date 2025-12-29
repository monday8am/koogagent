package com.monday8am.koogagent.inference.litertlm

import android.util.Log
import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.monday8am.koogagent.data.WeatherProviderImpl
import kotlinx.coroutines.runBlocking

/**
 * Native LiteRT-LM tool set
 */
class LiteRTLmTools {
    private val logger = Logger.withTag("NativeWeatherTools")
    private val weatherProvider = WeatherProviderImpl()

    @Tool(description = "Get current weather conditions for a specific location")
    fun getWeather(
        @ToolParam(description = "Latitude coordinate (between -90 and 90)")
        latitude: Double,
        @ToolParam(description = "Longitude coordinate (between -180 and 180)")
        longitude: Double,
    ): String = runBlocking {
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

class NativeLocationTools {
    @Tool(description = "No arguments required. Get the user's current location in latitude and longitude format")
    fun get_location(): String {
        val result = """{"latitude": 40.4168, "longitude": -3.7038}"""
        Log.e("NativeLocationTools", "🔧 Returning: $result")
        return result
    }
}

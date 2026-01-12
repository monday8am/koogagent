package com.monday8am.koogagent.inference.litertlm

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.monday8am.agent.tools.ToolCall
import com.monday8am.agent.tools.ToolTrace
import com.monday8am.koogagent.data.WeatherProviderImpl
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

/** Native LiteRT-LM tool set */
class LiteRTLmTools {
    private val weatherProvider = WeatherProviderImpl()

    @Tool(description = "Get current weather conditions for a specific location")
    fun get_weather(
        @ToolParam(description = "Latitude coordinate (between -90 and 90)") latitude: Double,
        @ToolParam(description = "Longitude coordinate (between -180 and 180)") longitude: Double,
    ): String = runBlocking {
        ToolTrace.log(
            ToolCall(
                name = "get_weather",
                args = mapOf("latitude" to latitude, "longitude" to longitude),
            )
        )

        // Check for mock response
        ToolTrace.getMockResponse("get_weather")?.let { return@runBlocking it }

        try {
            val weatherCondition = weatherProvider.getCurrentWeather(latitude, longitude)

            if (weatherCondition != null) {
                "The weather is ${weatherCondition.name.lowercase()}"
            } else {
                "Weather data unavailable"
            }
        } catch (e: Exception) {
            "Failed to fetch weather: ${e.message}"
        }
    }

    @Tool(description = "Get current date and time")
    fun get_time(): String {
        ToolTrace.log(ToolCall(name = "get_time", args = emptyMap()))

        // Check for mock response
        ToolTrace.getMockResponse("get_time")?.let { return it }

        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return now.format(formatter)
    }

    @Tool(description = "Get meal history for a user")
    fun get_meal_history(
        @ToolParam(description = "Meal type (BREAKFAST, LUNCH, DINNER, SNACK)") mealType: String
    ): String {
        ToolTrace.log(ToolCall(name = "get_meal_history", args = mapOf("mealType" to mealType)))

        // Check for mock response
        ToolTrace.getMockResponse("get_meal_history")?.let { return it }

        // Default mock data for testing
        return """
            {
                "mealType": "$mealType",
                "entries": [
                    {"food": "Apple", "calories": 95},
                    {"food": "Salad", "calories": 150}
                ],
                "totalCalories": 245
            }
        """.trimIndent()
    }
}

class NativeLocationTools {
    @Tool(
        description =
            "No arguments required. Get the user's current location in latitude and longitude format"
    )
    fun get_location(): String {
        ToolTrace.log(ToolCall(name = "get_location", args = emptyMap()))

        // Check for mock response
        ToolTrace.getMockResponse("get_location")?.let { return it }

        val result = """{"latitude": 40.4168, "longitude": -3.7038}"""
        Log.e("NativeLocationTools", "🔧 Returning: $result")
        return result
    }
}

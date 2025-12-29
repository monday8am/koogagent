package com.monday8am.koogagent.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Weather provider using Open-Meteo API (https://open-meteo.com/).
 * No API key required, free for non-commercial use.
 */
class WeatherProviderImpl(
    private val client: OkHttpClient = OkHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WeatherProvider {
    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherCondition? =
        withContext(dispatcher) {
            try {
                val url =
                    "https://api.open-meteo.com/v1/forecast?" +
                        "latitude=$latitude&longitude=$longitude&current_weather=true"

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null

                    val json = JSONObject(response.body.string())
                    val currentWeather = json.getJSONObject("current_weather")

                    mapWeatherCodeToCondition(
                        weatherCode = currentWeather.getInt("weathercode"),
                        temperature = currentWeather.getDouble("temperature"),
                    )
                }
            } catch (e: Exception) {
                println("OpenMeteoWeatherProvider: Error fetching weather: ${e.message}")
                null
            }
        }

    /**
     * Maps WMO Weather interpretation codes to WeatherCondition enum.
     * See: https://open-meteo.com/en/docs
     */
    private fun mapWeatherCodeToCondition(weatherCode: Int, temperature: Double): WeatherCondition = when {
        // Temperature-based conditions
        temperature > 30 -> WeatherCondition.HOT

        temperature < 5 -> WeatherCondition.COLD

        // Weather code based
        weatherCode == 0 -> WeatherCondition.SUNNY

        // Clear sky
        weatherCode in 1..3 -> WeatherCondition.CLOUDY

        // Mainly clear, partly cloudy, overcast
        weatherCode in 51..67 || weatherCode in 80..82 -> WeatherCondition.RAINY

        // Drizzle, rain, showers
        weatherCode in 71..77 || weatherCode in 85..86 -> WeatherCondition.RAINY

        // Snow (treat as rainy for notification purposes)
        weatherCode in 95..99 -> WeatherCondition.RAINY

        // Thunderstorm

        else -> WeatherCondition.CLOUDY
    }
}

package com.monday8am.local

import com.monday8am.agent.NotificationGenerator
import com.monday8am.agent.WeatherTool
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.OpenMeteoWeatherProvider
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Hello from pure Kotlin!")

    val weatherProvider = OpenMeteoWeatherProvider()
    val locationProvider = MockLocationProvider()

    val message = NotificationGenerator(
        agent = OllamaAgent().apply {
            initializeWithTool(WeatherTool(weatherProvider, locationProvider))
        }
    ).generate(
        NotificationContext(
            mealType = MealType.LUNCH,
            motivationLevel = MotivationLevel.HIGH,
            alreadyLogged = true,
            userLocale = "en-US",
            country = "ES",
        )
    )
    println(message)
}

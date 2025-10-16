package com.monday8am.local

import com.monday8am.agent.NotificationGenerator
import com.monday8am.agent.OllamaAgent
import com.monday8am.agent.WeatherToolSet
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.OpenMeteoWeatherProvider
import com.monday8am.koogagent.data.WeatherCondition
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Hello from pure Kotlin!")

    val weatherProvider = OpenMeteoWeatherProvider()
    val locationProvider = MockLocationProvider()
    val weatherToolSet = WeatherToolSet(weatherProvider, locationProvider)

    val message = NotificationGenerator(
        agent = OllamaAgent().apply {
            initializeWithTools(weatherToolSet)
        }
    ).generate(
        NotificationContext(
            mealType = MealType.WATER,
            motivationLevel = MotivationLevel.HIGH,
            weather = WeatherCondition.SUNNY,
            alreadyLogged = true,
            userLocale = "en-US",
            country = "ES",
        )
    )
    println(message)
}

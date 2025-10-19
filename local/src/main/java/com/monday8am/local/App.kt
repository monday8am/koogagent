package com.monday8am.local

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import com.monday8am.agent.GetLocationTool
import com.monday8am.agent.GetWeatherTool
import com.monday8am.agent.NotificationGenerator
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.OpenMeteoWeatherProvider
import kotlinx.coroutines.runBlocking

fun main() =
    runBlocking {
        println("Hello from pure Kotlin!")

        val weatherProvider = OpenMeteoWeatherProvider()
        val locationProvider = MockLocationProvider()

        val toolRegistry = ToolRegistry {
            tool(tool = GetWeatherTool(weatherProvider))
            tool(tool = GetLocationTool(locationProvider))
            SayToUser
        }

        val message =
            NotificationGenerator(
                agent =
                    OllamaAgent().apply {
                        initializeWithTools(toolRegistry)
                    },
            ).generate(
                NotificationContext(
                    mealType = MealType.LUNCH,
                    motivationLevel = MotivationLevel.HIGH,
                    alreadyLogged = true,
                    userLocale = "en-US",
                    country = "ES",
                ),
            )
        println(message)
    }

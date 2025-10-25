package com.monday8am.agent.ollama

import ai.koog.agents.core.tools.ToolRegistry
import co.touchlab.kermit.Logger
import com.monday8am.agent.core.NotificationGenerator
import com.monday8am.agent.tools.GetLocationTool
import com.monday8am.agent.tools.GetWeatherTool
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.WeatherProviderImpl
import kotlinx.coroutines.runBlocking

private val logger = Logger.withTag("KoogAgentApp")

fun main() =
    runBlocking {
        logger.i { "Starting KoogAgent local test application" }

        val weatherProvider = WeatherProviderImpl()
        val locationProvider = MockLocationProvider()
        val toolRegistry =
            ToolRegistry {
                tool(tool = GetWeatherTool(weatherProvider = weatherProvider, locationProvider = locationProvider))
                tool(tool = GetLocationTool(locationProvider))
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

        logger.i { "Generated notification:" }
        logger.i { "  Title: ${message.title}" }
        logger.i { "  Body: ${message.body}" }
        logger.i { "  Language: ${message.language}" }
        logger.i { "  Confidence: ${message.confidence}" }
        logger.i { "  Is Fallback: ${message.isFallback}" }

        logger.i { "Application completed successfully" }
    }

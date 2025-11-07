package com.monday8am.agent.ollama

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.toLLModel
import co.touchlab.kermit.Logger
import com.monday8am.agent.core.NotificationAgent
import com.monday8am.agent.core.NotificationGenerator
import com.monday8am.agent.core.ToolFormat
import com.monday8am.agent.tools.GetLocation
import com.monday8am.agent.tools.GetWeather
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
                tool(tool = GetWeather(weatherProvider = weatherProvider, locationProvider = locationProvider))
                tool(tool = GetLocation(locationProvider))
            }

        // Get first available Ollama model dynamically
        val client = OllamaClient()
        val llModel = client.getModels().firstOrNull()?.toLLModel()
        if (llModel == null) {
            logger.e { "No Ollama models found" }
            throw Exception("No models found")
        }
        logger.i { "Using Ollama model: ${llModel.id}" }

        val agent =
            NotificationAgent.koog(
                modelId = llModel.id,
            ).apply {
                initializeWithTools(toolRegistry)
            }

        val message =
            NotificationGenerator(agent = agent).generate(
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

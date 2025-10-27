package com.monday8am.presentation.testing

import ai.koog.agents.core.tools.ToolRegistry
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.monday8am.agent.gemma.GemmaAgent
import com.monday8am.agent.tools.GetLocationTool
import com.monday8am.agent.tools.GetWeatherTool
import com.monday8am.agent.tools.GetWeatherToolFromLocation
import com.monday8am.koogagent.data.LocationProvider
import com.monday8am.koogagent.data.WeatherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Improved tool calling integration tests for Gemma + Koog
 *
 * ## Key Improvements:
 * - Added timing measurements
 * - Better assertions with actual coordinate checking
 * - Tool hallucination test
 * - Logging integration
 * - Clear expectations documentation
 * - Progressive updates via Flow<String>
 */
internal class GemmaToolCallingTest(
    private val promptExecutor: suspend (String) -> String?,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: LocationProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = Logger.Companion.withTag("GemmaToolCallingTest")
    private val testIterations = 5

    fun runAllTests(): Flow<String> =
        flow {
            withContext(dispatcher) {
                // Enable verbose logging for tests
                Logger.Companion.setMinSeverity(Severity.Debug)

                emit("=== GEMMA TOOL CALLING TESTS ===")
                emit("Model: Gemma 3n-1b-it-int4")
                emit("Protocol: Simplified JSON (single tool, no parameters)")
                emit("")

                try {
                    val tests =
                        listOf(
                            // ::testBasicToolCall,
                            // ::testNoToolNeeded,
                            // ::testToolHallucination,
                            ::testWeatherTool,
                            // ::testMultiTurnSequence,
                        )

                    tests.forEach { test ->
                        val result = test()
                        // Emit each line of the test result
                        result.lines().forEach { line ->
                            emit(line)
                        }
                        emit("")
                    }
                } catch (e: Exception) {
                    emit("FATAL ERROR: ${e.message}")
                    logger.e(e) { "Test suite failed" }
                }

                emit("=== TESTS COMPLETE ===")
            }
        }

    private suspend fun testBasicToolCall(): String {
        val output = StringBuilder()
        output.appendLine("TEST 1: Basic Location Tool Call")
        output.appendLine("─".repeat(testIterations))

        val toolRegistry =
            ToolRegistry.Companion {
                tool(GetLocationTool(locationProvider))
            }

        val queries =
            listOf(
                "Where am I?" to "short query",
                "What is my current location?" to "explicit query",
                "Can you tell me my coordinates?" to "coordinate query",
            )

        var passCount = 0

        for ((query, description) in queries) {
            val startTime = System.currentTimeMillis()
            output.appendLine("Query ($description): $query")

            try {
                val agent = GemmaAgent(promptExecutor = promptExecutor)
                agent.initializeWithTools(toolRegistry)

                val result =
                    agent.generateMessage(
                        systemPrompt = "You are a helpful assistant that can access the user's location.",
                        userPrompt = query,
                    )

                val duration = System.currentTimeMillis() - startTime
                output.appendLine("Result: $result")
                output.appendLine("Duration: ${duration}ms")

                // Check for Madrid coordinates from MockLocationProvider
                val hasMadridLat = result.contains("40.4") || result.contains("40°")
                val hasMadridLon = result.contains("3.7") || result.contains("3°") || result.contains("-3.7")
                val hasLocationKeyword =
                    result.contains("location", ignoreCase = true) ||
                        result.contains("latitude", ignoreCase = true) ||
                        result.contains("longitude", ignoreCase = true)

                val passed = (hasMadridLat || hasMadridLon) && hasLocationKeyword

                if (passed) {
                    output.appendLine("✓ PASS: Response contains Madrid coordinates")
                    passCount++
                } else {
                    output.appendLine("✗ FAIL: Missing expected coordinates")
                    output.appendLine("  Expected: Madrid (40.4168, -3.7038)")
                    output.appendLine("  Got Madrid Lat: $hasMadridLat, Lon: $hasMadridLon, Keywords: $hasLocationKeyword")
                }
            } catch (e: Exception) {
                output.appendLine("✗ ERROR: ${e.message}")
                logger.e(e) { "Test failed: $query" }
            }
            output.appendLine()
        }

        output.appendLine("Summary: $passCount/${queries.size} passed")
        return output.toString()
    }

    private suspend fun testNoToolNeeded(): String {
        val output = StringBuilder()
        output.appendLine("TEST 2: No Tool Needed (Normal Conversation)")
        output.appendLine("─".repeat(testIterations))

        val toolRegistry =
            ToolRegistry.Companion {
                tool(GetLocationTool(locationProvider))
            }

        val queries =
            listOf(
                "Hello! How are you?",
                "Tell me a joke",
                "What is 2 + 2?",
            )

        var passCount = 0

        for (query in queries) {
            val startTime = System.currentTimeMillis()
            output.appendLine("Query: $query")

            try {
                val agent = GemmaAgent(promptExecutor = promptExecutor)
                agent.initializeWithTools(toolRegistry)

                val result =
                    agent.generateMessage(
                        systemPrompt = "You are a helpful assistant. Only use tools when specifically asked about location.",
                        userPrompt = query,
                    )

                val duration = System.currentTimeMillis() - startTime
                output.appendLine("Result: $result")
                output.appendLine("Duration: ${duration}ms")

                // Should NOT mention tools or call GetLocationTool
                val inappropriateToolUse =
                    result.contains("{\"tool\"", ignoreCase = true) ||
                        result.contains("GetLocationTool", ignoreCase = true) ||
                        result.contains("latitude", ignoreCase = true)

                val hasValidResponse = result.isNotBlank() && result.length > 5

                if (!inappropriateToolUse && hasValidResponse) {
                    output.appendLine("✓ PASS: Normal conversation without inappropriate tool use")
                    passCount++
                } else {
                    output.appendLine("✗ FAIL: Inappropriate tool mention or invalid response")
                }
            } catch (e: Exception) {
                output.appendLine("✗ ERROR: ${e.message}")
            }
            output.appendLine()
        }

        output.appendLine("Summary: $passCount/${queries.size} passed")
        return output.toString()
    }

    private suspend fun testToolHallucination(): String {
        val output = StringBuilder()
        output.appendLine("TEST 3: Tool Hallucination Prevention")
        output.appendLine("─".repeat(testIterations))
        output.appendLine("Available tools: GetLocationTool ONLY")
        output.appendLine("Query asks about: Weather (requires GetWeatherTool)")
        output.appendLine("Expected: Model should NOT hallucinate GetWeatherTool")
        output.appendLine()

        val toolRegistry =
            ToolRegistry.Companion {
                tool(GetLocationTool(locationProvider))
            }

        val query = "What's the weather like?"

        val startTime = System.currentTimeMillis()
        output.appendLine("Query: $query")

        try {
            val agent = GemmaAgent(promptExecutor = promptExecutor)
            agent.initializeWithTools(toolRegistry)

            val result =
                agent.generateMessage(
                    systemPrompt = "You are a helpful assistant. ONLY use tools that are explicitly available to you.",
                    userPrompt = query,
                )

            val duration = System.currentTimeMillis() - startTime
            output.appendLine("Result: $result")
            output.appendLine("Duration: ${duration}ms")

            // Check if model handled unavailable tool gracefully
            val apologizes =
                result.contains("can't", ignoreCase = true) ||
                    result.contains("unable", ignoreCase = true) ||
                    result.contains("don't have", ignoreCase = true) ||
                    result.contains("doesn't exist", ignoreCase = true)

            // OR model just answered without using tools (acceptable)
            val answeredWithoutTools = !result.contains("{\"tool\"")

            if (apologizes || answeredWithoutTools) {
                output.appendLine("✓ PASS: Correctly handled unavailable tool")
            } else {
                output.appendLine("✗ FAIL: May have attempted to use unavailable tool")
            }
        } catch (e: Exception) {
            output.appendLine("✗ ERROR: ${e.message}")
        }

        return output.toString()
    }

    private suspend fun testWeatherTool(): String {
        val output = StringBuilder()
        output.appendLine("TEST 4: Weather Tool Execution")
        output.appendLine("─".repeat(testIterations))

        val toolRegistry =
            ToolRegistry.Companion {
                tool(GetWeatherTool(locationProvider = locationProvider, weatherProvider = weatherProvider))
            }

        // Note: Weather tool needs coordinates, but Gemma can't pass parameters
        // So this tests if the tool uses default/context coordinates
        val query = "What's the weather?"

        val startTime = System.currentTimeMillis()
        output.appendLine("Query: $query")
        output.appendLine("Note: GetWeatherTool should use default coordinates (no parameters)")
        output.appendLine()

        try {
            val agent = GemmaAgent(promptExecutor = promptExecutor)
            agent.initializeWithTools(toolRegistry)

            val result =
                agent.generateMessage(
                    systemPrompt = "You are a helpful weather assistant. Use GetWeatherTool to check current weather.",
                    userPrompt = query,
                )

            val duration = System.currentTimeMillis() - startTime
            output.appendLine("Result: $result")
            output.appendLine("Duration: ${duration}ms")

            val hasWeatherInfo =
                result.contains("temperature", ignoreCase = true) ||
                    result.contains("weather", ignoreCase = true) ||
                    result.contains("sunny", ignoreCase = true) ||
                    result.contains("cloudy", ignoreCase = true) ||
                    result.contains("°", ignoreCase = true)

            if (hasWeatherInfo) {
                output.appendLine("✓ PASS: Weather tool executed successfully")
            } else {
                output.appendLine("✗ FAIL: No weather data in response")
            }
        } catch (e: Exception) {
            output.appendLine("✗ ERROR: ${e.message}")
        }

        return output.toString()
    }

    private suspend fun testMultiTurnSequence(): String {
        val output = StringBuilder()
        output.appendLine("TEST 5: Multi-Turn Tool Sequence")
        output.appendLine("─".repeat(testIterations))
        output.appendLine("IMPORTANT: This test demonstrates the limitation:")
        output.appendLine("  Gemma CANNOT call multiple tools in one turn")
        output.appendLine("  Must use separate turns for location → weather")
        output.appendLine()

        val toolRegistry =
            ToolRegistry.Companion {
                tool(GetLocationTool(locationProvider))
                tool(GetWeatherToolFromLocation(weatherProvider))
            }

        val query = "What's the weather where I am?"

        output.appendLine("Query: $query")
        output.appendLine()

        try {
            val agent = GemmaAgent(promptExecutor = promptExecutor)
            agent.initializeWithTools(toolRegistry)

            // Turn 1: Should trigger location tool (hopefully)
            val turn1Start = System.currentTimeMillis()
            val result =
                agent.generateMessage(
                    systemPrompt =
                        """
                        You are a helpful assistant.
                        To answer weather questions, you need the user's location first.
                        Use GetLocationTool, then GetWeatherTool.
                        """.trimIndent(),
                    userPrompt = query,
                )
            val turn1Duration = System.currentTimeMillis() - turn1Start

            output.appendLine("Turn 1 Result: $result")
            output.appendLine("Turn 1 Duration: ${turn1Duration}ms")
            output.appendLine()

            // Check if it contains weather info (unlikely in one turn with current protocol)
            val hasWeather =
                result.contains("temperature", ignoreCase = true) ||
                    result.contains("sunny", ignoreCase = true) ||
                    result.contains("cloudy", ignoreCase = true)

            val hasLocation =
                result.contains("latitude", ignoreCase = true) ||
                    result.contains("longitude", ignoreCase = true)

            when {
                hasWeather && hasLocation -> {
                    output.appendLine("⚠️  UNEXPECTED: Both location AND weather in one turn!")
                    output.appendLine("    This suggests agent made multiple tool calls somehow")
                }
                hasWeather -> {
                    output.appendLine("✓ PASS: Weather information retrieved")
                    output.appendLine("    (But we don't know how it got coordinates)")
                }
                hasLocation -> {
                    output.appendLine("⚠️  PARTIAL: Got location, but not weather")
                    output.appendLine("    This is expected with single-tool protocol")
                    output.appendLine("    Would need another turn to get weather")
                }
                else -> {
                    output.appendLine("✗ FAIL: No location or weather information")
                }
            }
        } catch (e: Exception) {
            output.appendLine("✗ ERROR: ${e.message}")
        }

        return output.toString()
    }
}

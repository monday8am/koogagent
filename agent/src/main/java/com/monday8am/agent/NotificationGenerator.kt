package com.monday8am.agent

import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.NotificationResult

class NotificationGenerator(
    private val agent: NotificationAgent,
) {
    private val systemPromptOld = "You are an nutritionist that generates short, motivating reminders for logging meals or water intake."
    private val systemPrompt = """
        You are a weather assistant. You have access to these tools:
        
        - GetLocationTool: Gets current location (no parameters)
        - GetWeatherTool: Gets weather for coordinates (requires: latitude, longitude)
        
        IMPORTANT: When you need to use a tool, you MUST respond ONLY with a tool call in this exact format:
        {"name": "ToolName", "arguments": {"param": "value"}}
        
        Do NOT write code or pseudo-code. Use actual tool calls.
        
        When asked about weather:
        1. Call GetLocationTool
        2. Wait for the location result
        3. Call GetWeatherTool with the latitude and longitude from step 1
        4. Present the weather information
    """.trimIndent()

    suspend fun generate(context: NotificationContext): NotificationResult {
        val prompt = buildPrompt(context)
        return try {
            val response =
                agent.generateMessage(
                    systemPrompt = systemPrompt,
                    userPrompt = prompt,
                )
            parseResponse(response)
        } catch (e: Exception) {
            println("NotificationGenerator: Error generating notification ${e.message}")
            fallback(context)
        }
    }

    // Consider weather when suggesting meals (e.g., hot soup on cold days, refreshing salads when hot, comfort food when rainy).
    // If weather information is relevant for this meal type, use the WeatherTool tool first to get current conditions, then tailor your suggestion accordingly.

    private fun buildPromptOld(context: NotificationContext): String =
        """
        Context:
        - Meal type: ${context.mealType}
        - Motivation level: ${context.motivationLevel}
        - Already logged: ${context.alreadyLogged}
        - Language: ${context.userLocale}
        - Country: ${context.country}

        Task: Generate a personalized meal notification.

        You have access to tools:
        - WeatherTool: Use this to fetch current weather conditions. Consider weather when suggesting meals (e.g., hot soup on cold days, refreshing salads when hot, comfort food when rainy).

        Generate a title (max 35 characters) and a body (max 160 characters) in plain JSON format: {"title":"...", "body":"...", "language":"en-US", "confidence":0.9}
        Use the language and suggest a meal or drink based on the country provided and the weather information obtained before.
        ${if (context.alreadyLogged) "The user has already logged something today - encourage them to continue." else "The user has not logged anything today - motivate them to start."}
        Say to user if you used the WeatherTool or not and why
        """.trimIndent()

    private fun buildPrompt(context: NotificationContext): String = "What's the weather today?"

    private fun parseResponse(response: String): NotificationResult {
        val cleanJson = response.removePrefix("```json\n").removeSuffix("\n```")
        val json = org.json.JSONObject(cleanJson)
        return NotificationResult(
            title = json.optString("title"),
            body = json.optString("body"),
            language = json.optString("language"),
            confidence = json.optDouble("confidence"),
            isFallback = false,
        )
    }

    private fun fallback(context: NotificationContext): NotificationResult {
        val (title, body) =
            when (context.mealType) {
                MealType.BREAKFAST ->
                    "Breakfast reminder" to
                        "Log your breakfast. Quick idea: toast with tomato and olive oil, or yogurt with fruit."
                MealType.LUNCH -> "Lunch time" to "Have you logged your lunch? Try a chickpea salad or grilled fish."
                MealType.SNACK -> "Healthy snack" to "A light snack is great now: seasonal fruit or a handful of nuts."
                MealType.DINNER -> "Balanced dinner" to "Log your dinner. Options: omelette with salad, vegetable soup."
                MealType.WATER -> "Hydration" to "On a hot day, a glass of water makes a difference. Log it now."
            }
        return NotificationResult(
            title = title,
            body = body,
            language = context.userLocale,
            confidence = 1.0,
            isFallback = true,
        )
    }
}

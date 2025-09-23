package com.monday8am.agent

class NotificationGenerator(
    private val agent: NotificationAgent,
) {

    private val systemPrompt = "You are an nutritionist that generates short, motivating reminders for logging meals or water intake."

    suspend fun generate(context: NotificationContext): NotificationResult {
        val prompt = buildPrompt(context)
        return try {
            val response = agent.generateMessage(
                systemPrompt = systemPrompt,
                userPrompt = prompt,
            )
            parseResponse(response)
        } catch (e: Exception) {
            fallback(context)
        }
    }

    private fun buildPrompt(context: NotificationContext): String {
        return """
            Context:
            - Meal type: ${context.mealType}
            - Motivation level: ${context.motivationLevel}
            - Weather: ${context.weather}
            - Already logged: ${context.alreadyLogged}
            - Language: ${context.userLocale}
            - Country: ${context.country}
            Generate a title (max 35 characters) and a body (max 160 characters) in JSON format: {"title":"...", "body":"...", "language":"en-US", "confidence":0.9}
        """.trimIndent()
    }

    private fun parseResponse(response: String): NotificationResult {
        // Parse JSON from LLM output (assume direct JSON for MVP)
        val json = org.json.JSONObject(response)
        return NotificationResult(
            title = json.optString("title"),
            body = json.optString("body"),
            language = json.optString("language"),
            confidence = json.optDouble("confidence"),
            isFallback = false
        )
    }

    private fun fallback(context: NotificationContext): NotificationResult {
        val (title, body) = when (context.mealType) {
            MealType.BREAKFAST -> "Breakfast reminder" to "Log your breakfast. Quick idea: toast with tomato and olive oil, or yogurt with fruit."
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
            isFallback = true
        )
    }
}
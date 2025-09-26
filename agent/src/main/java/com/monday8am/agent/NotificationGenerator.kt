package com.monday8am.agent

class NotificationGenerator(
    private val agent: NotificationAgent,
) {
    private val systemPrompt = "You are an nutritionist that generates short, motivating reminders for logging meals or water intake."

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

    private fun buildPrompt(context: NotificationContext): String =
        """
        Context:
        - Meal type: ${context.mealType}
        - Motivation level: ${context.motivationLevel}
        - Weather: ${context.weather}
        - Already logged: ${context.alreadyLogged}
        - Language: ${context.userLocale}
        - Country: ${context.country}
        Generate a title (max 35 characters) and a body (max 160 characters) in plain JSON format: {"title":"...", "body":"...", "language":"en-US", "confidence":0.9}
        Use the language and suggest a meal or drink based on the country provided.
        ${if (context.alreadyLogged) "- The user has already logged something today." else "the user has not logged anything today."}
        """.trimIndent()

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

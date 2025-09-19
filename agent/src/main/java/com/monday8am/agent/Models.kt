package com.monday8am.mcpserver

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, WATER }
enum class MotivationLevel { LOW, MEDIUM, HIGH }
enum class WeatherCondition { SUNNY, CLOUDY, RAINY, HOT, COLD }

data class NotificationContext(
    val mealType: MealType,
    val motivationLevel: MotivationLevel,
    val weather: WeatherCondition,
    val alreadyLogged: Boolean,
    val userLocale: String = "en-US",
    val country: String = "ES"
)

data class NotificationResult(
    val title: String,
    val body: String,
    val language: String,
    val confidence: Double,
    val isFallback: Boolean = false
)

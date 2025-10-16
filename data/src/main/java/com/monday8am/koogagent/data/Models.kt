package com.monday8am.koogagent.data

enum class WeatherCondition { SUNNY, CLOUDY, RAINY, HOT, COLD }

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, WATER }

enum class MotivationLevel { LOW, MEDIUM, HIGH }

data class NotificationContext(
    val mealType: MealType,
    val motivationLevel: MotivationLevel,
    val weather: WeatherCondition,
    val alreadyLogged: Boolean,
    val userLocale: String = "en-US",
    val country: String = "ES",
) {
    val formatted: String
        get() = "MealType: $mealType\nMotivationLevel: $motivationLevel\nWeather: $weather\nAlreadyLogged: $alreadyLogged"
}

data class NotificationResult(
    val title: String,
    val body: String,
    val language: String,
    val confidence: Double,
    val isFallback: Boolean = false,
    val errorMessage: String? = null,
) {
    val formatted: String
        get() = "Title:$title\nBody:$body\nLanguage:$language\nConfidence:$confidence"
}

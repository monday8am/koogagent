package com.monday8am.agent

const val DEFAULT_MAX_TOKEN = 1024
const val DEFAULT_TOPK = 40
const val DEFAULT_TOPP = 0.9f
const val DEFAULT_TEMPERATURE = 0.5f

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, WATER }

enum class MotivationLevel { LOW, MEDIUM, HIGH }

enum class WeatherCondition { SUNNY, CLOUDY, RAINY, HOT, COLD }

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

data class LocalLLModel(
    val path: String,
    val maxToken: Int = DEFAULT_MAX_TOKEN,
    val topK: Int = DEFAULT_TOPK,
    val topP: Float = DEFAULT_TOPP,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val shouldEnableImage: Boolean = false,
    val shouldEnableAudio: Boolean = false,
    val isGPUAccelerated: Boolean = true,
)

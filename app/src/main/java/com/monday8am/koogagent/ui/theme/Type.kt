package com.monday8am.koogagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.monday8am.koogagent.R

@OptIn(ExperimentalTextApi::class)
val bodyFontFamily =
    FontFamily(
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Normal,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(400),
                ),
        ),
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Medium,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(500),
                ),
        ),
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Bold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(700),
                ),
        ),
    )

@OptIn(ExperimentalTextApi::class)
val displayFontFamily =
    FontFamily(
        Font(
            resId = R.font.crimson_pro,
            weight = FontWeight.Normal,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(400),
                ),
        ),
        Font(
            resId = R.font.crimson_pro,
            weight = FontWeight.Medium,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(500),
                ),
        ),
        Font(
            resId = R.font.crimson_pro,
            weight = FontWeight.Bold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(700),
                ),
        ),
    )

val baseline = Typography()

val AppTypography =
    Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily),
        displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily),
        displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily),
        titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily),
        titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily),
        titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
        labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
        labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
        labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
    )

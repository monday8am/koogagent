package com.monday8am.koogagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = SolarizedBlue,
        secondary = SolarizedCyan,
        tertiary = SolarizedOrange,
        error = SolarizedRed,
        background = SolarizedBase03,
        surface = SolarizedBase03,
        onPrimary = SolarizedBase3,
        onSecondary = SolarizedBase3,
        onTertiary = SolarizedBase3,
        onError = SolarizedBase3,
        onBackground = SolarizedBase0,
        onSurface = SolarizedBase0,
        surfaceVariant = SolarizedBase02,
        onSurfaceVariant = SolarizedBase1,
        primaryContainer = SolarizedBlue.copy(alpha = 0.2f),
        onPrimaryContainer = SolarizedBlue,
        secondaryContainer = SolarizedCyan.copy(alpha = 0.2f),
        onSecondaryContainer = SolarizedCyan,
        tertiaryContainer = SolarizedOrange.copy(alpha = 0.2f),
        onTertiaryContainer = SolarizedOrange,
        errorContainer = SolarizedRed.copy(alpha = 0.2f),
        onErrorContainer = SolarizedRed,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SolarizedBlue,
        secondary = SolarizedCyan,
        tertiary = SolarizedOrange,
        error = SolarizedRed,
        background = SolarizedBase3,
        surface = SolarizedBase3,
        onPrimary = SolarizedBase3,
        onSecondary = SolarizedBase3,
        onTertiary = SolarizedBase3,
        onError = SolarizedBase3,
        onBackground = SolarizedBase00,
        onSurface = SolarizedBase00,
        surfaceVariant = SolarizedBase2,
        onSurfaceVariant = SolarizedBase01,
        primaryContainer = SolarizedBlue.copy(alpha = 0.15f),
        onPrimaryContainer = SolarizedBlue,
        secondaryContainer = SolarizedCyan.copy(alpha = 0.15f),
        onSecondaryContainer = SolarizedCyan,
        tertiaryContainer = SolarizedOrange.copy(alpha = 0.15f),
        onTertiaryContainer = SolarizedOrange,
        errorContainer = SolarizedRed.copy(alpha = 0.15f),
        onErrorContainer = SolarizedRed,
    )

@Composable
fun KoogAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColorScheme
            }

            else -> {
                LightColorScheme
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

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
        // Primary brand color (Purple)
        primary = BrandPurple,
        onPrimary = Color.White,
        primaryContainer = DarkThinkingContainer,
        onPrimaryContainer = DarkThinkingOnContainer,

        // Secondary color (Blue for tools)
        secondary = DarkToolBorder,
        onSecondary = Color.White,
        secondaryContainer = DarkToolContainer,
        onSecondaryContainer = DarkToolOnContainer,

        // Tertiary color (Green for success)
        tertiary = DarkSuccessBorder,
        onTertiary = Color.White,
        tertiaryContainer = DarkSuccessContainer,
        onTertiaryContainer = DarkSuccessOnContainer,

        // Error colors
        error = DarkErrorBorder,
        onError = Color.White,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkErrorOnContainer,

        // Background and surface
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurface,

        // Outline
        outline = DarkOutline,
        outlineVariant = DarkOutline.copy(alpha = 0.5f),
    )

private val LightColorScheme =
    lightColorScheme(
        // Primary brand color (Purple)
        primary = BrandPurple,
        onPrimary = Color.White,
        primaryContainer = LightThinkingContainer,
        onPrimaryContainer = LightThinkingOnContainer,

        // Secondary color (Blue for tools)
        secondary = LightToolBorder,
        onSecondary = Color.White,
        secondaryContainer = LightToolContainer,
        onSecondaryContainer = LightToolOnContainer,

        // Tertiary color (Green for success)
        tertiary = LightSuccessBorder,
        onTertiary = Color.White,
        tertiaryContainer = LightSuccessContainer,
        onTertiaryContainer = LightSuccessOnContainer,

        // Error colors
        error = LightErrorBorder,
        onError = Color.White,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightErrorOnContainer,

        // Background and surface
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurface,

        // Outline
        outline = LightOutline,
        outlineVariant = LightOutline.copy(alpha = 0.5f),
    )

@Composable
fun KoogAgentTheme(
    darkTheme: Boolean = true,
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

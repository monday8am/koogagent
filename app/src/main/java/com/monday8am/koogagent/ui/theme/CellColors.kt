package com.monday8am.koogagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Cell color definitions for LazyColumn items.
 * Provides high contrast, semantically meaningful colors for different cell types.
 */
data class CellColorScheme(
    val container: Color,
    val onContainer: Color,
    val border: Color,
    val text: Color,
)

/**
 * Returns colors for "Thinking" cells (AI reasoning/analysis)
 * - Dark mode: Deep purple background with light purple text
 * - Light mode: Light purple background with deep purple text
 */
@Composable
fun thinkingCellColors(): CellColorScheme {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        CellColorScheme(
            container = DarkThinkingContainer,
            onContainer = DarkThinkingOnContainer,
            border = DarkThinkingBorder,
            text = DarkThinkingText,
        )
    } else {
        CellColorScheme(
            container = LightThinkingContainer,
            onContainer = LightThinkingOnContainer,
            border = LightThinkingBorder,
            text = LightThinkingText,
        )
    }
}

/**
 * Returns colors for "Tool" cells (function calls, API requests, etc.)
 * - Dark mode: Deep blue background with light blue text
 * - Light mode: Light blue background with deep blue text
 */
@Composable
fun toolCellColors(): CellColorScheme {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        CellColorScheme(
            container = DarkToolContainer,
            onContainer = DarkToolOnContainer,
            border = DarkToolBorder,
            text = DarkToolText,
        )
    } else {
        CellColorScheme(
            container = LightToolContainer,
            onContainer = LightToolOnContainer,
            border = LightToolBorder,
            text = LightToolText,
        )
    }
}

/**
 * Returns colors for "Success" cells (successful operations, confirmations)
 * - Dark mode: Deep green background with light green text
 * - Light mode: Light green background with deep green text
 */
@Composable
fun successCellColors(): CellColorScheme {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        CellColorScheme(
            container = DarkSuccessContainer,
            onContainer = DarkSuccessOnContainer,
            border = DarkSuccessBorder,
            text = DarkSuccessText,
        )
    } else {
        CellColorScheme(
            container = LightSuccessContainer,
            onContainer = LightSuccessOnContainer,
            border = LightSuccessBorder,
            text = LightSuccessText,
        )
    }
}

/**
 * Returns colors for "Error/Fail" cells (errors, failures, warnings)
 * - Dark mode: Deep red background with light red text
 * - Light mode: Light red background with deep red text
 */
@Composable
fun errorCellColors(): CellColorScheme {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        CellColorScheme(
            container = DarkErrorContainer,
            onContainer = DarkErrorOnContainer,
            border = DarkErrorBorder,
            text = DarkErrorText,
        )
    } else {
        CellColorScheme(
            container = LightErrorContainer,
            onContainer = LightErrorOnContainer,
            border = LightErrorBorder,
            text = LightErrorText,
        )
    }
}

/**
 * Alternative: Access cell colors through Material3 ColorScheme
 */
object CellTheme {
    /**
     * Use primaryContainer/onPrimaryContainer for Thinking cells
     */
    val ColorScheme.thinkingContainer: Color
        get() = primaryContainer

    val ColorScheme.onThinkingContainer: Color
        get() = onPrimaryContainer

    /**
     * Use secondaryContainer/onSecondaryContainer for Tool cells
     */
    val ColorScheme.toolContainer: Color
        get() = secondaryContainer

    val ColorScheme.onToolContainer: Color
        get() = onSecondaryContainer

    /**
     * Use tertiaryContainer/onTertiaryContainer for Success cells
     */
    val ColorScheme.successContainer: Color
        get() = tertiaryContainer

    val ColorScheme.onSuccessContainer: Color
        get() = onTertiaryContainer

    /**
     * Use errorContainer/onErrorContainer for Error/Fail cells
     */
    val ColorScheme.failContainer: Color
        get() = errorContainer

    val ColorScheme.onFailContainer: Color
        get() = onErrorContainer
}

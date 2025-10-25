package com.monday8am.presentation.notifications

import app.cash.turbine.ReceiveTurbine

/**
 * Test utilities for ViewModel testing.
 */

/**
 * Skips the initial state emission that occurs due to StateFlow's behavior
 * and the ViewModel's auto-initialization. This makes tests more explicit
 * about waiting for the initialized state before testing actual behavior.
 *
 * Usage:
 * ```
 * viewModel.uiState.test {
 *     skipInitialState()
 *     // Now test actual behavior
 *     viewModel.onUiAction(SomeAction)
 *     val state = awaitItem()
 *     // assertions...
 * }
 * ```
 */
suspend fun <T> ReceiveTurbine<T>.skipInitialState(): T = awaitItem()

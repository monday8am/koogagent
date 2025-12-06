package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.koogagent.ui.theme.KoogAgentTheme

/**
 * Model Selector Screen - Entry point for model selection.
 *
 * @param onNavigateToNotification Callback when user wants to proceed to notification screen
 * @param viewModelFactory Factory for creating ViewModel (injected from MainActivity)
 * @param modifier Optional modifier for composable
 */
@Composable
fun ModelSelectorScreen(
    onNavigateToNotification: () -> Unit,
    viewModelFactory: ModelSelectorViewModelFactory,
    modifier: Modifier = Modifier,
    viewModel: ModelSelectorViewModel = viewModel(factory = viewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModelSelectorContent(
        onNavigateToNotification = onNavigateToNotification,
        modifier = modifier,
    )
}

/**
 * Stateless UI content for model selector.
 * Separated for easier testing and preview.
 */
@Composable
private fun ModelSelectorContent(
    onNavigateToNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = "Model Selector",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Placeholder screen for future model selection",
            style = MaterialTheme.typography.bodyLarge,
        )

        Button(onClick = onNavigateToNotification) {
            Text("Go to Notifications")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSelectorScreenPreview() {
    KoogAgentTheme {
        ModelSelectorContent(
            onNavigateToNotification = {},
        )
    }
}

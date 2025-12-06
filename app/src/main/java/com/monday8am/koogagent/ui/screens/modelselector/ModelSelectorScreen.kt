package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.modelselector.DownloadStatus
import com.monday8am.presentation.modelselector.ModelInfo
import com.monday8am.presentation.modelselector.UiAction

/**
 * Model Selector Screen - Entry point for model selection.
 *
 * @param onNavigateToNotification Callback when user wants to proceed to notification screen (receives modelId)
 * @param viewModelFactory Factory for creating ViewModel (injected from MainActivity)
 * @param modifier Optional modifier for composable
 */
@Composable
fun ModelSelectorScreen(
    onNavigateToNotification: (String) -> Unit,
    viewModelFactory: ModelSelectorViewModelFactory,
    modifier: Modifier = Modifier,
    viewModel: AndroidModelSelectorViewModel = viewModel(factory = viewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = "Select a Model",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp),
        )

        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Model list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(
                items = uiState.models,
                key = { it.config.modelId },
            ) { modelInfo ->
                ModelCard(
                    modelInfo = modelInfo,
                    isSelected = modelInfo.config.modelId == uiState.selectedModelId,
                    onDownloadClick = {
                        viewModel.onUiAction(UiAction.DownloadModel(modelInfo.config.modelId))
                    },
                    onSelectClick = {
                        viewModel.onUiAction(UiAction.SelectModel(modelInfo.config.modelId))
                    },
                )
            }
        }

        // Bottom actions
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cancel button (only shown if downloading)
            if (uiState.currentDownload != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.onUiAction(UiAction.CancelCurrentDownload)
                    },
                ) {
                    Text("Cancel Download")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Navigate button (only enabled if model selected and downloaded)
            Button(
                onClick = {
                    uiState.selectedModelId?.let { modelId ->
                        onNavigateToNotification(modelId)
                    }
                },
                enabled =
                    uiState.selectedModelId != null &&
                        uiState.models.find { it.config.modelId == uiState.selectedModelId }?.isDownloaded == true,
            ) {
                Text("Go to Notifications")
            }
        }
    }
}

/**
 * Card displaying model information and download status
 */
@Composable
private fun ModelCard(
    modelInfo: ModelInfo,
    isSelected: Boolean,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when {
            modelInfo.isDownloaded -> Color(0xFF2D5016) // Dark green
            else -> Color(0xFF424242) // Dark grey
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = modelInfo.isDownloaded) { onSelectClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isSelected) BorderStroke(2.dp, Color.Green) else null,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Model info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modelInfo.config.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${modelInfo.config.parameterCount}B params • ${modelInfo.config.contextLength} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    text = "Library: ${modelInfo.config.inferenceLibrary.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            when (val downloadStatus = modelInfo.downloadStatus) {
                is DownloadStatus.NotStarted -> {
                    Button(onClick = onDownloadClick) {
                        Text("Download")
                    }
                }

                is DownloadStatus.Queued -> {
                    Text(
                        text = "Queued",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow,
                    )
                }

                is DownloadStatus.Downloading -> {
                    CircularProgressWithText(
                        progress = downloadStatus.progress / 100f,
                        text = "${downloadStatus.progress.toInt()}%",
                    )
                }

                is DownloadStatus.Completed -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color.Green,
                        modifier = Modifier.size(32.dp),
                    )
                }

                is DownloadStatus.Failed -> {
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = Color.Red,
                        )
                        TextButton(onClick = onDownloadClick) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular progress indicator with centered text
 */
@Composable
private fun CircularProgressWithText(
    progress: Float,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(48.dp),
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Color.White,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSelectorScreenPreview() {
    KoogAgentTheme {
        // Preview removed as it requires ViewModel
    }
}

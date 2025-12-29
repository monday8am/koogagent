package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monday8am.koogagent.Dependencies
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.modelselector.DownloadInfo
import com.monday8am.presentation.modelselector.DownloadStatus
import com.monday8am.presentation.modelselector.ModelInfo
import com.monday8am.presentation.modelselector.ModelSelectorViewModelImpl
import com.monday8am.presentation.modelselector.UiAction
import com.monday8am.presentation.modelselector.UiState

/**
 * Model Selector Screen - Entry point for model selection.
 */
@Composable
fun ModelSelectorScreen(onNavigateToNotification: (String) -> Unit, onNavigateToTesting: (String) -> Unit) {
    val viewModel =
        remember {
            AndroidModelSelectorViewModel(
                ModelSelectorViewModelImpl(
                    availableModels = ModelCatalog.ALL_MODELS,
                    modelDownloadManager = Dependencies.modelDownloadManager,
                ),
            )
        }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModelSelectorScreenContent(
        uiState = uiState,
        modifier = Modifier,
        onAction = viewModel::onUiAction,
        onNavigateToNotification = onNavigateToNotification,
        onNavigateToTesting = onNavigateToTesting,
    )
}

@Composable
private fun ModelSelectorScreenContent(
    uiState: UiState,
    modifier: Modifier = Modifier,
    onAction: (UiAction) -> Unit = {},
    onNavigateToNotification: (String) -> Unit = {},
    onNavigateToTesting: (String) -> Unit = {},
) {
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
                        onAction(UiAction.DownloadModel(modelInfo.config.modelId))
                    },
                    onSelectClick = {
                        onAction(UiAction.SelectModel(modelInfo.config.modelId))
                    },
                )
            }
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Delete button - visible only when downloaded model is selected
            val downloadStatus =
                uiState.models.find { it.config.modelId == uiState.selectedModelId }?.downloadStatus ?: DownloadStatus.NotStarted

            when (downloadStatus) {
                is DownloadStatus.Downloading,
                is DownloadStatus.Queued,
                -> {
                    Button(
                        onClick = {
                            onAction(UiAction.CancelCurrentDownload)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancel download",
                        )
                    }
                }

                is DownloadStatus.Completed -> {
                    Button(
                        onClick = {
                            uiState.selectedModelId?.let { modelId ->
                                onAction(UiAction.DeleteModel(modelId))
                            }
                        },
                        colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                        )
                    }
                }

                else -> {
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    uiState.selectedModelId?.let(onNavigateToTesting)
                },
                enabled = downloadStatus == DownloadStatus.Completed,
            ) {
                Text("Model Tests")
            }

            Button(
                onClick = {
                    uiState.selectedModelId?.let(onNavigateToNotification)
                },
                enabled = downloadStatus == DownloadStatus.Completed,
            ) {
                Text("Agentic Test")
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForward,
                    contentDescription = "Go forward",
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSelectorScreenPreview() {
    KoogAgentTheme {
        ModelSelectorScreenContent(
            uiState =
            UiState(
                models =
                ModelCatalog.ALL_MODELS.map {
                    ModelInfo(
                        config = it,
                        isDownloaded = it.modelId != ModelCatalog.GEMMA3_1B.modelId,
                        downloadStatus =
                        if (it.modelId ==
                            ModelCatalog.GEMMA3_1B.modelId
                        ) {
                            DownloadStatus.Downloading(10f)
                        } else {
                            DownloadStatus.Completed
                        },
                    )
                },
                selectedModelId = ModelCatalog.GEMMA3_1B.modelId,
                currentDownload = DownloadInfo(ModelCatalog.GEMMA3_1B.modelId, 10f),
                statusMessage = "Downloading model: GEMMA3_1B",
            ),
        )
    }
}

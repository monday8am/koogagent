package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.koogagent.Dependencies
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.ui.screens.testing.InitializationIndicator
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
    val viewModel: AndroidModelSelectorViewModel =
        viewModel {
            AndroidModelSelectorViewModel(
                ModelSelectorViewModelImpl(
                    modelCatalogProvider = Dependencies.modelCatalogProvider,
                    modelDownloadManager = Dependencies.modelDownloadManager,
                    modelRepository = Dependencies.modelRepository,
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
            modifier = Modifier.padding(bottom = 8.dp),
        )

        uiState.catalogError?.let { error ->
            Text(
                text = "Using cached models: $error",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800), // Orange warning color
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (uiState.isLoadingCatalog) {
            InitializationIndicator(
                message = "Loading models from Hugging Face...",
                modifier = Modifier.weight(1f),
            )
        } else {
            ModelList(
                models = uiState.models,
                selectedModelId = uiState.selectedModelId,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
        }

        ToolBar(
            models = uiState.models,
            selectedModelId = uiState.selectedModelId,
            onAction = onAction,
            onNavigateToTesting = onNavigateToTesting,
            onNavigateToNotification = onNavigateToNotification,
        )
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
                isLoadingCatalog = false,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelSelectorScreenPreview_Loading() {
    KoogAgentTheme {
        ModelSelectorScreenContent(
            uiState =
            UiState(
                isLoadingCatalog = true,
                statusMessage = "Loading models from Hugging Face...",
            ),
        )
    }
}

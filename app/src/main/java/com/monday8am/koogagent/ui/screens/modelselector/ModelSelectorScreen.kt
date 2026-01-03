package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.monday8am.presentation.modelselector.GroupingMode
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
                    modelDownloadManager = Dependencies.modelDownloadManager,
                    modelRepository = Dependencies.modelRepository,
                    authRepository = Dependencies.authRepository,
                ),
            )
        }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    var showAuthDialog: Boolean by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.models) {
        if (selectedModelId != null && uiState.models.none { it.config.modelId == selectedModelId }) {
            selectedModelId = null
        }
    }

    val displayStatusMessage = when {
        uiState.isLoadingCatalog -> "Loading models from Hugging Face..."
        uiState.currentDownload != null -> "Downloading: ${uiState.currentDownload?.modelId?.take(20)}..."
        selectedModelId != null -> {
            val name = uiState.models.find { it.config.modelId == selectedModelId }?.config?.displayName
            "Selected: $name"
        }

        else -> uiState.statusMessage
    }

    ModelSelectorScreenContent(
        uiState = uiState,
        selectedModelId = selectedModelId,
        statusMessage = displayStatusMessage,
        modifier = Modifier,
        onIntent = viewModel::onUiAction,
        onSelectModel = { selectedModelId = it },
        onShowDialog = { showAuthDialog = true },
        onNavigateToNotification = onNavigateToNotification,
        onNavigateToTesting = onNavigateToTesting,
    )

    if (showAuthDialog) {
        AuthDialog(
            onDismiss = { showAuthDialog = false },
            onSubmit = { token -> viewModel.onUiAction(UiAction.SubmitToken(token)) }
        )
    }
}

@Composable
private fun ModelSelectorScreenContent(
    uiState: UiState,
    selectedModelId: String?,
    statusMessage: String,
    modifier: Modifier = Modifier,
    onIntent: (UiAction) -> Unit = {},
    onSelectModel: (String) -> Unit = {},
    onShowDialog: () -> Unit = {},
    onNavigateToNotification: (String) -> Unit = {},
    onNavigateToTesting: (String) -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {

        ModelSelectorHeader(
            statusMessage = statusMessage,
            onIntent = onIntent,
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.catalogError?.let { error ->
            Text(
                text = "Using cached models: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
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
                groups = uiState.groupedModels,
                selectedModelId = selectedModelId,
                onIntent = { action ->
                    if (action is UiAction.DownloadModel) {
                        val model = uiState.models.find { it.config.modelId == action.modelId }
                        if (model?.isGated == true && !uiState.isLoggedIn) {
                            onShowDialog()
                        } else {
                            onSelectModel(action.modelId)
                            onIntent(action)
                        }
                    } else {
                        onIntent(action)
                    }
                },
                onSelectModel = onSelectModel,
                modifier = Modifier.weight(1f),
            )
        }

        ToolBar(
            models = uiState.models,
            selectedModelId = selectedModelId,
            isLoggedIn = uiState.isLoggedIn,
            onAction = onIntent,
            onShowAuthDialog = onShowDialog,
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
                    currentDownload = DownloadInfo(ModelCatalog.GEMMA3_1B.modelId, 10f),
                    statusMessage = "Downloading model: GEMMA3_1B",
                    isLoadingCatalog = false,
                ),
            selectedModelId = ModelCatalog.GEMMA3_1B.modelId,
            statusMessage = "Selected: GEMMA3_1B",
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
            selectedModelId = null,
            statusMessage = "Loading models from Hugging Face...",
        )
    }
}

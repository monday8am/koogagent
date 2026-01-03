package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.modelselector.DownloadStatus
import com.monday8am.presentation.modelselector.ModelInfo
import com.monday8am.presentation.modelselector.UiAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun ToolBar(
    models: ImmutableList<ModelInfo>,
    selectedModelId: String?,
    isLoggedIn: Boolean,
    onAction: (UiAction) -> Unit,
    onShowAuthDialog: () -> Unit,
    onNavigateToTesting: (String) -> Unit,
    onNavigateToNotification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconSizeModifier = Modifier.size(18.dp)

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                if (isLoggedIn) {
                    onAction(UiAction.Logout)
                } else {
                    onShowAuthDialog()
                }
            },
            colors = if (isLoggedIn) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Icon(
                imageVector = if (isLoggedIn) Icons.Default.Person else Icons.Default.PersonOff,
                contentDescription = if (isLoggedIn) "Logout" else "Login",
                modifier = iconSizeModifier,
            )
        }

        // Delete button - visible only when downloaded model is selected
        val downloadStatus =
            models.find { it.config.modelId == selectedModelId }?.downloadStatus ?: DownloadStatus.NotStarted

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
                        modifier = iconSizeModifier,
                    )
                }
            }

            is DownloadStatus.Completed -> {
                Button(
                    onClick = {
                        selectedModelId?.let { modelId ->
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
                        modifier = iconSizeModifier,
                    )
                }
            }

            else -> {
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                selectedModelId?.let(onNavigateToTesting)
            },
            enabled = downloadStatus == DownloadStatus.Completed,
        ) {
            Text("Function")
//            Icon(
//                imageVector = Icons.Default.Memory,
//                contentDescription = "Go forward",
//                modifier = iconSizeModifier,
//            )
        }

        Button(
            onClick = {
                selectedModelId?.let(onNavigateToNotification)
            },
            enabled = downloadStatus == DownloadStatus.Completed,
        ) {
            Text("Agentic")
//            Icon(
//                imageVector = Icons.Filled.Psychology,
//                contentDescription = "Go forward",
//                modifier = iconSizeModifier,
//            )
        }
    }
}

@Preview(name = "Not Downloaded", showBackground = true)
@Composable
private fun ToolBarPreview_NotDownloaded() {
    KoogAgentTheme {
        ToolBar(
            models = listOf(
                ModelInfo(
                    config = ModelCatalog.QWEN3_0_6B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.NotStarted,
                ),
            ).toImmutableList(),
            selectedModelId = ModelCatalog.QWEN3_0_6B.modelId,
            isLoggedIn = false,
            onAction = {},
            onShowAuthDialog = {},
            onNavigateToTesting = {},
            onNavigateToNotification = {},
        )
    }
}

@Preview(name = "Downloaded & Selected", showBackground = true)
@Composable
private fun ToolBarPreview_DownloadedSelected() {
    KoogAgentTheme {
        ToolBar(
            models =
            listOf(
                ModelInfo(
                    config = ModelCatalog.HAMMER2_1_0_5B,
                    isDownloaded = true,
                    downloadStatus = DownloadStatus.Completed,
                ),
            ).toImmutableList(),
            selectedModelId = ModelCatalog.HAMMER2_1_0_5B.modelId,
            isLoggedIn = true,
            onAction = {},
            onShowAuthDialog = {},
            onNavigateToTesting = {},
            onNavigateToNotification = {},
        )
    }
}

@Preview(name = "Downloading", showBackground = true)
@Composable
private fun ToolBarPreview_Downloading() {
    KoogAgentTheme {
        ToolBar(
            models =
            listOf(
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Downloading(42f),
                ),
            ).toImmutableList(),
            selectedModelId = ModelCatalog.GEMMA3_1B.modelId,
            isLoggedIn = true,
            onAction = {},
            onShowAuthDialog = {},
            onNavigateToTesting = {},
            onNavigateToNotification = {},
        )
    }
}

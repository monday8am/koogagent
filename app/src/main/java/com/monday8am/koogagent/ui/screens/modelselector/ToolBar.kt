package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monday8am.presentation.modelselector.DownloadStatus
import com.monday8am.presentation.modelselector.ModelInfo
import com.monday8am.presentation.modelselector.UiAction

@Composable
internal fun ToolBar(
    models: List<ModelInfo>,
    selectedModelId: String?,
    onAction: (UiAction) -> Unit,
    onNavigateToTesting: (String) -> Unit,
    onNavigateToNotification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = "Go forward",
            )
        }

        Button(
            onClick = {
                selectedModelId?.let(onNavigateToNotification)
            },
            enabled = downloadStatus == DownloadStatus.Completed,
        ) {
            Text("Agentic")
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = "Go forward",
            )
        }
    }
}

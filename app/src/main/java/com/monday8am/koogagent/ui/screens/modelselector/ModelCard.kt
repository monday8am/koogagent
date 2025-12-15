package com.monday8am.koogagent.ui.screens.modelselector

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.modelselector.DownloadStatus
import com.monday8am.presentation.modelselector.ModelInfo

/**
 * Card displaying model information and download status
 */
@SuppressLint("DefaultLocale")
@Composable
internal fun ModelCard(
    modelInfo: ModelInfo,
    isSelected: Boolean,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when {
            modelInfo.isDownloaded -> Color(0xFF2D5016)

            // Dark green
            else -> Color(0xFF424242) // Dark grey
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(true) { onSelectClick() },
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
                        text = "${String.format("%.1f", downloadStatus.progress)}%",
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

@Preview(name = "Not Downloaded", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_NotDownloaded() {
    KoogAgentTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.QWEN3_0_6B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.NotStarted,
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Downloading", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Downloading() {
    KoogAgentTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Downloading(42f),
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Queued", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Queued() {
    KoogAgentTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.GEMMA3_1B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Queued,
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Downloaded & Selected", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_DownloadedSelected() {
    KoogAgentTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.HAMMER2_1_0_5B,
                    isDownloaded = true,
                    downloadStatus = DownloadStatus.Completed,
                ),
            isSelected = true,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

@Preview(name = "Download Failed", showBackground = true, widthDp = 380)
@Composable
private fun ModelCardPreview_Failed() {
    KoogAgentTheme {
        ModelCard(
            modelInfo =
                ModelInfo(
                    config = ModelCatalog.HAMMER2_1_0_5B,
                    isDownloaded = false,
                    downloadStatus = DownloadStatus.Failed("Network error"),
                ),
            isSelected = false,
            onDownloadClick = {},
            onSelectClick = {},
        )
    }
}

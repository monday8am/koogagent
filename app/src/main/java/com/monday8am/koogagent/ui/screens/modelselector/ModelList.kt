package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.InferenceLibrary
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.modelselector.DownloadStatus
import com.monday8am.presentation.modelselector.ModelInfo
import com.monday8am.presentation.modelselector.UiAction

@Composable
internal fun ModelList(
    models: List<ModelInfo>,
    selectedModelId: String?,
    onIntent: (UiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = models,
            key = { it.config.modelId },
        ) { modelInfo ->
            ModelCard(
                modelInfo = modelInfo,
                isSelected = modelInfo.config.modelId == selectedModelId,
                onDownloadClick = {
                    onIntent(UiAction.DownloadModel(modelInfo.config.modelId))
                },
                onSelectClick = {
                    onIntent(UiAction.SelectModel(modelInfo.config.modelId))
                },
            )
        }
    }
}

@Preview
@Composable
private fun ModelListPreview() {
    val sampleModels = listOf(
        ModelInfo(
            config = ModelConfiguration(
                displayName = "Qwen3 0.6B (LiteRT, 4K context)",
                modelFamily = "qwen3",
                parameterCount = 0.6f,
                quantization = "int8",
                contextLength = 4096,
                downloadUrl = "https://example.com/model.litertlm",
                bundleFilename = "model1.litertlm",
                inferenceLibrary = InferenceLibrary.LITERT,
                hardwareAcceleration = HardwareBackend.CPU_ONLY
            ),
            isDownloaded = true,
            downloadStatus = DownloadStatus.Completed
        ),
        ModelInfo(
            config = ModelConfiguration(
                displayName = "Gemma3 1B (LiteRT, 4K context)",
                modelFamily = "gemma3",
                parameterCount = 1.0f,
                quantization = "int4",
                contextLength = 4096,
                downloadUrl = "https://example.com/model2.litertlm",
                bundleFilename = "model2.litertlm",
                inferenceLibrary = InferenceLibrary.LITERT,
                hardwareAcceleration = HardwareBackend.GPU_SUPPORTED
            ),
            isDownloaded = false,
            downloadStatus = DownloadStatus.NotStarted
        )
    )

    KoogAgentTheme {
        ModelList(
            models = sampleModels,
            selectedModelId = sampleModels.first().config.modelId,
            onIntent = {}
        )
    }
}

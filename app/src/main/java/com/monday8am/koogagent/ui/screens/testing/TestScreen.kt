package com.monday8am.koogagent.ui.screens.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monday8am.koogagent.Dependencies
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.InferenceEngineFactory
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.testing.TestUiAction
import com.monday8am.presentation.testing.TestViewModelImpl

/**
 * Test Screen - Dedicated screen for running model regression tests.
 */
@Composable
fun TestScreen(modelId: String) {
    val viewModel: AndroidTestViewModel =
        remember(modelId) {
            val selectedModel = ModelCatalog.findById(modelId) ?: ModelCatalog.DEFAULT

            val inferenceEngine =
                InferenceEngineFactory.create(
                    context = Dependencies.appContext,
                    inferenceLibrary = selectedModel.inferenceLibrary,
                    liteRtTools = Dependencies.nativeTools,
                    mediaPipeTools = Dependencies.mediaPipeTools,
                )

            val modelPath =
                (Dependencies.modelDownloadManager as ModelDownloadManagerImpl)
                    .getModelPath(selectedModel.bundleFilename)

            val impl =
                TestViewModelImpl(
                    selectedModel = selectedModel,
                    modelPath = modelPath,
                    inferenceEngine = inferenceEngine,
                )

            AndroidTestViewModel(impl, selectedModel)
        }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TestContent(
        logMessages = state.logMessages,
        selectedModel = state.selectedModel,
        isRunning = state.isRunning,
        onRunTests = { viewModel.onUiAction(TestUiAction.RunTests) },
        modifier = Modifier,
    )
}

@Composable
private fun TestContent(
    logMessages: List<String>,
    selectedModel: ModelConfiguration,
    isRunning: Boolean,
    onRunTests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        ModelInfoCard(model = selectedModel, modifier = Modifier.padding(top = 32.dp))

        LogPanel(logMessages = logMessages)

        Button(
            onClick = onRunTests,
            enabled = !isRunning,
        ) {
            Text(
                text = if (isRunning) "Running..." else "Run Tests",
            )
        }
    }
}

@Composable
private fun ModelInfoCard(
    model: ModelConfiguration,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Model: ${model.displayName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${model.parameterCount}B params • ${model.contextLength} tokens",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Library: ${model.inferenceLibrary.name}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LogPanel(
    logMessages: List<String>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val textLog = logMessages.joinToString("\n")

    LaunchedEffect(logMessages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(350.dp)
                .verticalScroll(scrollState)
                .background(Color(0xFF000080)),
    ) {
        Text(
            text = textLog,
            color = Color(0xFF00FF00),
            textAlign = TextAlign.Start,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TestContentPreview() {
    KoogAgentTheme {
        TestContent(
            logMessages = listOf("Model: Test Model", "Ready to run tests"),
            selectedModel = ModelCatalog.DEFAULT,
            isRunning = false,
            onRunTests = { },
        )
    }
}

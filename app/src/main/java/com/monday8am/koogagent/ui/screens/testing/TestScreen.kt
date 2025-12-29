package com.monday8am.koogagent.ui.screens.testing

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monday8am.koogagent.Dependencies
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.InferenceEngineFactory
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.testing.TestResultFrame
import com.monday8am.presentation.testing.TestUiAction
import com.monday8am.presentation.testing.TestViewModelImpl
import com.monday8am.presentation.testing.ValidationResult

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
        frames = state.frames.values,
        selectedModel = state.selectedModel,
        isRunning = state.isRunning,
        isInitializing = state.isInitializing,
        onRunTests = { viewModel.onUiAction(TestUiAction.RunTests) },
        modifier = Modifier,
    )
}

@Composable
private fun TestContent(
    frames: Collection<TestResultFrame>,
    selectedModel: ModelConfiguration,
    isRunning: Boolean,
    isInitializing: Boolean,
    onRunTests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ModelInfoCard(model = selectedModel)

        if (isInitializing) {
            InitializationIndicator()
        }

        TestResultsList(
            frames = frames,
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = onRunTests,
            enabled = !isRunning && !isInitializing,
        ) {
            Text(
                text = if (isRunning) "Running..." else "Run Tests",
            )
        }
    }
}

@Composable
private fun ModelInfoCard(model: ModelConfiguration, modifier: Modifier = Modifier) {
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
private fun TestResultsList(frames: Collection<TestResultFrame>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest item
    LaunchedEffect(frames.size) {
        if (frames.isNotEmpty()) {
            listState.animateScrollToItem(frames.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier =
        modifier.fillMaxWidth(),
        verticalArrangement = spacedBy(8.dp),
    ) {
        items(
            items = frames.toList(),
            key = { frame -> frame.id },
        ) { frame ->
            when (frame) {
                is TestResultFrame.Description -> DescriptionCell(frame)
                is TestResultFrame.Query -> QueryCell(frame)
                is TestResultFrame.Thinking -> ThinkingCell(frame)
                is TestResultFrame.Tool -> ToolCell(frame)
                is TestResultFrame.Content -> ContentCell(frame)
                is TestResultFrame.Validation -> ValidationCell(frame)
            }
        }
    }
}

@Composable
private fun DescriptionCell(frame: TestResultFrame.Description) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = frame.testName,
                style = MaterialTheme.typography.titleSmall,
            )
            if (frame.description.isNotEmpty()) {
                Text(
                    text = frame.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "System Prompt: ${frame.systemPrompt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueryCell(frame: TestResultFrame.Query) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "User",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = frame.query,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun InitializationIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Text(
            text = "Initializing model...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TestContentPreview() {
    KoogAgentTheme {
        TestContent(
            frames =
            listOf(
                TestResultFrame.Content(
                    testName = "TEST 0: Basic Response",
                    chunk = "",
                    accumulator = "Hello! I'm doing great, thanks for asking!",
                ),
                TestResultFrame.Validation(
                    testName = "TEST 0: Basic Response",
                    result = ValidationResult.Pass("Valid response received"),
                    duration = 1234,
                    fullContent = "Hello! I'm doing great, thanks for asking!",
                ),
            ),
            selectedModel = ModelCatalog.DEFAULT,
            isRunning = false,
            isInitializing = true,
            onRunTests = { },
        )
    }
}

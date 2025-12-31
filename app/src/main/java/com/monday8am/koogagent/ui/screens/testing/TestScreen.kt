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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.koogagent.Dependencies
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.InferenceEngineFactory
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.presentation.testing.TestResultFrame
import com.monday8am.presentation.testing.TestStatus
import com.monday8am.presentation.testing.TestUiAction
import com.monday8am.presentation.testing.TestViewModelImpl
import com.monday8am.presentation.testing.ValidationResult

@Composable
fun TestScreen(modelId: String) {
    val viewModel: AndroidTestViewModel =
        viewModel(key = modelId) {
            val selectedModel = Dependencies.modelRepository.findById(modelId) ?: ModelCatalog.DEFAULT

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

            AndroidTestViewModel(
                TestViewModelImpl(
                    selectedModel = selectedModel,
                    modelPath = modelPath,
                    inferenceEngine = inferenceEngine,
                ),
                selectedModel
            )
        }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TestContent(
        frames = state.frames.values,
        testStatuses = state.testStatuses,
        selectedModel = state.selectedModel,
        isRunning = state.isRunning,
        isInitializing = state.isInitializing,
        onRunTests = { viewModel.onUiAction(TestUiAction.RunTests) },
        onCancelTests = { viewModel.onUiAction(TestUiAction.CancelTests) },
        modifier = Modifier,
    )
}

@Composable
private fun TestContent(
    frames: Collection<TestResultFrame>,
    testStatuses: List<TestStatus>,
    selectedModel: ModelConfiguration,
    isRunning: Boolean,
    isInitializing: Boolean,
    onRunTests: () -> Unit,
    onCancelTests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // 1. Top Card with Model Info
        ModelInfoCard(model = selectedModel)

        if (isInitializing) {
            InitializationIndicator(message = "Initializing model...")
        }

        // 2. LazyColumn with cells (70% weight)
        TestResultsList(
            frames = frames,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
        )

        TestStatusList(
            testStatuses = testStatuses,
            modifier = Modifier
                .fillMaxWidth()
        )

        // 4. Run/Cancel Button
        Button(
            onClick = if (isRunning) onCancelTests else onRunTests,
            enabled = !isInitializing,
        ) {
            Text(
                text = if (isRunning) "Cancel Tests" else "Run Tests",
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
fun InitializationIndicator(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Text(
            text = message,
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
            testStatuses = listOf(
                TestStatus(
                    name = "TEST 0: Basic Response",
                    state = TestStatus.State.PASS,
                )
            ),
            selectedModel = ModelCatalog.DEFAULT,
            isRunning = false,
            isInitializing = true,
            onRunTests = { },
            onCancelTests = { },
        )
    }
}

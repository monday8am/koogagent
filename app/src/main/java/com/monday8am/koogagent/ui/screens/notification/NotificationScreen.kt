package com.monday8am.koogagent.ui.screens.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monday8am.koogagent.Dependencies
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.InferenceEngineFactory
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.koogagent.ui.toDisplayString
import com.monday8am.presentation.notifications.NotificationViewModelImpl
import com.monday8am.presentation.notifications.UiAction
import com.monday8am.presentation.notifications.defaultNotificationContext

@Composable
fun NotificationScreen(modelId: String) {
    val viewModel: AndroidNotificationViewModel =
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

            AndroidNotificationViewModel(
                NotificationViewModelImpl(
                    selectedModel = selectedModel,
                    modelPath = modelPath,
                    inferenceEngine = inferenceEngine,
                    notificationEngine = Dependencies.notificationEngine,
                    weatherProvider = Dependencies.weatherProvider,
                    locationProvider = Dependencies.locationProvider,
                    deviceContextProvider = Dependencies.deviceContextProvider,
                ),
                selectedModel
            )
        }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NotificationContent(
        log = state.statusMessage.toDisplayString(),
        selectedModel = state.selectedModel,
        notificationContext = state.context,
        onNotificationContextChange = { viewModel.onUiAction(UiAction.UpdateContext(context = it)) },
        onPressButton = { viewModel.onUiAction(uiAction = it) },
        modifier = Modifier,
    )
}

@Composable
private fun NotificationContent(
    log: String,
    selectedModel: ModelConfiguration,
    notificationContext: NotificationContext,
    onNotificationContextChange: (NotificationContext) -> Unit,
    onPressButton: (UiAction) -> Unit,
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

        LogPanel(textLog = log)

        Button(
            onClick = { onPressButton(UiAction.ShowNotification) },
        ) {
            Text(
                text = "Trigger Notification",
            )
        }

        NotificationContextEditor(
            notificationContext = notificationContext,
            onContextChange = onNotificationContextChange,
        )
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
private fun LogPanel(textLog: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(textLog) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(290.dp)
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.inverseSurface),
    ) {
        Text(
            text = textLog,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NotificationContextEditor(
    notificationContext: NotificationContext,
    onContextChange: (NotificationContext) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = "Notification context", style = MaterialTheme.typography.titleMedium)

        EnumDropdown(
            label = "Meal type",
            options = enumValues<MealType>(),
            selected = notificationContext.mealType,
            onSelected = { onContextChange(notificationContext.copy(mealType = it)) },
        )

        EnumDropdown(
            label = "Motivation level",
            options = enumValues<MotivationLevel>(),
            selected = notificationContext.motivationLevel,
            onSelected = { onContextChange(notificationContext.copy(motivationLevel = it)) },
        )

        RowWithSwitch(
            checked = notificationContext.alreadyLogged,
            onCheckedChange = { onContextChange(notificationContext.copy(alreadyLogged = it)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(
    label: String,
    options: Array<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        TextField(
            value = formatEnumName(selected.name),
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            readOnly = true,
            modifier =
            Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(formatEnumName(option.name)) },
                    onClick = {
                        expanded = false
                        if (option != selected) {
                            onSelected(option)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowWithSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Already logged a meal")
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun formatEnumName(raw: String): String {
    val normalized = raw.lowercase().replace('_', ' ')
    return normalized.replaceFirstChar { it.titlecase() }
}

@Preview(showBackground = true)
@Composable
private fun NotificationContentPreview() {
    KoogAgentTheme {
        NotificationContent(
            log = "Welcome to Yazio notificator!",
            notificationContext = defaultNotificationContext,
            onNotificationContextChange = { },
            onPressButton = { },
            selectedModel = ModelCatalog.DEFAULT,
        )
    }
}

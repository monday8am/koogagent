package com.monday8am.koogagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monday8am.koogagent.data.MealType
import com.monday8am.koogagent.data.MockLocationProvider
import com.monday8am.koogagent.data.ModelCatalog
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.koogagent.data.MotivationLevel
import com.monday8am.koogagent.data.NotificationContext
import com.monday8am.koogagent.data.WeatherProviderImpl
import com.monday8am.koogagent.download.ModelDownloadManagerImpl
import com.monday8am.koogagent.inference.litert.tools.NativeLocationTools
import com.monday8am.koogagent.inference.litert.tools.NativeWeatherTools
import com.monday8am.koogagent.ui.AndroidNotificationViewModel
import com.monday8am.koogagent.ui.DeviceContextProviderImpl
import com.monday8am.koogagent.ui.NotificationEngineImpl
import com.monday8am.koogagent.ui.NotificationViewModelFactory
import com.monday8am.koogagent.ui.theme.KoogAgentTheme
import com.monday8am.koogagent.ui.toDisplayString
import com.monday8am.presentation.notifications.UiAction
import com.monday8am.presentation.notifications.defaultNotificationContext

class MainActivity : ComponentActivity() {
    private val notificationEngine: NotificationEngineImpl by lazy {
        NotificationEngineImpl(this.applicationContext)
    }

    private val viewModelFactory: NotificationViewModelFactory by lazy {
        val applicationContext = this.applicationContext

        // Native LiteRT-LM tools with @Tool annotations
        // These are passed to ConversationConfig for native tool calling
        val nativeTools =
            listOf(
                NativeLocationTools(),
                NativeWeatherTools(),
            )

        // Select model at startup (could be configurable in future)
        val selectedModel = ModelCatalog.DEFAULT

        val notificationEngine = notificationEngine
        val weatherProvider = WeatherProviderImpl()
        val locationProvider = MockLocationProvider() // <-- using Mock for now.
        val deviceContextProvider = DeviceContextProviderImpl(applicationContext)
        val modelManager = ModelDownloadManagerImpl(applicationContext)

        NotificationViewModelFactory(
            selectedModel = selectedModel,
            notificationEngine = notificationEngine,
            weatherProvider = weatherProvider,
            locationProvider = locationProvider,
            deviceContextProvider = deviceContextProvider,
            modelManager = modelManager,
            liteRtTools = nativeTools,
        )
    }

    private val viewModel: AndroidNotificationViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notificationEngine.createChannel()
        notificationEngine.requestNotificationPermission(this)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        log = state.statusMessage.toDisplayString(),
                        selectedModel = state.selectedModel,
                        notificationContext = state.context,
                        isModelReady = state.isModelReady,
                        onNotificationContextChange = { viewModel.onUiAction(UiAction.UpdateContext(context = it)) },
                        onPressButton = { viewModel.onUiAction(uiAction = it) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    log: String,
    selectedModel: ModelConfiguration,
    notificationContext: NotificationContext,
    isModelReady: Boolean,
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
                .padding(16.dp),
    ) {
        ModelInfoCard(model = selectedModel, modifier = Modifier.padding(top = 32.dp))

        LogPanel(textLog = log)

        if (isModelReady) {
            Button(
                onClick = { onPressButton(UiAction.ShowNotification) },
            ) {
                Text(
                    text = "Trigger Notification",
                )
            }
            Button(
                onClick = { onPressButton(UiAction.RunModelTests) },
            ) {
                Text(
                    text = "Run tests",
                )
            }
        } else {
            Button(
                onClick = { onPressButton(UiAction.DownloadModel) },
            ) {
                Text(
                    text = "Download model",
                )
            }
        }

        NotificationContextEditor(
            notificationContext = notificationContext,
            onContextChange = onNotificationContextChange,
        )
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
    textLog: String,
    modifier: Modifier = Modifier,
) {
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
                .background(Color(0xFF000080)),
    ) {
        Text(text = textLog, color = Color(0xFF00FF00), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        onExpandedChange = { },
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
private fun RowWithSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
fun MainScreenPreview() {
    KoogAgentTheme {
        MainScreen(
            log = "Welcome to Yazio notificator!",
            notificationContext = defaultNotificationContext,
            isModelReady = true,
            onNotificationContextChange = { },
            onPressButton = { },
        )
    }
}

package com.monday8am.koogagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monday8am.agent.NotificationContext
import com.monday8am.agent.MealType
import com.monday8am.agent.MotivationLevel
import com.monday8am.agent.WeatherCondition
import com.monday8am.koogagent.ui.NotificationUtils
import com.monday8am.koogagent.ui.NotificationViewModel
import com.monday8am.koogagent.ui.defaultNotificationContext
import com.monday8am.koogagent.ui.theme.KoogAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationUtils.createChannel(this)
        NotificationUtils.requestNotificationPermission(this)

        setContent {
            val viewModel: NotificationViewModel by viewModels()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        log = state.textLog,
                        notificationContext = state.context,
                        onNotificationContextChange = { viewModel.updateContext(it) },
                        onClickDownload = { viewModel.downloadModel() },
                        onClickNotification = { viewModel.processAndShowNotification() },
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
    notificationContext: NotificationContext,
    onNotificationContextChange: (NotificationContext) -> Unit,
    onClickDownload: () -> Unit,
    onClickNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        Button(
            onClick = onClickDownload,
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(
                text = "Download model",
            )
        }

        Button(
            onClick = onClickNotification,
        ) {
            Text(
                text = "Trigger Notification",
            )
        }

        Text(text = log, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        NotificationContextEditor(
            notificationContext = notificationContext,
            onContextChange = onNotificationContextChange,
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

        EnumDropdown(
            label = "Weather",
            options = enumValues<WeatherCondition>(),
            selected = notificationContext.weather,
            onSelected = { onContextChange(notificationContext.copy(weather = it)) },
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
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        TextField(
            value = formatEnumName(selected.name),
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            readOnly = true,
            modifier = Modifier
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
        Text(text = "Already logged")
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
            log = "Welcome to KoogAgent!",
            notificationContext = defaultNotificationContext,
            onNotificationContextChange = { },
            onClickDownload = { },
            onClickNotification = { },
        )
    }
}

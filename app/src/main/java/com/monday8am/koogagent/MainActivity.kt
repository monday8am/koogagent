package com.monday8am.koogagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monday8am.koogagent.ui.NotificationUtils
import com.monday8am.koogagent.ui.NotificationViewModel
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
                        log = state,
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
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(
                text = "Trigger Notification",
            )
        }

        Text(text = log, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KoogAgentTheme {
        MainScreen(
            log = "Welcome to KoogAgent!",
            onClickDownload = { },
            onClickNotification = { },
        )
    }
}

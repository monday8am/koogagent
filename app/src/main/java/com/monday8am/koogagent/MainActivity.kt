package com.monday8am.koogagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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

            KoogAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        onClickAIButton = { viewModel.prompt() },
                        onClickNotificationButton = { viewModel.processAndShowNotification() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onClickAIButton: () -> Unit,
    onClickNotificationButton: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    )  {
        Button(onClick = onClickAIButton) {
            Text("Generate Meal Message")
        }

        Button(onClick = onClickNotificationButton) {
            Text(
                text = "Trigger Notification",
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KoogAgentTheme {
        MainScreen(
            onClickAIButton = { },
            onClickNotificationButton = { }
        )
    }
}